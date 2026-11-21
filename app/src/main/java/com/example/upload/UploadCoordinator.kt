package com.example.upload

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.storage.UploadTaskDao
import com.example.core.storage.UploadTaskEntity
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.SelectedFileItem
import com.example.domain.model.UploadStage
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UploadCoordinator(
  private val context: Context,
  private val taskDao: UploadTaskDao,
) {
  private val workManager: WorkManager by lazy {
    WorkManager.getInstance(context.applicationContext)
  }
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun enqueueUpload(
    projectName: String,
    owner: String,
    repoName: String,
    branch: String,
    commitMessage: String,
    expectedBaseSha: String?,
    conflictStrategy: ConflictStrategy,
    selectedFiles: List<SelectedFileItem>,
    filesToDelete: List<String> = emptyList(),
    updateMode: String = "INITIAL_UPLOAD",
    addedCount: Int = selectedFiles.size,
    modifiedCount: Int = 0,
  ): String {
    val taskId = "task_${UUID.randomUUID().toString().take(12)}"
    val now = System.currentTimeMillis()
    val payload = SerializedUploadPayload(
      taskId = taskId,
      projectName = projectName,
      owner = owner,
      repoName = repoName,
      branch = branch,
      commitMessage = commitMessage,
      expectedBaseSha = expectedBaseSha,
      conflictStrategy = conflictStrategy.name,
      files = selectedFiles.map { item ->
        SerializedUploadFile(
          relativePath = item.relativePath,
          sizeBytes = item.sizeBytes,
          useLfs = item.useLfs,
          sourceUriString = item.sourceUri?.toString(),
          localTempPath = item.localTempPath,
        )
      },
      filesToDelete = filesToDelete,
      updateMode = updateMode,
      addedCount = addedCount,
      modifiedCount = modifiedCount,
      deletedCount = filesToDelete.size,
    )

    val entity = UploadTaskEntity(
      id = taskId,
      projectName = projectName,
      repoFullName = "$owner/$repoName",
      branch = branch,
      commitMessage = commitMessage,
      totalBytes = selectedFiles.sumOf { it.sizeBytes }.coerceAtLeast(1L),
      uploadedBytes = 0L,
      speedBytesPerSec = 0.0,
      etaSeconds = 0L,
      stage = UploadStage.PREPARING.name,
      fileCount = selectedFiles.size + filesToDelete.size,
      lfsFileCount = selectedFiles.count { it.useLfs },
      commitSha = null,
      errorCode = null,
      errorCause = null,
      errorFix = null,
      logs = "[QUEUE] Mode=$updateMode • $projectName (+$addedCount ~$modifiedCount -${filesToDelete.size}) -> $owner/$repoName@$branch\n",
      payloadJson = json.encodeToString(payload),
      createdAt = now,
      updatedAt = now,
    )
    taskDao.upsert(entity)
    launchWorker(taskId)
    return taskId
  }

  suspend fun retryOrResume(taskId: String) {
    val existing = taskDao.getById(taskId) ?: return
    if (existing.stage == UploadStage.COMPLETED.name && !existing.commitSha.isNullOrBlank()) return
    taskDao.upsert(
      existing.copy(
        stage = UploadStage.PREPARING.name,
        errorCode = null,
        errorCause = null,
        errorFix = null,
        logs = existing.logs + "[RETRY] Resuming/retrying safely (existing LFS objects will be reused)...\n",
        updatedAt = System.currentTimeMillis(),
      ),
    )
    launchWorker(taskId)
  }

  fun pauseOrCancel(taskId: String) {
    workManager.cancelUniqueWork(uniqueWorkName(taskId))
  }

  suspend fun removeTask(taskId: String) {
    workManager.cancelUniqueWork(uniqueWorkName(taskId))
    taskDao.deleteById(taskId)
  }

  private fun launchWorker(taskId: String) {
    val request = OneTimeWorkRequestBuilder<UploadWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setInputData(Data.Builder().putString(UploadWorker.KEY_TASK_ID, taskId).build())
      .build()

    workManager.enqueueUniqueWork(
      uniqueWorkName(taskId),
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  private fun uniqueWorkName(taskId: String) = "qusin_upload_$taskId"
}
