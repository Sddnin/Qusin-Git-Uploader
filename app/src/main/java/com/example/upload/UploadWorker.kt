package com.example.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.QusinApplication
import com.example.R
import com.example.core.common.AppError
import com.example.core.common.FormatUtils
import com.example.core.storage.UploadHistoryEntity
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.SelectedFileItem
import com.example.domain.model.UploadStage
import com.example.git.GitUploadFileItem
import com.example.gitlfs.GitLfsClassifier
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SerializedUploadPayload(
  val taskId: String,
  val projectName: String,
  val owner: String,
  val repoName: String,
  val branch: String,
  val commitMessage: String,
  val expectedBaseSha: String?,
  val conflictStrategy: String,
  val files: List<SerializedUploadFile>,
  val filesToDelete: List<String> = emptyList(),
  val updateMode: String = "INITIAL_UPLOAD",
  val addedCount: Int = 0,
  val modifiedCount: Int = 0,
  val deletedCount: Int = 0,
)

@Serializable
data class SerializedUploadFile(
  val relativePath: String,
  val sizeBytes: Long,
  val useLfs: Boolean,
  val sourceUriString: String?,
  val localTempPath: String?,
)

class UploadWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun doWork(): Result {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
    val container = (applicationContext as QusinApplication).container
    val taskDao = container.database.uploadTaskDao()
    val historyDao = container.database.uploadHistoryDao()
    val taskEntity = taskDao.getById(taskId) ?: return Result.failure()

    // Idempotency guard: never re-commit if this task already completed
    if (taskEntity.stage == UploadStage.COMPLETED.name && !taskEntity.commitSha.isNullOrBlank()) {
      return Result.success()
    }

    val payload = try {
      json.decodeFromString<SerializedUploadPayload>(taskEntity.payloadJson)
    } catch (_: Throwable) {
      return Result.failure()
    }

    val settings = container.settingsRepository.settings.first()
    val concurrency = settings.uploadConcurrency.coerceIn(1, 6)

    createNotificationChannel()
    try {
      setForeground(buildForegroundInfo(taskId.hashCode(), payload.projectName, 0, "Preparing..."))
    } catch (_: Exception) {
    }

    val logBuffer = StringBuilder(taskEntity.logs)
    fun appendLog(line: String) {
      synchronized(logBuffer) {
        logBuffer.appendLine(line)
        if (logBuffer.length > 16_000) {
          logBuffer.delete(0, logBuffer.length - 12_000)
        }
      }
    }

    fun snapshotLogs(): String = synchronized(logBuffer) { logBuffer.toString() }

    val uploadedBytesAtomic = AtomicLong(0L)
    val transferStartMs = AtomicLong(0L)
    var currentStage = UploadStage.PREPARING
    val totalBytes = payload.files.sumOf { it.sizeBytes }.coerceAtLeast(1L)

    val uploadItems = payload.files.map { f ->
      val item = SelectedFileItem(
        id = f.relativePath,
        name = f.relativePath.substringAfterLast('/'),
        relativePath = f.relativePath,
        extension = FormatUtils.extensionOf(f.relativePath),
        fileType = FormatUtils.mimeCategory(f.relativePath),
        sizeBytes = f.sizeBytes,
        sourceUri = f.sourceUriString?.let { Uri.parse(it) },
        localTempPath = f.localTempPath,
        useLfs = f.useLfs,
        lfsClassification = GitLfsClassifier.classify(f.sizeBytes),
      )
      GitUploadFileItem(
        relativePath = f.relativePath,
        sizeBytes = f.sizeBytes,
        useLfs = f.useLfs,
        openStream = { openStreamForItem(applicationContext, item) },
      )
    }

    suspend fun persistProgress() {
      val now = System.currentTimeMillis()
      val transferred = uploadedBytesAtomic.get().coerceAtMost(totalBytes)
      val startedAt = transferStartMs.get()
      val elapsedSec = if (startedAt > 0L) ((now - startedAt).coerceAtLeast(250L)) / 1000.0 else 1.0
      val speed = if (startedAt > 0L && transferred > 0L) transferred / elapsedSec else 0.0
      val remainingBytes = (totalBytes - transferred).coerceAtLeast(0L)
      val eta = if (speed > 100.0) (remainingBytes / speed).toLong() else 0L
      val pct = ((transferred.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 99)

      taskDao.upsert(
        taskEntity.copy(
          uploadedBytes = transferred,
          speedBytesPerSec = speed,
          etaSeconds = eta,
          stage = currentStage.name,
          logs = snapshotLogs(),
          updatedAt = now,
        ),
      )
      try {
        setForeground(
          buildForegroundInfo(
            notificationId = taskId.hashCode(),
            projectName = payload.projectName,
            progressPercent = pct,
            status = "${currentStage.name} • $pct%",
          ),
        )
      } catch (_: Exception) {
      }
    }

    return try {
      val strategy = ConflictStrategy.entries.firstOrNull { it.name == payload.conflictStrategy }
        ?: ConflictStrategy.ABORT

      val result = coroutineScope {
        val progressTicker = launch {
          while (isActive) {
            delay(350L)
            persistProgress()
          }
        }
        try {
          container.gitEngine.pushFilesAtomically(
            owner = payload.owner,
            repo = payload.repoName,
            branch = payload.branch,
            commitMessage = payload.commitMessage,
            files = uploadItems,
            filesToDelete = payload.filesToDelete,
            expectedBaseSha = payload.expectedBaseSha,
            conflictStrategy = strategy,
            concurrency = concurrency,
            isCancelled = { isStopped },
            onStageChange = { stage ->
              currentStage = stage
              if ((stage == UploadStage.UPLOADING || stage == UploadStage.UPLOADING_LFS) &&
                transferStartMs.get() == 0L
              ) {
                transferStartMs.compareAndSet(0L, System.currentTimeMillis())
              }
              appendLog("[STAGE] -> ${stage.name}")
              persistProgress()
            },
            onLog = { line -> appendLog(line) },
            onProgressDelta = { delta ->
              if (transferStartMs.get() == 0L) {
                transferStartMs.compareAndSet(0L, System.currentTimeMillis())
              }
              uploadedBytesAtomic.addAndGet(delta)
            },
          )
        } finally {
          progressTicker.cancel()
        }
      }

      val completedAt = System.currentTimeMillis()
      val actualDeltaBytes = payload.files.sumOf { it.sizeBytes }
      val finalTask = taskEntity.copy(
        uploadedBytes = totalBytes,
        speedBytesPerSec = 0.0,
        etaSeconds = 0L,
        stage = UploadStage.COMPLETED.name,
        commitSha = result.commitSha,
        errorCode = null,
        errorCause = null,
        errorFix = null,
        logs = snapshotLogs(),
        updatedAt = completedAt,
      )
      taskDao.upsert(finalTask)

      historyDao.insert(
        UploadHistoryEntity(
          taskId = taskId,
          projectName = payload.projectName,
          repoFullName = "${payload.owner}/${payload.repoName}",
          branch = payload.branch,
          commitSha = result.commitSha,
          commitUrl = result.commitUrl,
          repoUrl = "https://github.com/${payload.owner}/${payload.repoName}",
          fileCount = result.totalFilesCommitted + result.deletedFilesCount,
          totalSizeBytes = actualDeltaBytes,
          timestamp = completedAt,
          status = UploadStage.COMPLETED.name,
          errorDetails = null,
          logs = snapshotLogs(),
          previousCommitSha = result.previousCommitSha,
          previousTreeSha = result.previousTreeSha,
          newTreeSha = result.newTreeSha,
          updateMode = payload.updateMode,
          addedCount = payload.addedCount,
          modifiedCount = payload.modifiedCount,
          deletedCount = result.deletedFilesCount,
          deltaBytes = actualDeltaBytes,
        ),
      )
      Result.success()
    } catch (t: Throwable) {
      val now = System.currentTimeMillis()
      val transferred = uploadedBytesAtomic.get().coerceAtMost(totalBytes)
      if (isStopped) {
        appendLog("[PAUSED] Task paused or cancelled by user.")
        taskDao.upsert(
          taskEntity.copy(
            uploadedBytes = transferred,
            speedBytesPerSec = 0.0,
            stage = UploadStage.PAUSED.name,
            logs = snapshotLogs(),
            updatedAt = now,
          ),
        )
        return Result.failure()
      }

      val appError = AppError.fromThrowable(t)
      appendLog("[ERROR] ${appError.code}: ${appError.technicalDetail ?: appError.titleEn}")
      taskDao.upsert(
        taskEntity.copy(
          uploadedBytes = transferred,
          speedBytesPerSec = 0.0,
          stage = UploadStage.FAILED.name,
          errorCode = appError.code,
          errorCause = appError.causeVi,
          errorFix = appError.fixVi,
          logs = snapshotLogs(),
          updatedAt = now,
        ),
      )

      historyDao.insert(
        UploadHistoryEntity(
          taskId = taskId,
          projectName = payload.projectName,
          repoFullName = "${payload.owner}/${payload.repoName}",
          branch = payload.branch,
          commitSha = null,
          commitUrl = null,
          repoUrl = "https://github.com/${payload.owner}/${payload.repoName}",
          fileCount = payload.files.size + payload.filesToDelete.size,
          totalSizeBytes = payload.files.sumOf { it.sizeBytes },
          timestamp = now,
          status = UploadStage.FAILED.name,
          errorDetails = "${appError.code}: ${appError.technicalDetail ?: appError.titleEn}",
          logs = snapshotLogs(),
          updateMode = payload.updateMode,
          addedCount = payload.addedCount,
          modifiedCount = payload.modifiedCount,
          deletedCount = payload.filesToDelete.size,
          deltaBytes = payload.files.sumOf { it.sizeBytes },
        ),
      )
      Result.failure()
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        applicationContext.getString(R.string.upload_notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = applicationContext.getString(R.string.upload_notification_channel_desc)
      }
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundInfo(
    notificationId: Int,
    projectName: String,
    progressPercent: Int,
    status: String,
  ): ForegroundInfo {
    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Qusin Upload: $projectName")
      .setContentText(status)
      .setProgress(100, progressPercent, progressPercent == 0)
      .setOngoing(true)
      .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }

  companion object {
    const val KEY_TASK_ID = "upload_task_id"
    const val CHANNEL_ID = "qusin_upload_channel"
  }
}
