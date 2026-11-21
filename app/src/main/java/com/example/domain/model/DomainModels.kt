package com.example.domain.model

import android.net.Uri
import com.example.core.common.AppLanguage

enum class LfsClassification {
  STANDARD_GIT,
  LFS_RECOMMENDED,
  LFS_REQUIRED,
}

enum class ZipMode {
  RAW_ZIP,
  EXTRACT_AND_UPLOAD,
}

enum class UploadStage {
  PREPARING,
  SCANNING,
  COMPRESSING,
  EXTRACTING,
  UPLOADING,
  UPLOADING_LFS,
  COMMITTING,
  PUSHING,
  VERIFYING,
  COMPLETED,
  FAILED,
  PAUSED,
}

enum class ConflictStrategy {
  ABORT,
  SYNC_AND_STACK,
  FORCE_UPDATE,
}

enum class UpdateMode {
  UPDATE_CHANGES,
  SYNC_PROJECT,
  OVERWRITE_PROJECT,
  UPDATE_SELECTED,
}

enum class DiffStatus {
  ADDED,
  MODIFIED,
  DELETED,
  UNCHANGED,
}

data class GitHubUser(
  val login: String,
  val name: String?,
  val avatarUrl: String?,
  val htmlUrl: String?,
  val publicRepos: Int,
  val privateRepos: Int,
  val scopes: List<String> = emptyList(),
  val hasWriteAccess: Boolean = true,
)

data class GitHubRepo(
  val id: Long,
  val name: String,
  val fullName: String,
  val ownerLogin: String,
  val description: String?,
  val isPrivate: Boolean,
  val defaultBranch: String,
  val updatedAt: String,
  val htmlUrl: String,
  val cloneUrl: String,
  val canPush: Boolean,
  val sizeKb: Long,
)

data class GitHubBranch(
  val name: String,
  val commitSha: String,
  val isProtected: Boolean,
)

data class GitHubCommit(
  val sha: String,
  val message: String,
  val authorName: String,
  val dateIso: String,
  val htmlUrl: String,
)

data class SelectedFileItem(
  val id: String,
  val name: String,
  val relativePath: String,
  val extension: String,
  val fileType: String,
  val sizeBytes: Long,
  val sourceUri: Uri? = null,
  val localTempPath: String? = null,
  val isSelected: Boolean = true,
  val useLfs: Boolean = false,
  val lfsClassification: LfsClassification = LfsClassification.STANDARD_GIT,
)

data class ZipInspectionResult(
  val zipName: String,
  val zipSizeBytes: Long,
  val extractedDirPath: String,
  val entries: List<SelectedFileItem>,
  val skippedExcludedCount: Int,
  val duplicateCount: Int,
  val totalExtractedBytes: Long,
)

data class DiffFileItem(
  val relativePath: String,
  val status: DiffStatus,
  val localSizeBytes: Long,
  val remoteSizeBytes: Long?,
  val localSha1OrOid: String?,
  val remoteSha1OrOid: String?,
  val isLfs: Boolean,
  val lfsClassification: LfsClassification,
  val selectedForApply: Boolean,
  val sourceItem: SelectedFileItem? = null,
)

data class ProjectDiffPlan(
  val owner: String,
  val repoName: String,
  val branch: String,
  val baseCommitSha: String?,
  val baseTreeSha: String?,
  val remoteTreeTruncated: Boolean,
  val targetPrefix: String,
  val items: List<DiffFileItem>,
) {
  val addedCount: Int get() = items.count { it.status == DiffStatus.ADDED }
  val modifiedCount: Int get() = items.count { it.status == DiffStatus.MODIFIED }
  val deletedCount: Int get() = items.count { it.status == DiffStatus.DELETED }
  val unchangedCount: Int get() = items.count { it.status == DiffStatus.UNCHANGED }
}

data class RollbackPreview(
  val historyItem: UploadHistoryModel,
  val currentHeadSha: String,
  val targetCommitSha: String,
  val targetTreeSha: String,
  val revertedEntries: List<DiffFileItem>,
)

data class UploadTaskModel(
  val id: String,
  val projectName: String,
  val repoFullName: String,
  val branch: String,
  val commitMessage: String,
  val totalBytes: Long,
  val uploadedBytes: Long,
  val speedBytesPerSec: Double,
  val etaSeconds: Long,
  val stage: UploadStage,
  val fileCount: Int,
  val lfsFileCount: Int,
  val commitSha: String? = null,
  val errorCode: String? = null,
  val errorCause: String? = null,
  val errorFix: String? = null,
  val logs: String = "",
  val createdAt: Long,
  val updatedAt: Long,
) {
  val progressFraction: Float
    get() = when (stage) {
      UploadStage.COMPLETED -> 1f
      else -> if (totalBytes > 0L) (uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f) else 0f
    }
}

data class UploadHistoryModel(
  val id: Long = 0,
  val taskId: String,
  val projectName: String,
  val repoFullName: String,
  val branch: String,
  val commitSha: String?,
  val commitUrl: String?,
  val repoUrl: String,
  val fileCount: Int,
  val totalSizeBytes: Long,
  val timestamp: Long,
  val status: UploadStage,
  val errorDetails: String?,
  val logs: String,
  val previousCommitSha: String? = null,
  val previousTreeSha: String? = null,
  val newTreeSha: String? = null,
  val updateMode: String? = null,
  val addedCount: Int = 0,
  val modifiedCount: Int = 0,
  val deletedCount: Int = 0,
  val deltaBytes: Long = 0L,
)

data class AppSettings(
  val isDarkMode: Boolean = true,
  val language: AppLanguage = AppLanguage.SYSTEM,
  val defaultRepoFullName: String = "",
  val defaultBranch: String = "main",
  val defaultCommitMessage: String = "Update project via Qusin Git Uploader",
  val lfsEnabled: Boolean = true,
  val lfsThresholdMb: Int = 50,
  val uploadConcurrency: Int = 2,
  val maxExtractionSizeMb: Int = 500,
  val excludedFolders: Set<String> = DEFAULT_EXCLUDED_FOLDERS,
) {
  companion object {
    val DEFAULT_EXCLUDED_FOLDERS = setOf(
      "node_modules",
      ".next",
      ".git",
      "build",
      ".gradle",
      "dist",
      ".idea",
    )
  }
}

data class LfsFilePlan(
  val relativePath: String,
  val sizeBytes: Long,
  val sha256Hex: String,
  val pointerContent: String,
)
