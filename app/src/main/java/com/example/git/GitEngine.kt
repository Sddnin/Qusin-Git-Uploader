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
import com.example.github.dto.GitTreeEntryRequestDto
import com.example.github.dto.PutContentRequestDto
import com.example.github.dto.UpdateGitRefRequestDto
import com.example.gitlfs.GitAttributesHelper
import com.example.gitlfs.GitLfsClassifier
import com.example.gitlfs.GitLfsEngine
import com.example.gitlfs.LfsUploadSource
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class GitUploadFileItem(
  val relativePath: String,
  val sizeBytes: Long,
  val useLfs: Boolean,
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

class GitEngine(
  private val api: GitHubApiService,
  private val lfsEngine: GitLfsEngine,
  private val moshi: Moshi,
) {
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
    val buf = ByteArray(64 * 1024)
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
    isCancelled: () -> Boolean,
    onStageChange: suspend (UploadStage) -> Unit,
    onLog: (String) -> Unit,
    onProgressDelta: (Long) -> Unit,
  ): GitPushResult {
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

    onStageChange(UploadStage.PREPARING)
    onLog("[GIT] Inspecting remote $owner/$repo branch '$branch'...")
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
    val treeEntries = mutableListOf<GitTreeEntryRequestDto>()

    // Phase A: Git LFS Batch API + Streaming PUT + Verify + Pointer Blobs + .gitattributes
    if (lfsItems.isNotEmpty()) {
      onStageChange(UploadStage.UPLOADING_LFS)
      onLog("[LFS] Routing ${lfsItems.size} file(s) through Git LFS Batch API...")
      val lfsPlans = lfsEngine.uploadObjectsViaBatchApi(
        owner = owner,
        repo = repo,
        branch = branch,
        sources = lfsItems.map { item ->
          LfsUploadSource(
            relativePath = sanitizeOrThrow(item.relativePath),
            sizeBytes = item.sizeBytes,
            openStream = item.openStream,
          )
        },
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
      val attrsBlobSha = createTextBlob(owner, repo, mergedAttrs)
      treeEntries.add(GitTreeEntryRequestDto(path = ".gitattributes", sha = attrsBlobSha))
      onLog("[LFS] Updated .gitattributes blob (${attrsBlobSha.take(7)})")

      for (plan in lfsPlans) {
        if (isCancelled()) throw AppErrorException(AppError.GitLfsFailure("Upload cancelled"))
        val pointerSha = createTextBlob(owner, repo, plan.pointerContent)
        treeEntries.add(GitTreeEntryRequestDto(path = plan.relativePath, sha = pointerSha))
        onLog("[LFS] Staged pointer for ${plan.relativePath} -> ${pointerSha.take(7)}")
      }
    }

    // Phase B: Standard Git Blobs
    if (standardItems.isNotEmpty()) {
      onStageChange(UploadStage.UPLOADING)
      for (item in standardItems) {
        if (isCancelled()) throw AppErrorException(AppError.Unknown("Upload cancelled by user"))
        val safePath = sanitizeOrThrow(item.relativePath)
        val rawBytes = readAllBytesSafely(item.openStream)
        val localSha1 = computeGitBlobSha1(rawBytes)
        val base64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        val blobRes = api.createBlob(owner, repo, CreateBlobRequestDto(content = base64, encoding = "base64"))
        if (!blobRes.isSuccessful) {
          throw AppErrorException(AppError.fromHttpStatus(blobRes.code(), blobRes.errorBody()?.string()))
        }
        val remoteBlobSha = blobRes.body()?.sha
          ?: throw AppErrorException(AppError.Unknown("Missing blob SHA from GitHub"))
        onLog("[GIT] Blob $safePath • local=$localSha1 remote=${remoteBlobSha.take(7)}")
        treeEntries.add(GitTreeEntryRequestDto(path = safePath, sha = remoteBlobSha))
        onProgressDelta(item.sizeBytes)
      }
    }

    // Phase B2: Deletions via sha = null on base_tree
    val cleanDeletions = filesToDelete.mapNotNull { PathSecurity.sanitizeRelativeGitPath(it) }
    for (delPath in cleanDeletions) {
      treeEntries.add(GitTreeEntryRequestDto(path = delPath, sha = null))
      onLog("[GIT] Marked for deletion: $delPath")
    }

    // Phase C: Create Git Tree (using serializeNulls() whenever deletions exist)
    onStageChange(UploadStage.COMMITTING)
    onLog("[GIT] Creating atomic tree (${treeEntries.size} entries, ${cleanDeletions.size} deletions)...")
    val treeReqDto = CreateTreeRequestDto(
      baseTree = snapshot.baseTreeSha,
      tree = treeEntries,
    )
    val treeRes = if (cleanDeletions.isNotEmpty()) {
      val jsonBody = serializeTreeWithNulls(treeReqDto)
      api.createTreeRaw(owner, repo, jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
    } else {
      api.createTree(owner, repo, treeReqDto)
    }
    if (!treeRes.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(treeRes.code(), treeRes.errorBody()?.string()))
    }
    val newTreeSha = treeRes.body()?.sha
      ?: throw AppErrorException(AppError.Unknown("Missing tree SHA"))

    // Phase D: Create Git Commit
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

    return GitPushResult(
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

  private suspend fun createTextBlob(owner: String, repo: String, text: String): String {
    val b64 = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    val res = api.createBlob(owner, repo, CreateBlobRequestDto(content = b64, encoding = "base64"))
    if (!res.isSuccessful) {
      throw AppErrorException(AppError.fromHttpStatus(res.code(), res.errorBody()?.string()))
    }
    return res.body()?.sha ?: throw AppErrorException(AppError.Unknown("Missing blob SHA"))
  }

  private fun sanitizeOrThrow(raw: String): String {
    return PathSecurity.sanitizeRelativeGitPath(raw)
      ?: throw AppErrorException(AppError.Http400("Unsafe or invalid relative path: $raw"))
  }

  private fun readAllBytesSafely(openStream: () -> InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(32 * 1024)
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
