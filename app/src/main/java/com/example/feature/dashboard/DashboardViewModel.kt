package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.example.data.AppContainer
import com.example.domain.model.GitHubRepo
import com.example.domain.model.GitHubUser
import com.example.domain.model.UploadHistoryModel
import com.example.domain.model.UploadStage
import com.example.domain.model.UploadTaskModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
  val user: GitHubUser? = null,
  val isConnecting: Boolean = false,
  val connectionError: AppError? = null,
  val recentRepos: List<GitHubRepo> = emptyList(),
  val totalUploads: Int = 0,
  val totalBytesUploaded: Long = 0L,
  val activeTasks: List<UploadTaskModel> = emptyList(),
  val recentHistory: List<UploadHistoryModel> = emptyList(),
  val systemMessage: String = "Git LFS Batch API v1 • AES-256 Keystore Active",
)

class DashboardViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val isConnecting = MutableStateFlow(false)
  private val connectionError = MutableStateFlow<AppError?>(null)
  private val systemMessage = MutableStateFlow("Git LFS Batch API v1 • AES-256 Keystore Active")

  private val baseFlows = combine(
    container.gitHubAuthRepository.currentUser,
    container.gitHubRepoRepository.cachedRepos,
    container.historyRepository.totalCompletedUploads,
    container.historyRepository.totalUploadedBytes,
  ) { user, repos, count, bytes ->
    Quad(user, repos, count, bytes)
  }

  private val activityFlows = combine(
    container.historyRepository.activeTasks,
    container.historyRepository.historyItems,
    isConnecting,
    connectionError,
    systemMessage,
  ) { tasks, history, connecting, err, sysMsg ->
    ActivityTuple(tasks, history, connecting, err, sysMsg)
  }

  val uiState: StateFlow<DashboardUiState> = combine(baseFlows, activityFlows) { base, act ->
    DashboardUiState(
      user = base.first,
      isConnecting = act.connecting,
      connectionError = act.error,
      recentRepos = base.second.take(4),
      totalUploads = base.third,
      totalBytesUploaded = base.fourth,
      activeTasks = act.tasks.filter {
        it.stage != UploadStage.COMPLETED && it.stage != UploadStage.FAILED
      },
      recentHistory = act.history.take(5),
      systemMessage = act.systemMessage,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = DashboardUiState(),
  )

  init {
    refreshSessionIfAvailable()
  }

  fun refreshSessionIfAvailable() {
    viewModelScope.launch {
      if (!container.gitHubAuthRepository.hasSavedToken()) return@launch
      isConnecting.value = true
      connectionError.value = null
      when (val res = container.gitHubAuthRepository.refreshSavedSession()) {
        is AppResult.Success -> {
          systemMessage.value = "GitHub @${res.data.login} • LFS Ready"
          container.gitHubRepoRepository.fetchRepositories()
        }
        is AppResult.Failure -> {
          connectionError.value = res.error
          systemMessage.value = "${res.error.code} • ${res.error.titleEn}"
        }
      }
      isConnecting.value = false
    }
  }

  fun pauseTask(taskId: String) {
    container.uploadCoordinator.pauseOrCancel(taskId)
  }

  private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
  private data class ActivityTuple(
    val tasks: List<UploadTaskModel>,
    val history: List<UploadHistoryModel>,
    val connecting: Boolean,
    val error: AppError?,
    val systemMessage: String,
  )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DashboardViewModel(container) as T
    }
  }
}
