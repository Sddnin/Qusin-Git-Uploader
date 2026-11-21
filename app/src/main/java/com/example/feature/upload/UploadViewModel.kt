package com.example.feature.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.example.core.common.FormatUtils
import com.example.core.security.PathSecurity
import com.example.data.AppContainer
import com.example.domain.model.AppSettings
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.GitHubBranch
import com.example.domain.model.GitHubRepo
import com.example.domain.model.LfsClassification
import com.example.domain.model.SelectedFileItem
import com.example.domain.model.UploadTaskModel
import com.example.domain.model.ZipMode
import com.example.gitlfs.GitLfsClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConflictPromptState(
  val expectedSha: String,
  val remoteHeadSha: String,
)

data class UploadUiState(
  val projectName: String = "My Project",
  val allFiles: List<SelectedFileItem> = emptyList(),
  val visibleFiles: List<SelectedFileItem> = emptyList(),
  val availableExtensions: List<String> = emptyList(),
  val activeExtensionFilter: String? = null,
  val excludedFolders: Set<String> = AppSettings.DEFAULT_EXCLUDED_FOLDERS,
  val pendingZipItem: SelectedFileItem? = null,
  val selectedZipMode: ZipMode? = null,
  val zipCommonRootFolder: String? = null,
  val stripZipRootFolder: Boolean = true,
  val zipSkippedCount: Int = 0,
  val zipDuplicateCount: Int = 0,
  val repos: List<GitHubRepo> = emptyList(),
  val selectedRepo: GitHubRepo? = null,
  val branches: List<GitHubBranch> = emptyList(),
  val selectedBranch: String = "main",
  val commitMessage: String = "Update project via Qusin Git Uploader",
  val checkRemoteSync: Boolean = true,
  val isScanning: Boolean = false,
  val error: AppError? = null,
  val conflictPrompt: ConflictPromptState? = null,
  val tasks: List<UploadTaskModel> = emptyList(),
) {
  val selectedCount: Int get() = allFiles.count { it.isSelected }
  val selectedTotalBytes: Long get() = allFiles.filter { it.isSelected }.sumOf { it.sizeBytes }
  val lfsCount: Int get() = allFiles.count { it.isSelected && it.useLfs }
}

class UploadViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val projectName = MutableStateFlow("My Project")
  private val rawFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
  private val extensionFilter = MutableStateFlow<String?>(null)
  private val excludedFoldersOverride = MutableStateFlow<Set<String>?>(null)
  private val pendingZipItem = MutableStateFlow<SelectedFileItem?>(null)
  private val selectedZipMode = MutableStateFlow<ZipMode?>(null)
  private val zipCommonRootFolder = MutableStateFlow<String?>(null)
  private val stripZipRootFolder = MutableStateFlow(true)
  private val zipMeta = MutableStateFlow(0 to 0)
  private val selectedRepo = MutableStateFlow<GitHubRepo?>(null)
  private val branches = MutableStateFlow<List<GitHubBranch>>(emptyList())
  private val selectedBranch = MutableStateFlow("main")
  private val commitMessage = MutableStateFlow("Update project via Qusin Git Uploader")
  private val checkRemoteSync = MutableStateFlow(true)
  private val isScanning = MutableStateFlow(false)
  private val error = MutableStateFlow<AppError?>(null)
  private val conflictPrompt = MutableStateFlow<ConflictPromptState?>(null)
  private var expectedBaseSha: String? = null

  private val sourceFlow = combine(
    projectName,
    rawFiles,
    extensionFilter,
    excludedFoldersOverride,
    container.settingsRepository.settings,
  ) { pName, files, ext, exOverride, settings ->
    SourceTuple(pName, files, ext, exOverride ?: settings.excludedFolders, settings)
  }

  private val targetFlow = combine(
    container.gitHubRepoRepository.cachedRepos,
    selectedRepo,
    branches,
    selectedBranch,
    commitMessage,
  ) { repos, repo, branchList, branch, msg ->
    TargetTuple(repos, repo, branchList, branch, msg)
  }

  private val zipRootFlow = combine(
    zipCommonRootFolder,
    stripZipRootFolder,
  ) { root, strip -> root to strip }

  private val metaFlow = combine(
    pendingZipItem,
    selectedZipMode,
    zipMeta,
    checkRemoteSync,
    isScanning,
  ) { zipItem, zipMode, zMeta, sync, scanning ->
    MetaTuple(zipItem, zipMode, zMeta.first, zMeta.second, sync, scanning)
  }

  val uiState: StateFlow<UploadUiState> = combine(
    sourceFlow,
    targetFlow,
    metaFlow,
    zipRootFlow,
    error,
    conflictPrompt,
    container.historyRepository.activeTasks,
  ) { flows ->
    val src = flows[0] as SourceTuple
    val tgt = flows[1] as TargetTuple
    val meta = flows[2] as MetaTuple
    @Suppress("UNCHECKED_CAST")
    val zipRootPair = flows[3] as Pair<String?, Boolean>
    val err = flows[4] as AppError?
    val conflict = flows[5] as ConflictPromptState?
    @Suppress("UNCHECKED_CAST")
    val taskList = flows[6] as List<UploadTaskModel>

    val (commonRoot, stripRoot) = zipRootPair
    val effectiveFiles = if (commonRoot != null && stripRoot) {
      src.files.mapNotNull { item ->
        val mapped = PathSecurity.applyRootStripAndPrefix(
          relativePath = item.relativePath,
          commonRootToStrip = commonRoot,
        ) ?: return@mapNotNull null
        item.copy(relativePath = mapped)
      }
    } else {
      src.files
    }

    val afterExclusions = effectiveFiles.filter { !PathSecurity.isExcludedPath(it.relativePath, src.excluded) }
    val extensions = afterExclusions.map { it.extension }.filter { it.isNotEmpty() }.distinct().sorted()
    val visible = if (src.extFilter == null) {
      afterExclusions
    } else {
      afterExclusions.filter { it.extension.equals(src.extFilter, ignoreCase = true) }
    }

    val effectiveRepo = tgt.repo ?: tgt.repos.firstOrNull { it.fullName == src.settings.defaultRepoFullName } ?: tgt.repos.firstOrNull()
    val effectiveBranch = if (tgt.repo != null) tgt.branch else effectiveRepo?.defaultBranch ?: src.settings.defaultBranch

    UploadUiState(
      projectName = src.projectName,
      allFiles = afterExclusions,
      visibleFiles = visible,
      availableExtensions = extensions,
      activeExtensionFilter = src.extFilter,
      excludedFolders = src.excluded,
      pendingZipItem = meta.zipItem,
      selectedZipMode = meta.zipMode,
      zipCommonRootFolder = commonRoot,
      stripZipRootFolder = stripRoot,
      zipSkippedCount = meta.zipSkipped,
      zipDuplicateCount = meta.zipDup,
      repos = tgt.repos,
      selectedRepo = effectiveRepo,
      branches = tgt.branches,
      selectedBranch = effectiveBranch,
      commitMessage = tgt.msg.ifBlank { src.settings.defaultCommitMessage },
      checkRemoteSync = meta.sync,
      isScanning = meta.scanning,
      error = err,
      conflictPrompt = conflict,
      tasks = taskList,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UploadUiState(),
  )

  init {
    viewModelScope.launch {
      val s = container.settingsRepository.settings.first()
      commitMessage.value = s.defaultCommitMessage
      val repos = container.gitHubRepoRepository.cachedRepos.first()
      val initialRepo = repos.firstOrNull { it.fullName == s.defaultRepoFullName } ?: repos.firstOrNull()
      if (initialRepo != null) {
        selectRepository(initialRepo)
      }
    }
  }

  fun onSingleOrMultiFilesPicked(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      isScanning.value = true
      error.value = null
      pendingZipItem.value = null
      selectedZipMode.value = null
      zipCommonRootFolder.value = null
      try {
        val settings = container.settingsRepository.settings.first()
        val scanned = withContext(container.dispatchers.io) {
          container.safFileScanner.scanSingleOrMultipleUris(uris, settings)
        }
        if (scanned.size == 1 && scanned.first().extension.equals("zip", ignoreCase = true)) {
          pendingZipItem.value = scanned.first()
          projectName.value = scanned.first().name.removeSuffix(".zip")
          rawFiles.value = emptyList()
        } else {
          rawFiles.value = scanned
          projectName.value = if (scanned.size == 1) scanned.first().name else "Selected Files (${scanned.size})"
        }
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanning.value = false
    }
  }

  fun onZipPicked(uri: Uri) {
    viewModelScope.launch {
      isScanning.value = true
      error.value = null
      zipCommonRootFolder.value = null
      try {
        val settings = container.settingsRepository.settings.first()
        val scanned = withContext(container.dispatchers.io) {
          container.safFileScanner.scanSingleOrMultipleUris(listOf(uri), settings)
        }
        val zipItem = scanned.firstOrNull()
        if (zipItem != null) {
          pendingZipItem.value = zipItem
          selectedZipMode.value = null
          projectName.value = zipItem.name.removeSuffix(".zip")
          rawFiles.value = emptyList()
        }
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanning.value = false
    }
  }

  fun onFolderPicked(treeUri: Uri) {
    viewModelScope.launch {
      isScanning.value = true
      error.value = null
      pendingZipItem.value = null
      selectedZipMode.value = null
      zipCommonRootFolder.value = null
      try {
        val settings = container.settingsRepository.settings.first()
        val effectiveSettings = settings.copy(
          excludedFolders = excludedFoldersOverride.value ?: settings.excludedFolders,
        )
        val (folderName, scanned) = withContext(container.dispatchers.io) {
          container.safFileScanner.scanTreeUri(treeUri, effectiveSettings)
        }
        projectName.value = folderName
        rawFiles.value = scanned
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanning.value = false
    }
  }

  fun chooseZipMode(mode: ZipMode) {
    val zipItem = pendingZipItem.value ?: return
    selectedZipMode.value = mode
    if (mode == ZipMode.RAW_ZIP) {
      zipCommonRootFolder.value = null
      rawFiles.value = listOf(zipItem)
      zipMeta.value = 0 to 0
      return
    }
    viewModelScope.launch {
      isScanning.value = true
      error.value = null
      try {
        val settings = container.settingsRepository.settings.first()
        val effectiveSettings = settings.copy(
          excludedFolders = excludedFoldersOverride.value ?: settings.excludedFolders,
        )
        val uri = zipItem.sourceUri ?: return@launch
        val result = withContext(container.dispatchers.io) {
          container.zipManager.inspectAndExtractSafely(
            zipUri = uri,
            zipName = zipItem.name,
            zipSizeBytes = zipItem.sizeBytes,
            settings = effectiveSettings,
          )
        }
        zipCommonRootFolder.value = result.commonRootFolder
        stripZipRootFolder.value = true
        rawFiles.value = result.entries
        zipMeta.value = result.skippedExcludedCount to result.duplicateCount
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanning.value = false
    }
  }

  fun toggleStripZipRootFolder(strip: Boolean) {
    stripZipRootFolder.value = strip
  }

  fun toggleFileSelected(fileId: String) {
    rawFiles.value = rawFiles.value.map {
      if (it.id == fileId) it.copy(isSelected = !it.isSelected) else it
    }
  }

  fun toggleFileLfs(fileId: String) {
    rawFiles.value = rawFiles.value.map { item ->
      if (item.id == fileId && item.lfsClassification != LfsClassification.LFS_REQUIRED) {
        item.copy(useLfs = !item.useLfs)
      } else {
        item
      }
    }
  }

  fun setExtensionFilter(ext: String?) {
    extensionFilter.value = ext
  }

  fun addExcludedFolder(folder: String) {
    val clean = folder.trim().trim('/')
    if (clean.isEmpty()) return
    viewModelScope.launch {
      val current = excludedFoldersOverride.value ?: container.settingsRepository.settings.first().excludedFolders
      excludedFoldersOverride.value = current + clean
    }
  }

  fun removeExcludedFolder(folder: String) {
    viewModelScope.launch {
      val current = excludedFoldersOverride.value ?: container.settingsRepository.settings.first().excludedFolders
      excludedFoldersOverride.value = current - folder
    }
  }

  fun selectRepository(repo: GitHubRepo) {
    selectedRepo.value = repo
    selectedBranch.value = repo.defaultBranch
    expectedBaseSha = null
    viewModelScope.launch {
      when (val bRes = container.gitHubRepoRepository.fetchBranches(repo.ownerLogin, repo.name)) {
        is AppResult.Success -> {
          branches.value = bRes.data
          val match = bRes.data.firstOrNull { it.name == repo.defaultBranch } ?: bRes.data.firstOrNull()
          if (match != null) {
            selectedBranch.value = match.name
            expectedBaseSha = match.commitSha
          }
        }
        is AppResult.Failure -> branches.value = emptyList()
      }
    }
  }

  fun selectBranch(branch: String) {
    selectedBranch.value = branch
    expectedBaseSha = branches.value.firstOrNull { it.name == branch }?.commitSha
  }

  fun updateCommitMessage(msg: String) {
    commitMessage.value = msg
  }

  fun toggleCheckRemoteSync(enabled: Boolean) {
    checkRemoteSync.value = enabled
  }

  fun startUpload(conflictStrategy: ConflictStrategy = ConflictStrategy.ABORT) {
    val current = uiState.value
    val repo = current.selectedRepo ?: run {
      error.value = AppError.Http404("Please connect GitHub and select a target repository")
      return
    }
    val chosenFiles = current.allFiles.filter { it.isSelected }
    if (chosenFiles.isEmpty()) {
      error.value = AppError.Http400("No files selected for upload")
      return
    }

    viewModelScope.launch {
      error.value = null
      conflictPrompt.value = null
      try {
        if (current.checkRemoteSync && conflictStrategy == ConflictStrategy.ABORT && expectedBaseSha != null) {
          val snap = withContext(container.dispatchers.io) {
            container.gitEngine.inspectRemoteBranch(repo.ownerLogin, repo.name, current.selectedBranch)
          }
          if (snap.branchExists && snap.headCommitSha != null && snap.headCommitSha != expectedBaseSha) {
            conflictPrompt.value = ConflictPromptState(
              expectedSha = expectedBaseSha!!,
              remoteHeadSha = snap.headCommitSha,
            )
            return@launch
          }
        }

        container.uploadCoordinator.enqueueUpload(
          projectName = current.projectName,
          owner = repo.ownerLogin,
          repoName = repo.name,
          branch = current.selectedBranch,
          commitMessage = current.commitMessage,
          expectedBaseSha = expectedBaseSha,
          conflictStrategy = conflictStrategy,
          selectedFiles = chosenFiles,
        )
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
    }
  }

  fun dismissConflictPrompt() {
    conflictPrompt.value = null
  }

  fun pauseTask(taskId: String) {
    container.uploadCoordinator.pauseOrCancel(taskId)
  }

  fun resumeOrRetryTask(taskId: String) {
    viewModelScope.launch {
      container.uploadCoordinator.retryOrResume(taskId)
    }
  }

  fun cancelAndRemoveTask(taskId: String) {
    viewModelScope.launch {
      container.uploadCoordinator.removeTask(taskId)
    }
  }

  private data class SourceTuple(
    val projectName: String,
    val files: List<SelectedFileItem>,
    val extFilter: String?,
    val excluded: Set<String>,
    val settings: AppSettings,
  )

  private data class TargetTuple(
    val repos: List<GitHubRepo>,
    val repo: GitHubRepo?,
    val branches: List<GitHubBranch>,
    val branch: String,
    val msg: String,
  )

  private data class MetaTuple(
    val zipItem: SelectedFileItem?,
    val zipMode: ZipMode?,
    val zipSkipped: Int,
    val zipDup: Int,
    val sync: Boolean,
    val scanning: Boolean,
  )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return UploadViewModel(container) as T
    }
  }
}
