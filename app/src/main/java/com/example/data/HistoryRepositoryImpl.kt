package com.example.data

import com.example.core.storage.UploadHistoryDao
import com.example.core.storage.UploadHistoryEntity
import com.example.core.storage.UploadTaskDao
import com.example.core.storage.UploadTaskEntity
import com.example.domain.model.UploadHistoryModel
import com.example.domain.model.UploadStage
import com.example.domain.model.UploadTaskModel
import com.example.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepositoryImpl(
  private val historyDao: UploadHistoryDao,
  private val taskDao: UploadTaskDao,
) : HistoryRepository {

  override val historyItems: Flow<List<UploadHistoryModel>> = historyDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  override val activeTasks: Flow<List<UploadTaskModel>> = taskDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  override val totalCompletedUploads: Flow<Int> = historyDao.observeCompletedCount()

  override val totalUploadedBytes: Flow<Long> = historyDao.observeTotalUploadedBytes()

  override suspend fun saveHistory(item: UploadHistoryModel) {
    historyDao.insert(
      UploadHistoryEntity(
        id = item.id,
        taskId = item.taskId,
        projectName = item.projectName,
        repoFullName = item.repoFullName,
        branch = item.branch,
        commitSha = item.commitSha,
        commitUrl = item.commitUrl,
        repoUrl = item.repoUrl,
        fileCount = item.fileCount,
        totalSizeBytes = item.totalSizeBytes,
        timestamp = item.timestamp,
        status = item.status.name,
        errorDetails = item.errorDetails,
        logs = item.logs,
        previousCommitSha = item.previousCommitSha,
        previousTreeSha = item.previousTreeSha,
        newTreeSha = item.newTreeSha,
        updateMode = item.updateMode,
        addedCount = item.addedCount,
        modifiedCount = item.modifiedCount,
        deletedCount = item.deletedCount,
        deltaBytes = item.deltaBytes,
      ),
    )
  }

  override suspend fun upsertTask(task: UploadTaskModel) {
    val existing = taskDao.getById(task.id)
    taskDao.upsert(
      UploadTaskEntity(
        id = task.id,
        projectName = task.projectName,
        repoFullName = task.repoFullName,
        branch = task.branch,
        commitMessage = task.commitMessage,
        totalBytes = task.totalBytes,
        uploadedBytes = task.uploadedBytes,
        speedBytesPerSec = task.speedBytesPerSec,
        etaSeconds = task.etaSeconds,
        stage = task.stage.name,
        fileCount = task.fileCount,
        lfsFileCount = task.lfsFileCount,
        commitSha = task.commitSha,
        errorCode = task.errorCode,
        errorCause = task.errorCause,
        errorFix = task.errorFix,
        logs = task.logs,
        payloadJson = existing?.payloadJson ?: "{}",
        createdAt = task.createdAt,
        updatedAt = task.updatedAt,
      ),
    )
  }

  override suspend fun getTaskById(taskId: String): UploadTaskModel? {
    return taskDao.getById(taskId)?.toDomain()
  }

  override suspend fun clearLocalHistory() {
    historyDao.deleteAll()
  }

  private fun UploadHistoryEntity.toDomain() = UploadHistoryModel(
    id = id,
    taskId = taskId,
    projectName = projectName,
    repoFullName = repoFullName,
    branch = branch,
    commitSha = commitSha,
    commitUrl = commitUrl,
    repoUrl = repoUrl,
    fileCount = fileCount,
    totalSizeBytes = totalSizeBytes,
    timestamp = timestamp,
    status = UploadStage.entries.firstOrNull { it.name == status } ?: UploadStage.FAILED,
    errorDetails = errorDetails,
    logs = logs,
    previousCommitSha = previousCommitSha,
    previousTreeSha = previousTreeSha,
    newTreeSha = newTreeSha,
    updateMode = updateMode,
    addedCount = addedCount,
    modifiedCount = modifiedCount,
    deletedCount = deletedCount,
    deltaBytes = deltaBytes,
  )

  private fun UploadTaskEntity.toDomain() = UploadTaskModel(
    id = id,
    projectName = projectName,
    repoFullName = repoFullName,
    branch = branch,
    commitMessage = commitMessage,
    totalBytes = totalBytes,
    uploadedBytes = uploadedBytes,
    speedBytesPerSec = speedBytesPerSec,
    etaSeconds = etaSeconds,
    stage = UploadStage.entries.firstOrNull { it.name == stage } ?: UploadStage.PREPARING,
    fileCount = fileCount,
    lfsFileCount = lfsFileCount,
    commitSha = commitSha,
    errorCode = errorCode,
    errorCause = errorCause,
    errorFix = errorFix,
    logs = logs,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )
}
