package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppLanguage
import com.example.data.AppContainer
import com.example.domain.model.AppSettings
import com.example.domain.model.GitHubUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val user: GitHubUser? = null,
  val cacheBytes: Long = 0L,
  val statusMessage: String? = null,
)

class SettingsViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val cacheBytes = MutableStateFlow(container.zipManager.calculateCacheBytes())
  private val statusMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<SettingsUiState> = combine(
    container.settingsRepository.settings,
    container.gitHubAuthRepository.currentUser,
    cacheBytes,
    statusMessage,
  ) { settings, user, cache, msg ->
    SettingsUiState(settings = settings, user = user, cacheBytes = cache, statusMessage = msg)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SettingsUiState(),
  )

  fun setDarkMode(dark: Boolean) {
    viewModelScope.launch { container.settingsRepository.setDarkMode(dark) }
  }

  fun setLanguage(lang: AppLanguage) {
    viewModelScope.launch { container.settingsRepository.setLanguage(lang) }
  }

  fun setLfsConfig(enabled: Boolean, thresholdMb: Int) {
    viewModelScope.launch { container.settingsRepository.setLfsConfig(enabled, thresholdMb) }
  }

  fun setDefaultRepoAndBranch(repo: String, branch: String) {
    viewModelScope.launch { container.settingsRepository.setDefaultRepoAndBranch(repo.trim(), branch.trim()) }
  }

  fun setDefaultCommitMessage(msg: String) {
    viewModelScope.launch { container.settingsRepository.setDefaultCommitMessage(msg) }
  }

  fun setUploadConcurrency(concurrency: Int) {
    viewModelScope.launch { container.settingsRepository.setUploadConcurrency(concurrency) }
  }

  fun setMaxExtractionSizeMb(maxMb: Int) {
    viewModelScope.launch { container.settingsRepository.setMaxExtractionSizeMb(maxMb) }
  }

  fun addExcludedFolder(folder: String) {
    val clean = folder.trim().trim('/')
    if (clean.isEmpty()) return
    viewModelScope.launch {
      val updated = uiState.value.settings.excludedFolders + clean
      container.settingsRepository.setExcludedFolders(updated)
    }
  }

  fun removeExcludedFolder(folder: String) {
    viewModelScope.launch {
      val updated = uiState.value.settings.excludedFolders - folder
      container.settingsRepository.setExcludedFolders(updated)
    }
  }

  fun clearTempCache(clearedMsg: String) {
    viewModelScope.launch {
      container.zipManager.clearTempCache()
      cacheBytes.value = container.zipManager.calculateCacheBytes()
      statusMessage.value = clearedMsg
    }
  }

  fun clearUploadHistory() {
    viewModelScope.launch {
      container.historyRepository.clearLocalHistory()
      statusMessage.value = "Local upload history cleared"
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SettingsViewModel(container) as T
    }
  }
}
