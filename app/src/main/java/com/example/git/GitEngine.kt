package com.example.git

import android.util.Base64
import com.example.core.common.AppError
import com.example.core.common.AppErrorException
import com.example.core.security.PathSecurity
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.DiffFileItem
import com.example.domain.model.UploadStage
import com.example.github.api.GitHubApiService
import com.example.github.dto.CreateBlobRequestDto
import com.example.github.dto.CreateCommitRequestDto
import com.example.github.dto.CreateGitRefRequestDto
import com.example.github.dto.CreateTreeRequestDto
import com.example.github.dto.PutContentRequestDto
import com.example.github.dto.UpdateGitRefRequestDto
import com.example.gitlfs.GitAttributesHelper
import com.example.gitlfs.GitLfsClassifier
import com.example.gitlfs.GitLfsEngine
import com.example.gitlfs.LfsUploadSource
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

data class GitUploadFileItem(
  val relativePath: String,
  val sizeBytes: Long,
  val useLfs: Boolean,
  val precomputedSha256: String? = null,
  val openStream: () -> InputStream,
)

data class GitPushResult(
  val commitSha: String,
  val commitUrl: String,
  val previousCommitSha: String?,
  val previousTreeSha: String?,
  val newTreeSha: String,
  val totalFilesCommitted: Int,
  val lfsFilesCount: Int,
  val deletedFilesCount: Int,
)

data class RemoteBranchSnapshot(
  val isEmptyRepo: Boolean,
  val branchExists: Boolean,
  val headCommitSha: String?,
  val baseTreeSha: String?,
)

data class RawTreeEntry(
  val path: String,
  val mode: String = "100644",
  val type: String = "blob",
  val sha: String? = null,
  val inlineContent: String? = null,
  val isDeletion: Boolean = false,
  val originalSizeBytes: Long = 0L,
)

class GitEngine(
  private val api: GitHubApiService,
  private val lfsEngine: GitLfsEngine,
  private val moshi: Moshi,
) {
  companion object {
    private const val MAX_INLINE_FILE_BYTES = 96 * 1024
    private const val MAX_TREE_CHUNK_ENTRIES = 200
    private const val MAX_TREE_CHUNK_BYTES = 1_500_000L
  }

  /**
   * Computes standard Git object SHA-1: sha1("blob <size>\0<content>").
   */
  fun computeGitBlobSha1(bytes: ByteArray): String {
    val header = "blob ${bytes.size}\u0000".toByteArray(StandardCharsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(header)
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Streaming Git Blob SHA-1 computation using O(1) memory buffer.
   */
  fun computeGitBlobSha1Streaming(sizeBytes: Long, openStream: () -> InputStream): String {
    val header = "blob $sizeBytes\u0000".toByteArray(StandardCharsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(header)
    val buf = ByteArray(128 * 1024)
    openStream().use { input ->
      while (true) {
        val read = input.read(buf)
        if (read == -1) break
        digest.update(buf, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  suspend fun inspectRemoteBranch(
    owner: String,
    repo: String,
    branch: String,
  ): RemoteBranchSnapshot {
    val refRes = api.getBranchRef(owner, repo, branch)
    if (refRes.code() == 409) {
      return RemoteBranchSnapshot(
        isEmptyRepo = true,
        branchExists = false,
        headCommitSha = null,
        baseTreeSha = null,
      )
    }
    if (refRes.code() == 404) {
      return RemoteBranchSnapshot(
        isEmptyRepo = false,
        branchExists = false,
        headCommitSha = null,
        baseTreeSha = null,
      )
    }
    if (!refRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(refRes.code(), refRes.errorBody()?.string()))
    }
    val headSha = refRes.body()?.obj?.sha
      ?: throw AppErrorException(AppError.Unknown("Missing ref object SHA"))
    val commitRes = api.getGitCommit(owner, repo, headSha)
    if (!commitRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(commitRes.code(), commitRes.errorBody()?.string()))
    }
    val baseTreeSha = commitRes.body()?.tree?.sha
    return RemoteBranchSnapshot(
      isEmptyRepo = false,
      branchExists = true,
      headCommitSha = headSha,
      baseTreeSha = baseTreeSha,
    )
  }

  suspend fun pushFilesAtomically(
    owner: String,
    repo: String,
    branch: String,
    commitMessage: String,
    files: List<GitUploadFileItem>,
    filesToDelete: List<String> = emptyList(),
    expectedBaseSha: String?,
    conflictStrategy: ConflictStrategy,
    concurrency: Int = 4,
    isCancelled: () -> Boolean,
    onStageChange: suspend (UploadStage) -> Unit,
    onLog: (String) -> Unit,
    onProgressDelta: (Long) -> Unit,
  ): GitPushResult = coroutineScope {
    if (files.isEmpty() && filesToDelete.isEmpty()) {
      throw AppErrorException(AppError.Http400("No file changes selected to commit"))
    }

    for (file in files) {
      if (file.sizeBytes >= GitLfsClassifier.HARD_GIT_LIMIT_BYTES && !file.useLfs) {
        throw AppErrorException(
          AppError.Http422("File '${file.relativePath}' >= 100MB cannot be pushed via standard Git; Git LFS is mandatory."),
        )
      }
    }

    val workerLimit = concurrency.coerceIn(1, 6)
    onStageChange(UploadStage.PREPARING)
    onLog("[GIT] Inspecting remote $owner/$repo branch '$branch' (workers=$workerLimit)...")
    var snapshot = inspectRemoteBranch(owner, repo, branch)
    val previousCommitSha = snapshot.headCommitSha
    val previousTreeSha = snapshot.baseTreeSha

    if (snapshot.branchExists && expectedBaseSha != null && snapshot.headCommitSha != expectedBaseSha) {
      when (conflictStrategy) {
        ConflictStrategy.ABORT -> {
          throw AppErrorException(
            AppError.Http409(
              "Remote HEAD (${snapshot.headCommitSha?.take(7)}) differs from expected (${expectedBaseSha.take(7)}). Pull/Sync or confirm update.",
            ),
          )
        }
        ConflictStrategy.SYNC_AND_STACK -> {
          onLog("[GIT] Synced with newer remote HEAD: ${snapshot.headCommitSha?.take(7)}")
        }
        ConflictStrategy.FORCE_UPDATE -> {
          onLog("[GIT] Force push mode confirmed by user; overwriting remote ref.")
        }
      }
    }

    if (snapshot.isEmptyRepo) {
      onLog("[GIT] Repository is empty. Initializing branch '$branch'...")
      val initContent = Base64.encodeToString(
        "# $repo\nInitialized by Qusin Git Uploader\n".toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP,
      )
      val initRes = api.putContentFile(
        owner = owner,
        repo = repo,
        path = "README.md",
        body = PutContentRequestDto(
          message = "Initialize repository via Qusin Git Uploader",
          content = initContent,
          branch = branch,
        ),
      )
      if (!initRes.isSuccessful) {
        throw AppErrorException(AppError.fromHttpStatus(initRes.code(), initRes.errorBody()?.string()))
      }
      snapshot = inspectRemoteBranch(owner, repo, branch)
    } else if (!snapshot.branchExists) {
      val repoRes = api.getRepo(owner, repo)
      if (!repoRes.isSuccessful) {
        throw AppErrorException(AppError.fromHttpStatus(repoRes.code(), repoRes.errorBody()?.string()))
      }
      val defaultBranch = repoRes.body()?.defaultBranch ?: "main"
      val baseSnap = inspectRemoteBranch(owner, repo, defaultBranch)
      val baseSha = baseSnap.headCommitSha
        ?: throw AppErrorException(AppError.Http404("Default branch '$defaultBranch' has no commits"))
      val createRefRes = api.createRef(owner, repo, CreateGitRefRequestDto("refs/heads/$branch", baseSha))
      if (!createRefRes.isSuccessful) {
        throw AppErrorException(AppError.fromHttpStatus(createRefRes.code(), createRefRes.errorBody()?.string()))
      }
      snapshot = inspectRemoteBranch(owner, repo, branch)
    }

    val lfsItems = files.filter { it.useLfs }
    val standardItems = files.filter { !it.useLfs }
    val inlineTextEntries = Collections.synchronizedList(mutableListOf<RawTreeEntry>())
    val blobShaEntries = Collections.synchronizedList(mutableListOf<RawTreeEntry>())

    // Phase A: Git LFS Batch API + Parallel Streaming PUT + Verify + Inline Pointer Tree Entries
    if (lfsItems.isNotEmpty()) {
      onStageChange(UploadStage.UPLOADING_LFS)
      onLog("[LFS] Routing ${lfsItems.size} file(s) through Git LFS Batch API (workers=$workerLimit)...")
      val lfsPlans = lfsEngine.uploadObjectsViaBatchApi(
        owner = owner,
        repo = repo,
        branch = branch,
        sources = lfsItems.map { item ->
          LfsUploadSource(
            relativePath = sanitizeOrThrow(item.relativePath),
            sizeBytes = item.sizeBytes,
            precomputedSha256 = item.precomputedSha256,
            openStream = item.openStream,
          )
        },
        concurrency = workerLimit,
        isCancelled = isCancelled,
        onLog = onLog,
        onProgressDelta = onProgressDelta,
      )

      val existingAttrsRes = api.getContentFile(owner, repo, ".gitattributes", ref = branch)
      val existingAttrsText = if (existingAttrsRes.isSuccessful) {
        existingAttrsRes.body()?.content?.let { encoded ->
          val clean = encoded.replace("\n", "")
          String(Base64.decode(clean, Base64.DEFAULT), StandardCharsets.UTF_8)
        }
      } else {
        null
      }

      val mergedAttrs = GitAttributesHelper.mergeGitAttributes(
        existingContent = existingAttrsText,
        lfsRelativePaths = lfsPlans.map { it.relativePath },
      )
      inlineTextEntries.add(RawTreeEntry(path = ".gitattributes", inlineContent = mergedAttrs))
      for (plan in lfsPlans) {
        if (isCancelled()) throw AppErrorException(AppError.GitLfsFailure("Upload cancelled"))
        inlineTextEntries.add(RawTreeEntry(path = plan.relativePath, inlineContent = plan.pointerContent))
      }
      onLog("[LFS] Staged .gitattributes + ${lfsPlans.size} pointer(s) inline in tree")
    }

    // Phase B: Standard Git Files — Single-Read Parallel Pipeline (Chunked Inline UTF-8 + Parallel Blobs)
    var runningBaseTreeSha = snapshot.baseTreeSha
    if (standardItems.isNotEmpty()) {
      onStageChange(UploadStage.UPLOADING)
      onLog("[GIT] Processing ${standardItems.size} file(s) with $workerLimit parallel worker(s)...")

      val semaphore = Semaphore(workerLimit)
      standardItems.map { item ->
        async(Dispatchers.IO) {
          semaphore.withPermit {
            if (isCancelled()) throw AppErrorException(AppError.Unknown("Upload cancelled by user"))
            val safePath = sanitizeOrThrow(item.relativePath)

            // Read SAF/temp stream ONCE; if valid UTF-8 <= 96KB, stage inline without POST /git/blobs
            val rawBytes = readAllBytesSafely(item.openStream)
            val utf8Text = if (rawBytes.size <= MAX_INLINE_FILE_BYTES) {
              tryDecodeExactUtf8(rawBytes)
            } else {
              null
            }

            if (utf8Text != null) {
              inlineTextEntries.add(
                RawTreeEntry(
                  path = safePath,
                  inlineContent = utf8Text,
                  originalSizeBytes = item.sizeBytes,
                ),
              )
            } else {
              val base64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
              val remoteBlobSha = createBlobWithRetry(
                owner = owner,
                repo = repo,
                base64Content = base64,
                isCancelled = isCancelled,
              )
              onProgressDelta(item.sizeBytes)
              blobShaEntries.add(RawTreeEntry(path = safePath, sha = remoteBlobSha))
            }
          }
        }
      }.awaitAll()
    }

    // Phase B2: Deletions via explicit sha = null on base_tree
    val cleanDeletions = filesToDelete.mapNotNull { PathSecurity.sanitizeRelativeGitPath(it) }
    val deletionEntries = cleanDeletions.map { delPath ->
      onLog("[GIT] Marked for deletion: $delPath")
      RawTreeEntry(path = delPath, isDeletion = true)
    }

    // Phase C: Push all inline text entries in safe chunks (up to 200 files / 1.5MB per POST /git/trees)
    // followed by binary blob SHAs & deletions, chaining base_tree -> single atomic commit at the end.
    onStageChange(UploadStage.COMMITTING)
    val inlineChunks = chunkTreeEntries(inlineTextEntries.toList())
    if (inlineChunks.isNotEmpty()) {
      onLog("[GIT] Batch-committing ${inlineTextEntries.size} text file(s) in ${inlineChunks.size} tree chunk(s) (zero per-file REST calls)...")
    }

    val trailingEntries = blobShaEntries.toList() + deletionEntries
    val trailingChunks = if (trailingEntries.isNotEmpty()) {
      trailingEntries.chunked(MAX_TREE_CHUNK_ENTRIES)
    } else {
      emptyList()
    }
    val allChunks = inlineChunks + trailingChunks
    require(allChunks.isNotEmpty()) { "No tree chunks to commit" }

    for ((idx, chunk) in allChunks.withIndex()) {
      if (isCancelled()) throw AppErrorException(AppError.Unknown("Upload cancelled by user"))
      val jsonBody = serializeRawTree(
        baseTree = runningBaseTreeSha,
        entries = chunk,
      )
      val treeRes = createTreeRawWithRetry(
        owner = owner,
        repo = repo,
        jsonBody = jsonBody,
        isCancelled = isCancelled,
      )
      runningBaseTreeSha = treeRes
      val chunkBytes = chunk.sumOf { it.originalSizeBytes }
      if (chunkBytes > 0L) {
        onProgressDelta(chunkBytes)
      }
      onLog("[GIT] Tree chunk ${idx + 1}/${allChunks.size} created (${chunk.size} entries) -> ${treeRes.take(7)}")
    }
    val newTreeSha = runningBaseTreeSha
      ?: throw AppErrorException(AppError.Unknown("Missing final tree SHA"))

    // Phase D: Create Single Atomic Git Commit
    val parents = listOfNotNull(snapshot.headCommitSha)
    val commitRes = api.createCommit(
      owner = owner,
      repo = repo,
      body = CreateCommitRequestDto(
        message = commitMessage.ifBlank { "Update project via Qusin Git Uploader" },
        tree = newTreeSha,
        parents = parents,
      ),
    )
    if (!commitRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(commitRes.code(), commitRes.errorBody()?.string()))
    }
    val newCommitSha = commitRes.body()?.sha
      ?: throw AppErrorException(AppError.Unknown("Missing commit SHA"))
    onLog("[GIT] Commit created: $newCommitSha")

    // Phase E: Push Ref
    onStageChange(UploadStage.PUSHING)
    val isForce = conflictStrategy == ConflictStrategy.FORCE_UPDATE
    val pushRes = api.updateBranchRef(
      owner = owner,
      repo = repo,
      branch = branch,
      body = UpdateGitRefRequestDto(sha = newCommitSha, force = isForce),
    )
    if (!pushRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(pushRes.code(), pushRes.errorBody()?.string()))
    }

    // Phase F: Verify remote HEAD matches newCommitSha
    onStageChange(UploadStage.VERIFYING)
    val verifySnap = inspectRemoteBranch(owner, repo, branch)
    if (verifySnap.headCommitSha != newCommitSha) {
      throw AppErrorException(AppError.Http409("Post-push verification mismatch: expected $newCommitSha, got ${verifySnap.headCommitSha}"))
    }
    onLog("[VERIFY] Remote refs/heads/$branch verified at $newCommitSha")
    onStageChange(UploadStage.COMPLETED)

    return@coroutineScope GitPushResult(
      commitSha = newCommitSha,
      commitUrl = "https://github.com/$owner/$repo/commit/$newCommitSha",
      previousCommitSha = previousCommitSha,
      previousTreeSha = previousTreeSha,
      newTreeSha = newTreeSha,
      totalFilesCommitted = files.size,
      lfsFilesCount = lfsItems.size,
      deletedFilesCount = cleanDeletions.size,
    )
  }

  internal fun chunkTreeEntries(entries: List<RawTreeEntry>): List<List<RawTreeEntry>> {
    if (entries.isEmpty()) return emptyList()
    val chunks = mutableListOf<List<RawTreeEntry>>()
    var current = mutableListOf<RawTreeEntry>()
    var currentBytes = 0L

    for (entry in entries) {
      val entryBytes = (entry.inlineContent?.length?.toLong() ?: 0L)
      val exceedsLimit = current.isNotEmpty() &&
        (current.size >= MAX_TREE_CHUNK_ENTRIES || currentBytes + entryBytes > MAX_TREE_CHUNK_BYTES)
      if (exceedsLimit) {
        chunks.add(current)
        current = mutableListOf()
        currentBytes = 0L
      }
      current.add(entry)
      currentBytes += entryBytes
    }
    if (current.isNotEmpty()) {
      chunks.add(current)
    }
    return chunks
  }

  private suspend fun createBlobWithRetry(
    owner: String,
    repo: String,
    base64Content: String,
    isCancelled: () -> Boolean,
  ): String {
    var attempt = 0
    while (true) {
      if (isCancelled()) throw AppErrorException(AppError.Unknown("Upload cancelled by user"))
      val res = api.createBlob(owner, repo, CreateBlobRequestDto(content = base64Content, encoding = "base64"))
      if (res.isSuccessful) {
        return res.body()?.sha ?: throw AppErrorException(AppError.Unknown("Missing blob SHA from GitHub"))
      }
      val code = res.code()
      if ((code == 429 || code == 403 || code in 500..503) && attempt < 3) {
        attempt++
        val retryAfterSec = res.headers()["Retry-After"]?.toLongOrNull()?.coerceIn(1L, 15L)
        val waitMs = retryAfterSec?.times(1000L) ?: (1000L * attempt)
        delay(waitMs)
        continue
      }
      throw AppErrorException(AppError.fromHttpStatus(code, res.errorBody()?.string()))
    }
  }

  private suspend fun createTreeRawWithRetry(
    owner: String,
    repo: String,
    jsonBody: String,
    isCancelled: () -> Boolean,
  ): String {
    var attempt = 0
    while (true) {
      if (isCancelled()) throw AppErrorException(AppError.Unknown("Upload cancelled by user"))
      val res = api.createTreeRaw(
        owner = owner,
        repo = repo,
        body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()),
      )
      if (res.isSuccessful) {
        return res.body()?.sha ?: throw AppErrorException(AppError.Unknown("Missing tree SHA"))
      }
      val code = res.code()
      if ((code == 429 || code == 403 || code in 500..503) && attempt < 3) {
        attempt++
        val retryAfterSec = res.headers()["Retry-After"]?.toLongOrNull()?.coerceIn(1L, 15L)
        val waitMs = retryAfterSec?.times(1000L) ?: (1000L * attempt)
        delay(waitMs)
        continue
      }
      throw AppErrorException(AppError.fromHttpStatus(code, res.errorBody()?.string()))
    }
  }

  /**
   * Computes which files will be reverted when rolling back branch HEAD to [targetTreeSha].
   */
  suspend fun previewRollback(
    owner: String,
    repo: String,
    branch: String,
    targetTreeSha: String,
  ): Pair<String, List<DiffFileItem>> {
    val snap = inspectRemoteBranch(owner, repo, branch)
    val currentHead = snap.headCommitSha
      ?: throw AppErrorException(AppError.Http404("Branch '$branch' has no commits"))
    val currentTreeSha = snap.baseTreeSha
      ?: throw AppErrorException(AppError.Http404("Branch '$branch' has no base tree"))

    val currentTreeRes = api.getGitTree(owner, repo, currentTreeSha, recursive = 1)
    if (!currentTreeRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(currentTreeRes.code(), currentTreeRes.errorBody()?.string()))
    }
    val targetTreeRes = api.getGitTree(owner, repo, targetTreeSha, recursive = 1)
    if (!targetTreeRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(targetTreeRes.code(), targetTreeRes.errorBody()?.string()))
    }

    val delta = ProjectDiffEngine.computeTreeDeltaPreview(
      currentTree = currentTreeRes.body()?.tree.orEmpty(),
      targetTree = targetTreeRes.body()?.tree.orEmpty(),
    )
    return currentHead to delta
  }

  /**
   * Creates a new forward commit pointing to [targetTreeSha] without erasing commit history.
   */
  suspend fun executeRollback(
    owner: String,
    repo: String,
    branch: String,
    targetCommitSha: String,
    targetTreeSha: String,
  ): GitPushResult {
    val snap = inspectRemoteBranch(owner, repo, branch)
    val currentHead = snap.headCommitSha
      ?: throw AppErrorException(AppError.Http404("Branch '$branch' has no commits"))
    val currentTreeSha = snap.baseTreeSha

    val commitRes = api.createCommit(
      owner = owner,
      repo = repo,
      body = CreateCommitRequestDto(
        message = "Rollback to ${targetCommitSha.take(7)} via Qusin Git Uploader",
        tree = targetTreeSha,
        parents = listOf(currentHead),
      ),
    )
    if (!commitRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(commitRes.code(), commitRes.errorBody()?.string()))
    }
    val newCommitSha = commitRes.body()?.sha
      ?: throw AppErrorException(AppError.Unknown("Missing rollback commit SHA"))

    val pushRes = api.updateBranchRef(
      owner = owner,
      repo = repo,
      branch = branch,
      body = UpdateGitRefRequestDto(sha = newCommitSha, force = false),
    )
    if (!pushRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(pushRes.code(), pushRes.errorBody()?.string()))
    }

    return GitPushResult(
      commitSha = newCommitSha,
      commitUrl = "https://github.com/$owner/$repo/commit/$newCommitSha",
      previousCommitSha = currentHead,
      previousTreeSha = currentTreeSha,
      newTreeSha = targetTreeSha,
      totalFilesCommitted = 0,
      lfsFilesCount = 0,
      deletedFilesCount = 0,
    )
  }

  fun serializeTreeWithNulls(dto: CreateTreeRequestDto): String {
    return moshi.adapter(CreateTreeRequestDto::class.java).serializeNulls().toJson(dto)
  }

  fun serializeRawTree(baseTree: String?, entries: List<RawTreeEntry>): String {
    val buffer = Buffer()
    val writer = JsonWriter.of(buffer)
    writer.beginObject()
    if (baseTree != null) {
      writer.name("base_tree").value(baseTree)
    }
    writer.name("tree").beginArray()
    for (entry in entries) {
      writer.beginObject()
      writer.name("path").value(entry.path)
      writer.name("mode").value(entry.mode)
      writer.name("type").value(entry.type)
      when {
        entry.isDeletion -> {
          val prev = writer.serializeNulls
          writer.serializeNulls = true
          writer.name("sha").nullValue()
          writer.serializeNulls = prev
        }
        entry.inlineContent != null -> {
          writer.name("content").value(entry.inlineContent)
        }
        else -> {
          writer.name("sha").value(entry.sha)
        }
      }
      writer.endObject()
    }
    writer.endArray()
    writer.endObject()
    writer.flush()
    return buffer.readUtf8()
  }

  internal fun tryDecodeExactUtf8(bytes: ByteArray): String? {
    if (bytes.size > MAX_INLINE_FILE_BYTES) return null
    for (b in bytes) {
      if (b == 0.toByte()) return null
    }
    return try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
      if (decoded.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) decoded else null
    } catch (_: Throwable) {
      null
    }
  }

  private fun sanitizeOrThrow(raw: String): String {
    return PathSecurity.sanitizeRelativeGitPath(raw)
      ?: throw AppErrorException(AppError.Http400("Unsafe or invalid relative path: $raw"))
  }

  private fun readAllBytesSafely(openStream: () -> InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(128 * 1024)
    openStream().use { input ->
      while (true) {
        val r = input.read(buf)
        if (r == -1) break
        out.write(buf, 0, r)
      }
    }
    return out.toByteArray()
  }
}
