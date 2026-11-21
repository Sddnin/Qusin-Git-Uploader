package com.example.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppLanguage
import com.example.core.update.AppReleaseCheckResult
import com.example.core.update.AppReleaseInfo
import com.example.data.AppContainer
import com.example.domain.model.AppSettings
import com.example.domain.model.GitHubUser
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppUpdateUiState {
  data object Idle : AppUpdateUiState
  data object Checking : AppUpdateUiState
  data object NoReleasesPublished : AppUpdateUiState
  data class ReleaseFound(val release: AppReleaseInfo) : AppUpdateUiState
  data class Downloading(
    val release: AppReleaseInfo,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : AppUpdateUiState
  data class ReadyToInstall(
    val release: AppReleaseInfo,
    val apkFile: File,
  ) : AppUpdateUiState
  data class Error(val message: String) : AppUpdateUiState
}

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val user: GitHubUser? = null,
  val cacheBytes: Long = 0L,
  val statusMessage: String? = null,
  val updateState: AppUpdateUiState = AppUpdateUiState.Idle,
)

class SettingsViewModel(
  private val container: AppContainer,
) : ViewModel() {

  val currentVersionName: String = container.appUpdateManager.currentVersionName
  val currentVersionCode: Int = container.appUpdateManager.currentVersionCode

  private val cacheBytes = MutableStateFlow(container.zipManager.calculateCacheBytes())
  private val statusMessage = MutableStateFlow<String?>(null)
  private val updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)

  val uiState: StateFlow<SettingsUiState> = combine(
    container.settingsRepository.settings,
    container.gitHubAuthRepository.currentUser,
    cacheBytes,
    statusMessage,
    updateState,
  ) { settings, user, cache, msg, upd ->
    SettingsUiState(
      settings = settings,
      user = user,
      cacheBytes = cache,
      statusMessage = msg,
      updateState = upd,
    )
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

  fun notifyStatus(message: String) {
    statusMessage.value = message
  }

  fun checkForAppUpdate() {
    if (updateState.value is AppUpdateUiState.Checking || updateState.value is AppUpdateUiState.Downloading) return
    viewModelScope.launch {
      updateState.value = AppUpdateUiState.Checking
      when (val result = container.appUpdateManager.checkLatestRelease()) {
        is AppReleaseCheckResult.Success -> {
          updateState.value = AppUpdateUiState.ReleaseFound(result.release)
        }
        AppReleaseCheckResult.NoReleasesPublished -> {
          updateState.value = AppUpdateUiState.NoReleasesPublished
        }
        is AppReleaseCheckResult.Failure -> {
          updateState.value = AppUpdateUiState.Error(result.message)
        }
      }
    }
  }

  fun downloadAndInstallApk(
    release: AppReleaseInfo,
    onNeedUnknownSourcesPermission: (Intent) -> Unit,
    onLaunchApkInstaller: (Intent) -> Unit,
  ) {
    val url = release.apkDownloadUrl ?: return
    val name = release.apkAssetName ?: "qusin-git-uploader-${release.tagName}.apk"
    viewModelScope.launch {
      try {
        updateState.value = AppUpdateUiState.Downloading(release, 0L, release.apkSizeBytes)
        val apkFile = container.appUpdateManager.downloadApk(
          downloadUrl = url,
          fileName = name,
          onProgress = { downloaded, total ->
            updateState.value = AppUpdateUiState.Downloading(
              release = release,
              downloadedBytes = downloaded,
              totalBytes = if (total > 0L) total else release.apkSizeBytes,
            )
          },
        )
        updateState.value = AppUpdateUiState.ReadyToInstall(release, apkFile)
        launchOrRequestInstall(
          apkFile = apkFile,
          onNeedUnknownSourcesPermission = onNeedUnknownSourcesPermission,
          onLaunchApkInstaller = onLaunchApkInstaller,
        )
      } catch (t: Throwable) {
        updateState.value = AppUpdateUiState.Error(t.message ?: "APK download failed")
      }
    }
  }

  fun installReadyApkIfPermitted(
    apkFile: File,
    onNeedUnknownSourcesPermission: (Intent) -> Unit,
    onLaunchApkInstaller: (Intent) -> Unit,
  ) {
    launchOrRequestInstall(
      apkFile = apkFile,
      onNeedUnknownSourcesPermission = onNeedUnknownSourcesPermission,
      onLaunchApkInstaller = onLaunchApkInstaller,
    )
  }

  fun onReturnedFromInstallPermissionSetting(
    onLaunchApkInstaller: (Intent) -> Unit,
  ) {
    val ready = updateState.value as? AppUpdateUiState.ReadyToInstall ?: return
    if (container.appUpdateManager.canInstallPackages()) {
      onLaunchApkInstaller(container.appUpdateManager.createInstallApkIntent(ready.apkFile))
    }
  }

  private fun launchOrRequestInstall(
    apkFile: File,
    onNeedUnknownSourcesPermission: (Intent) -> Unit,
    onLaunchApkInstaller: (Intent) -> Unit,
  ) {
    if (container.appUpdateManager.canInstallPackages()) {
      onLaunchApkInstaller(container.appUpdateManager.createInstallApkIntent(apkFile))
    } else {
      onNeedUnknownSourcesPermission(container.appUpdateManager.createManageUnknownSourcesIntent())
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SettingsViewModel(container) as T
    }
  }
}
