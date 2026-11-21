package com.example.feature.update

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.example.core.security.PathSecurity
import com.example.data.AppContainer
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.DiffFileItem
import com.example.domain.model.DiffStatus
import com.example.domain.model.GitHubBranch
import com.example.domain.model.GitHubRepo
import com.example.domain.model.ProjectDiffPlan
import com.example.domain.model.SelectedFileItem
import com.example.domain.model.UpdateMode
import com.example.domain.model.UploadTaskModel
import com.example.feature.upload.ConflictPromptState
import com.example.upload.openStreamForItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateProjectUiState(
  val repos: List<GitHubRepo> = emptyList(),
  val selectedRepo: GitHubRepo? = null,
  val branches: List<GitHubBranch> = emptyList(),
  val selectedBranch: String = "main",
  val targetPrefix: String = "",
  val sourceLabel: String = "",
  val zipCommonRootFolder: String? = null,
  val stripZipRootFolder: Boolean = true,
  val stagedLocalFiles: List<SelectedFileItem> = emptyList(),
  val updateMode: UpdateMode = UpdateMode.UPDATE_CHANGES,
  val diffPlan: ProjectDiffPlan? = null,
  val showUnchanged: Boolean = false,
  val confirmDestructiveAction: Boolean = false,
  val commitMessage: String = "Update project via Qusin Git Uploader",
  val isScanningOrDiffing: Boolean = false,
  val progressStatus: String = "",
  val error: AppError? = null,
  val conflictPrompt: ConflictPromptState? = null,
  val tasks: List<UploadTaskModel> = emptyList(),
)

class UpdateProjectViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val selectedRepo = MutableStateFlow<GitHubRepo?>(null)
  private val branches = MutableStateFlow<List<GitHubBranch>>(emptyList())
  private val selectedBranch = MutableStateFlow("main")
  private val targetPrefix = MutableStateFlow("")
  private val sourceLabel = MutableStateFlow("")
  private val zipCommonRootFolder = MutableStateFlow<String?>(null)
  private val stripZipRootFolder = MutableStateFlow(true)
  private val stagedLocalFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
  private val updateMode = MutableStateFlow(UpdateMode.UPDATE_CHANGES)
  private val diffPlan = MutableStateFlow<ProjectDiffPlan?>(null)
  private val showUnchanged = MutableStateFlow(false)
  private val confirmDestructiveAction = MutableStateFlow(false)
  private val commitMessage = MutableStateFlow("Update project via Qusin Git Uploader")
  private val isScanningOrDiffing = MutableStateFlow(false)
  private val progressStatus = MutableStateFlow("")
  private val error = MutableStateFlow<AppError?>(null)
  private val conflictPrompt = MutableStateFlow<ConflictPromptState?>(null)

  private val targetFlow = combine(
    container.gitHubRepoRepository.cachedRepos,
    selectedRepo,
    branches,
    selectedBranch,
    targetPrefix,
  ) { repos, repo, branchList, branch, prefix ->
    TargetGroup(repos, repo, branchList, branch, prefix)
  }

  private val sourceFlow = combine(
    sourceLabel,
    stagedLocalFiles,
    updateMode,
    diffPlan,
    showUnchanged,
  ) { label, files, mode, plan, showUnch ->
    SourceGroup(label, files, mode, plan, showUnch)
  }

  private val metaFlow = combine(
    confirmDestructiveAction,
    commitMessage,
    isScanningOrDiffing,
    progressStatus,
    error,
  ) { confirmDel, msg, busy, status, err ->
    MetaGroup(confirmDel, msg, busy, status, err)
  }

  private val zipRootFlow = combine(
    zipCommonRootFolder,
    stripZipRootFolder,
  ) { root, strip -> root to strip }

  val uiState: StateFlow<UpdateProjectUiState> = combine(
    targetFlow,
    sourceFlow,
    metaFlow,
    zipRootFlow,
    conflictPrompt,
    container.historyRepository.activeTasks,
    container.settingsRepository.settings,
  ) { arr ->
    val t = arr[0] as TargetGroup
    val s = arr[1] as SourceGroup
    val m = arr[2] as MetaGroup
    @Suppress("UNCHECKED_CAST")
    val zipRootPair = arr[3] as Pair<String?, Boolean>
    val conflict = arr[4] as ConflictPromptState?
    @Suppress("UNCHECKED_CAST")
    val tasks = arr[5] as List<UploadTaskModel>
    val settings = arr[6] as com.example.domain.model.AppSettings

    val effRepo = t.repo ?: t.repos.firstOrNull { it.fullName == settings.defaultRepoFullName } ?: t.repos.firstOrNull()
    val effBranch = if (t.repo != null) t.branch else effRepo?.defaultBranch ?: settings.defaultBranch
    val (commonRoot, stripRoot) = zipRootPair
    val effectiveLocalFiles = if (commonRoot != null && stripRoot) {
      s.files.mapNotNull { item ->
        val mapped = PathSecurity.applyRootStripAndPrefix(
          relativePath = item.relativePath,
          commonRootToStrip = commonRoot,
        ) ?: return@mapNotNull null
        item.copy(relativePath = mapped)
      }
    } else {
      s.files
    }

    UpdateProjectUiState(
      repos = t.repos,
      selectedRepo = effRepo,
      branches = t.branches,
      selectedBranch = effBranch,
      targetPrefix = t.prefix,
      sourceLabel = s.label,
      zipCommonRootFolder = commonRoot,
      stripZipRootFolder = stripRoot,
      stagedLocalFiles = effectiveLocalFiles,
      updateMode = s.mode,
      diffPlan = s.plan,
      showUnchanged = s.showUnchanged,
      confirmDestructiveAction = m.confirmDel,
      commitMessage = m.commitMsg,
      isScanningOrDiffing = m.busy,
      progressStatus = m.status,
      error = m.err,
      conflictPrompt = conflict,
      tasks = tasks,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UpdateProjectUiState(),
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

  fun selectRepository(repo: GitHubRepo) {
    selectedRepo.value = repo
    selectedBranch.value = repo.defaultBranch
    diffPlan.value = null
    viewModelScope.launch {
      when (val bRes = container.gitHubRepoRepository.fetchBranches(repo.ownerLogin, repo.name)) {
        is AppResult.Success -> {
          branches.value = bRes.data
          val match = bRes.data.firstOrNull { it.name == repo.defaultBranch } ?: bRes.data.firstOrNull()
          if (match != null) selectedBranch.value = match.name
        }
        is AppResult.Failure -> branches.value = emptyList()
      }
    }
  }

  fun selectBranch(branch: String) {
    selectedBranch.value = branch
    diffPlan.value = null
  }

  fun updateTargetPrefix(prefix: String) {
    targetPrefix.value = prefix
    diffPlan.value = null
  }

  fun updateCommitMessage(msg: String) {
    commitMessage.value = msg
  }

  fun setUpdateMode(mode: UpdateMode) {
    updateMode.value = mode
    confirmDestructiveAction.value = false
    val currentPlan = diffPlan.value ?: return
    val updatedItems = currentPlan.items.map { item ->
      val defaultSelect = when (mode) {
        UpdateMode.UPDATE_CHANGES -> item.status == DiffStatus.ADDED || item.status == DiffStatus.MODIFIED
        UpdateMode.SYNC_PROJECT, UpdateMode.OVERWRITE_PROJECT ->
          item.status == DiffStatus.ADDED || item.status == DiffStatus.MODIFIED || item.status == DiffStatus.DELETED
        UpdateMode.UPDATE_SELECTED -> item.status == DiffStatus.ADDED || item.status == DiffStatus.MODIFIED
      }
      item.copy(selectedForApply = defaultSelect)
    }
    diffPlan.value = currentPlan.copy(items = updatedItems)
  }

  fun toggleShowUnchanged(show: Boolean) {
    showUnchanged.value = show
  }

  fun setConfirmDestructive(confirmed: Boolean) {
    confirmDestructiveAction.value = confirmed
  }

  fun toggleDiffItemSelection(relativePath: String) {
    val plan = diffPlan.value ?: return
    val updated = plan.items.map { item ->
      if (item.relativePath == relativePath && item.status != DiffStatus.UNCHANGED) {
        item.copy(selectedForApply = !item.selectedForApply)
      } else {
        item
      }
    }
    diffPlan.value = plan.copy(items = updated)
  }

  fun onNewZipPicked(uri: Uri) {
    viewModelScope.launch {
      isScanningOrDiffing.value = true
      error.value = null
      diffPlan.value = null
      zipCommonRootFolder.value = null
      progressStatus.value = "Extracting ZIP safely into temp workspace..."
      try {
        val settings = container.settingsRepository.settings.first()
        val zipItems = withContext(container.dispatchers.io) {
          container.safFileScanner.scanSingleOrMultipleUris(listOf(uri), settings)
        }
        val zipItem = zipItems.firstOrNull() ?: return@launch
        val result = withContext(container.dispatchers.io) {
          container.zipManager.inspectAndExtractSafely(
            zipUri = uri,
            zipName = zipItem.name,
            zipSizeBytes = zipItem.sizeBytes,
            settings = settings,
          )
        }
        zipCommonRootFolder.value = result.commonRootFolder
        stripZipRootFolder.value = true
        sourceLabel.value = "${zipItem.name} (${result.entries.size} files)"
        stagedLocalFiles.value = result.entries
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanningOrDiffing.value = false
      progressStatus.value = ""
    }
  }

  fun onNewFolderPicked(treeUri: Uri) {
    viewModelScope.launch {
      isScanningOrDiffing.value = true
      error.value = null
      diffPlan.value = null
      zipCommonRootFolder.value = null
      progressStatus.value = "Scanning new project folder..."
      try {
        val settings = container.settingsRepository.settings.first()
        val (folderName, entries) = withContext(container.dispatchers.io) {
          container.safFileScanner.scanTreeUri(treeUri, settings)
        }
        sourceLabel.value = "$folderName (${entries.size} files)"
        stagedLocalFiles.value = entries
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanningOrDiffing.value = false
      progressStatus.value = ""
    }
  }

  fun toggleStripZipRootFolder(strip: Boolean) {
    stripZipRootFolder.value = strip
    diffPlan.value = null
  }

  fun analyzeDiff() {
    val current = uiState.value
    val repo = current.selectedRepo ?: run {
      error.value = AppError.Http404("Please connect GitHub and select a target repository")
      return
    }
    if (current.stagedLocalFiles.isEmpty()) {
      error.value = AppError.Http400("Please select a new version ZIP or folder first")
      return
    }

    viewModelScope.launch {
      isScanningOrDiffing.value = true
      error.value = null
      diffPlan.value = null
      confirmDestructiveAction.value = false
      try {
        val plan = withContext(container.dispatchers.io) {
          container.projectDiffEngine.analyzeDiffAgainstRemote(
            owner = repo.ownerLogin,
            repo = repo.name,
            branch = current.selectedBranch,
            targetPrefix = current.targetPrefix,
            localItems = current.stagedLocalFiles,
            openStreamFor = { item -> openStreamForItem(container.appContext, item) },
            onProgress = { status -> progressStatus.value = status },
          )
        }
        diffPlan.value = plan
        // Re-apply current update mode default selection
        setUpdateMode(current.updateMode)
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
      isScanningOrDiffing.value = false
      progressStatus.value = ""
    }
  }

  fun executeUpdate(conflictStrategy: ConflictStrategy = ConflictStrategy.ABORT) {
    val current = uiState.value
    val repo = current.selectedRepo ?: return
    val plan = current.diffPlan ?: return

    val activeItems: List<DiffFileItem> = when (current.updateMode) {
      UpdateMode.UPDATE_CHANGES -> plan.items.filter {
        it.status == DiffStatus.ADDED || it.status == DiffStatus.MODIFIED
      }
      UpdateMode.SYNC_PROJECT, UpdateMode.OVERWRITE_PROJECT -> plan.items.filter {
        it.status == DiffStatus.ADDED || it.status == DiffStatus.MODIFIED || it.status == DiffStatus.DELETED
      }
      UpdateMode.UPDATE_SELECTED -> plan.items.filter {
        it.selectedForApply && it.status != DiffStatus.UNCHANGED
      }
    }

    val filesToUpload = activeItems.mapNotNull { it.sourceItem }
    val filesToDelete = activeItems.filter { it.status == DiffStatus.DELETED }.map { it.relativePath }

    if (filesToUpload.isEmpty() && filesToDelete.isEmpty()) {
      error.value = AppError.Http400("No changes selected to commit")
      return
    }

    viewModelScope.launch {
      error.value = null
      conflictPrompt.value = null
      try {
        if (conflictStrategy == ConflictStrategy.ABORT && plan.baseCommitSha != null) {
          val liveSnap = withContext(container.dispatchers.io) {
            container.gitEngine.inspectRemoteBranch(repo.ownerLogin, repo.name, current.selectedBranch)
          }
          if (liveSnap.branchExists && liveSnap.headCommitSha != null && liveSnap.headCommitSha != plan.baseCommitSha) {
            conflictPrompt.value = ConflictPromptState(
              expectedSha = plan.baseCommitSha,
              remoteHeadSha = liveSnap.headCommitSha,
            )
            return@launch
          }
        }

        container.uploadCoordinator.enqueueUpload(
          projectName = current.sourceLabel.ifBlank { repo.name },
          owner = repo.ownerLogin,
          repoName = repo.name,
          branch = current.selectedBranch,
          commitMessage = current.commitMessage,
          expectedBaseSha = plan.baseCommitSha,
          conflictStrategy = conflictStrategy,
          selectedFiles = filesToUpload,
          filesToDelete = filesToDelete,
          updateMode = current.updateMode.name,
          addedCount = activeItems.count { it.status == DiffStatus.ADDED },
          modifiedCount = activeItems.count { it.status == DiffStatus.MODIFIED },
        )
      } catch (t: Throwable) {
        error.value = AppError.fromThrowable(t)
      }
    }
  }

  fun dismissConflictPrompt() {
    conflictPrompt.value = null
  }

  fun pauseTask(taskId: String) = container.uploadCoordinator.pauseOrCancel(taskId)

  fun resumeOrRetryTask(taskId: String) {
    viewModelScope.launch { container.uploadCoordinator.retryOrResume(taskId) }
  }

  fun cancelTask(taskId: String) {
    viewModelScope.launch { container.uploadCoordinator.removeTask(taskId) }
  }

  private data class TargetGroup(
    val repos: List<GitHubRepo>,
    val repo: GitHubRepo?,
    val branches: List<GitHubBranch>,
    val branch: String,
    val prefix: String,
  )

  private data class SourceGroup(
    val label: String,
    val files: List<SelectedFileItem>,
    val mode: UpdateMode,
    val plan: ProjectDiffPlan?,
    val showUnchanged: Boolean,
  )

  private data class MetaGroup(
    val confirmDel: Boolean,
    val commitMsg: String,
    val busy: Boolean,
    val status: String,
    val err: AppError?,
  )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return UpdateProjectViewModel(container) as T
    }
  }
}
