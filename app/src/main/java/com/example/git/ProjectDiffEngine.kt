package com.example.git

import android.util.Base64
import com.example.core.common.AppError
import com.example.core.common.AppErrorException
import com.example.core.security.PathSecurity
import com.example.domain.model.DiffFileItem
import com.example.domain.model.DiffStatus
import com.example.domain.model.LfsClassification
import com.example.domain.model.ProjectDiffPlan
import com.example.domain.model.SelectedFileItem
import com.example.github.api.GitHubApiService
import com.example.github.dto.GitTreeNodeDto
import com.example.gitlfs.GitLfsEngine
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class RemoteBlobSummary(
  val path: String,
  val sha: String,
  val size: Long,
  val lfsOid: String? = null,
)

class ProjectDiffEngine(
  private val api: GitHubApiService,
  private val gitEngine: GitEngine,
  private val lfsEngine: GitLfsEngine,
) {
  suspend fun analyzeDiffAgainstRemote(
    owner: String,
    repo: String,
    branch: String,
    targetPrefix: String,
    localItems: List<SelectedFileItem>,
    openStreamFor: (SelectedFileItem) -> InputStream,
    isCancelled: () -> Boolean = { false },
    onProgress: (String) -> Unit = {},
  ): ProjectDiffPlan {
    val cleanPrefix = normalizePrefix(targetPrefix)
    onProgress("Inspecting remote $owner/$repo@$branch...")
    val snapshot = gitEngine.inspectRemoteBranch(owner, repo, branch)

    var truncated = false
    val remoteBlobs = mutableMapOf<String, RemoteBlobSummary>()

    if (snapshot.branchExists && snapshot.baseTreeSha != null) {
      onProgress("Fetching remote tree (${snapshot.baseTreeSha.take(7)})...")
      val treeRes = api.getGitTree(owner, repo, snapshot.baseTreeSha, recursive = 1)
      if (!treeRes.isSuccessful) {
        throw AppErrorException(AppError.fromHttpStatus(treeRes.code(), treeRes.errorBody()?.string()))
      }
      val body = treeRes.body()
        ?: throw AppErrorException(AppError.Unknown("Empty remote tree response"))
      truncated = body.truncated
      for (node in body.tree) {
        if (node.type == "blob") {
          remoteBlobs[node.path] = RemoteBlobSummary(
            path = node.path,
            sha = node.sha,
            size = node.size ?: 0L,
          )
        }
      }
    }

    val scopedLocalItems = localItems.mapNotNull { item ->
      val combinedPath = if (cleanPrefix.isEmpty()) item.relativePath else "$cleanPrefix/${item.relativePath}"
      val safePath = PathSecurity.sanitizeRelativeGitPath(combinedPath) ?: return@mapNotNull null
      item.copy(relativePath = safePath)
    }

    val diffItems = mutableListOf<DiffFileItem>()
    val seenLocalPaths = mutableSetOf<String>()

    for (local in scopedLocalItems) {
      if (isCancelled()) throw AppErrorException(AppError.Unknown("Diff scan cancelled"))
      seenLocalPaths.add(local.relativePath)
      val remote = remoteBlobs[local.relativePath]

      if (remote == null) {
        // Brand new file: avoid hashing non-LFS file until upload, hash LFS only when needed
        diffItems.add(
          DiffFileItem(
            relativePath = local.relativePath,
            status = DiffStatus.ADDED,
            localSizeBytes = local.sizeBytes,
            remoteSizeBytes = null,
            localSha1OrOid = null,
            remoteSha1OrOid = null,
            isLfs = local.useLfs,
            lfsClassification = local.lfsClassification,
            selectedForApply = true,
            sourceItem = local,
          ),
        )
        continue
      }

      if (local.useLfs) {
        // Remote LFS pointer files are small (~125-200 bytes)
        onProgress("Checking LFS OID: ${local.relativePath}")
        val localOid = lfsEngine.computeSha256({ openStreamFor(local) }, isCancelled)
        val remotePointerOid = if (remote.size in 80L..260L) {
          fetchRemoteLfsPointerOid(owner, repo, remote.sha)
        } else {
          null
        }
        val status = if (remotePointerOid != null && remotePointerOid == localOid) {
          DiffStatus.UNCHANGED
        } else {
          DiffStatus.MODIFIED
        }
        diffItems.add(
          DiffFileItem(
            relativePath = local.relativePath,
            status = status,
            localSizeBytes = local.sizeBytes,
            remoteSizeBytes = remote.size,
            localSha1OrOid = "sha256:${localOid.take(12)}",
            remoteSha1OrOid = remotePointerOid?.let { "sha256:${it.take(12)}" } ?: remote.sha.take(12),
            isLfs = true,
            lfsClassification = local.lfsClassification,
            selectedForApply = status != DiffStatus.UNCHANGED,
            sourceItem = local,
          ),
        )
      } else {
        // Standard Git file: compute Git Blob SHA-1 streaming with O(1) memory
        onProgress("Hashing Git blob SHA-1: ${local.relativePath}")
        val localSha1 = gitEngine.computeGitBlobSha1Streaming(local.sizeBytes) { openStreamFor(local) }
        val status = if (localSha1 == remote.sha) DiffStatus.UNCHANGED else DiffStatus.MODIFIED
        diffItems.add(
          DiffFileItem(
            relativePath = local.relativePath,
            status = status,
            localSizeBytes = local.sizeBytes,
            remoteSizeBytes = remote.size,
            localSha1OrOid = localSha1.take(12),
            remoteSha1OrOid = remote.sha.take(12),
            isLfs = false,
            lfsClassification = local.lfsClassification,
            selectedForApply = status != DiffStatus.UNCHANGED,
            sourceItem = local,
          ),
        )
      }
    }

    // Detect remote files missing from new local source (respecting targetPrefix scope!)
    for ((remotePath, remoteNode) in remoteBlobs) {
      if (remotePath == ".gitattributes") continue
      val inScope = if (cleanPrefix.isEmpty()) true else remotePath.startsWith("$cleanPrefix/")
      if (inScope && remotePath !in seenLocalPaths) {
        diffItems.add(
          DiffFileItem(
            relativePath = remotePath,
            status = DiffStatus.DELETED,
            localSizeBytes = 0L,
            remoteSizeBytes = remoteNode.size,
            localSha1OrOid = null,
            remoteSha1OrOid = remoteNode.sha.take(12),
            isLfs = false,
            lfsClassification = LfsClassification.STANDARD_GIT,
            selectedForApply = false,
            sourceItem = null,
          ),
        )
      }
    }

    val sorted = diffItems.sortedWith(
      compareBy<DiffFileItem> {
        when (it.status) {
          DiffStatus.ADDED -> 0
          DiffStatus.MODIFIED -> 1
          DiffStatus.DELETED -> 2
          DiffStatus.UNCHANGED -> 3
        }
      }.thenBy { it.relativePath },
    )

    return ProjectDiffPlan(
      owner = owner,
      repoName = repo,
      branch = branch,
      baseCommitSha = snapshot.headCommitSha,
      baseTreeSha = snapshot.baseTreeSha,
      remoteTreeTruncated = truncated,
      targetPrefix = cleanPrefix,
      items = sorted,
    )
  }

  private suspend fun fetchRemoteLfsPointerOid(owner: String, repo: String, blobSha: String): String? {
    val res = api.getGitBlob(owner, repo, blobSha)
    if (!res.isSuccessful) return null
    val b64 = res.body()?.content?.replace("\n", "") ?: return null
    val text = try {
      String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
    } catch (_: Exception) {
      return null
    }
    return parseLfsOidFromPointer(text)
  }

  companion object {
    fun parseLfsOidFromPointer(pointerText: String): String? {
      if (!pointerText.contains("git-lfs.github.com/spec/v1")) return null
      val line = pointerText.lines().firstOrNull { it.trim().startsWith("oid sha256:") } ?: return null
      return line.trim().removePrefix("oid sha256:").trim().takeIf { it.length == 64 }
    }

    fun normalizePrefix(prefix: String): String {
      return prefix.trim().replace('\\', '/').trim('/')
    }

    /**
     * Pure deterministic diff classifier used for fast local verification and unit testing.
     */
    fun classifyHashes(
      localEntries: Map<String, Pair<Long, String>>, // path -> (size, sha1OrLfsOid)
      remoteEntries: Map<String, Pair<Long, String>>, // path -> (size, sha1OrLfsOid)
      targetPrefix: String = "",
    ): List<DiffFileItem> {
      val cleanPrefix = normalizePrefix(targetPrefix)
      val out = mutableListOf<DiffFileItem>()
      for ((path, localPair) in localEntries) {
        val scoped = if (cleanPrefix.isEmpty()) path else "$cleanPrefix/$path"
        val remotePair = remoteEntries[scoped]
        if (remotePair == null) {
          out.add(
            DiffFileItem(
              relativePath = scoped,
              status = DiffStatus.ADDED,
              localSizeBytes = localPair.first,
              remoteSizeBytes = null,
              localSha1OrOid = localPair.second,
              remoteSha1OrOid = null,
              isLfs = false,
              lfsClassification = LfsClassification.STANDARD_GIT,
              selectedForApply = true,
            ),
          )
        } else {
          val status = if (localPair.second == remotePair.second) DiffStatus.UNCHANGED else DiffStatus.MODIFIED
          out.add(
            DiffFileItem(
              relativePath = scoped,
              status = status,
              localSizeBytes = localPair.first,
              remoteSizeBytes = remotePair.first,
              localSha1OrOid = localPair.second,
              remoteSha1OrOid = remotePair.second,
              isLfs = false,
              lfsClassification = LfsClassification.STANDARD_GIT,
              selectedForApply = status != DiffStatus.UNCHANGED,
            ),
          )
        }
      }
      val localScopedSet = localEntries.keys.map { if (cleanPrefix.isEmpty()) it else "$cleanPrefix/$it" }.toSet()
      for ((remotePath, remotePair) in remoteEntries) {
        val inScope = if (cleanPrefix.isEmpty()) true else remotePath.startsWith("$cleanPrefix/")
        if (inScope && remotePath !in localScopedSet) {
          out.add(
            DiffFileItem(
              relativePath = remotePath,
              status = DiffStatus.DELETED,
              localSizeBytes = 0L,
              remoteSizeBytes = remotePair.first,
              localSha1OrOid = null,
              remoteSha1OrOid = remotePair.second,
              isLfs = false,
              lfsClassification = LfsClassification.STANDARD_GIT,
              selectedForApply = false,
            ),
          )
        }
      }
      return out
    }

    fun computeTreeDeltaPreview(
      currentTree: List<GitTreeNodeDto>,
      targetTree: List<GitTreeNodeDto>,
    ): List<DiffFileItem> {
      val currentMap = currentTree.filter { it.type == "blob" }.associate { it.path to ((it.size ?: 0L) to it.sha) }
      val targetMap = targetTree.filter { it.type == "blob" }.associate { it.path to ((it.size ?: 0L) to it.sha) }
      return classifyHashes(localEntries = targetMap, remoteEntries = currentMap)
        .filter { it.status != DiffStatus.UNCHANGED }
    }
  }
}
