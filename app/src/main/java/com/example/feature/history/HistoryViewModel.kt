package com.example.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.data.AppContainer
import com.example.domain.model.RollbackPreview
import com.example.domain.model.UploadHistoryModel
import com.example.domain.model.UploadStage
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryStatusFilter {
  ALL,
  COMPLETED,
  FAILED,
  RUNNING,
}

data class HistoryUiState(
  val items: List<UploadHistoryModel> = emptyList(),
  val searchQuery: String = "",
  val filter: HistoryStatusFilter = HistoryStatusFilter.ALL,
  val isRollingBack: Boolean = false,
  val rollbackPreview: RollbackPreview? = null,
  val rollbackError: AppError? = null,
  val statusBanner: String? = null,
)

class HistoryViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val statusFilter = MutableStateFlow(HistoryStatusFilter.ALL)
  private val isRollingBack = MutableStateFlow(false)
  private val rollbackPreview = MutableStateFlow<RollbackPreview?>(null)
  private val rollbackError = MutableStateFlow<AppError?>(null)
  private val statusBanner = MutableStateFlow<String?>(null)

  private val rollbackState = combine(
    isRollingBack,
    rollbackPreview,
    rollbackError,
    statusBanner,
  ) { busy, preview, err, banner ->
    RollbackStateTuple(busy, preview, err, banner)
  }

  val uiState: StateFlow<HistoryUiState> = combine(
    container.historyRepository.historyItems,
    searchQuery,
    statusFilter,
    rollbackState,
  ) { all, query, filter, rb ->
    val byStatus = when (filter) {
      HistoryStatusFilter.ALL -> all
      HistoryStatusFilter.COMPLETED -> all.filter { it.status == UploadStage.COMPLETED }
      HistoryStatusFilter.FAILED -> all.filter { it.status == UploadStage.FAILED }
      HistoryStatusFilter.RUNNING -> all.filter {
        it.status != UploadStage.COMPLETED && it.status != UploadStage.FAILED
      }
    }
    val filtered = if (query.isBlank()) {
      byStatus
    } else {
      byStatus.filter {
        it.projectName.contains(query, ignoreCase = true) ||
          it.repoFullName.contains(query, ignoreCase = true) ||
          it.branch.contains(query, ignoreCase = true) ||
          it.commitSha.orEmpty().contains(query, ignoreCase = true)
      }
    }
    HistoryUiState(
      items = filtered,
      searchQuery = query,
      filter = filter,
      isRollingBack = rb.busy,
      rollbackPreview = rb.preview,
      rollbackError = rb.error,
      statusBanner = rb.banner,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = HistoryUiState(),
  )

  fun updateQuery(query: String) {
    searchQuery.value = query
  }

  fun setFilter(filter: HistoryStatusFilter) {
    statusFilter.value = filter
  }

  fun clearLocalHistory() {
    viewModelScope.launch {
      container.historyRepository.clearLocalHistory()
    }
  }

  fun prepareRollback(item: UploadHistoryModel) {
    val targetCommit = item.previousCommitSha ?: return
    val targetTree = item.previousTreeSha ?: return
    val parts = item.repoFullName.split('/')
    if (parts.size != 2) return
    val owner = parts[0]
    val repo = parts[1]

    viewModelScope.launch {
      isRollingBack.value = true
      rollbackError.value = null
      statusBanner.value = null
      try {
        val (currentHead, reverted) = withContext(container.dispatchers.io) {
          container.gitEngine.previewRollback(owner, repo, item.branch, targetTree)
        }
        rollbackPreview.value = RollbackPreview(
          historyItem = item,
          currentHeadSha = currentHead,
          targetCommitSha = targetCommit,
          targetTreeSha = targetTree,
          revertedEntries = reverted,
        )
      } catch (t: Throwable) {
        rollbackError.value = AppError.fromThrowable(t)
      }
      isRollingBack.value = false
    }
  }

  fun dismissRollback() {
    rollbackPreview.value = null
  }

  fun confirmRollback(successMsg: String) {
    val preview = rollbackPreview.value ?: return
    val parts = preview.historyItem.repoFullName.split('/')
    if (parts.size != 2) return
    val owner = parts[0]
    val repo = parts[1]

    viewModelScope.launch {
      isRollingBack.value = true
      rollbackError.value = null
      try {
        val pushRes = withContext(container.dispatchers.io) {
          container.gitEngine.executeRollback(
            owner = owner,
            repo = repo,
            branch = preview.historyItem.branch,
            targetCommitSha = preview.targetCommitSha,
            targetTreeSha = preview.targetTreeSha,
          )
        }
        container.historyRepository.saveHistory(
          UploadHistoryModel(
            taskId = "rollback_${UUID.randomUUID().toString().take(8)}",
            projectName = "Rollback -> ${preview.targetCommitSha.take(7)}",
            repoFullName = preview.historyItem.repoFullName,
            branch = preview.historyItem.branch,
            commitSha = pushRes.commitSha,
            commitUrl = pushRes.commitUrl,
            repoUrl = preview.historyItem.repoUrl,
            fileCount = preview.revertedEntries.size,
            totalSizeBytes = 0L,
            timestamp = System.currentTimeMillis(),
            status = UploadStage.COMPLETED,
            errorDetails = null,
            logs = "[ROLLBACK] Restored tree ${preview.targetTreeSha} from commit ${preview.targetCommitSha} -> new commit ${pushRes.commitSha}",
            previousCommitSha = pushRes.previousCommitSha,
            previousTreeSha = pushRes.previousTreeSha,
            newTreeSha = pushRes.newTreeSha,
            updateMode = "ROLLBACK",
            addedCount = preview.revertedEntries.count { it.status == com.example.domain.model.DiffStatus.ADDED },
            modifiedCount = preview.revertedEntries.count { it.status == com.example.domain.model.DiffStatus.MODIFIED },
            deletedCount = preview.revertedEntries.count { it.status == com.example.domain.model.DiffStatus.DELETED },
            deltaBytes = 0L,
          ),
        )
        rollbackPreview.value = null
        statusBanner.value = "$successMsg (${pushRes.commitSha.take(7)})"
      } catch (t: Throwable) {
        rollbackError.value = AppError.fromThrowable(t)
      }
      isRollingBack.value = false
    }
  }

  private data class RollbackStateTuple(
    val busy: Boolean,
    val preview: RollbackPreview?,
    val error: AppError?,
    val banner: String?,
  )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return HistoryViewModel(container) as T
    }
  }
}
