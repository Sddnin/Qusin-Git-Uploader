package com.example.feature.repository

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.example.core.security.GitHubOAuthManager
import com.example.data.AppContainer
import com.example.domain.model.GitHubBranch
import com.example.domain.model.GitHubCommit
import com.example.domain.model.GitHubRepo
import com.example.domain.model.GitHubUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RepositoryUiState(
  val user: GitHubUser? = null,
  val hasToken: Boolean = false,
  val repos: List<GitHubRepo> = emptyList(),
  val searchQuery: String = "",
  val selectedRepo: GitHubRepo? = null,
  val branches: List<GitHubBranch> = emptyList(),
  val selectedBranch: String = "main",
  val commits: List<GitHubCommit> = emptyList(),
  val isLoading: Boolean = false,
  val isLoadingBranches: Boolean = false,
  val isOAuthInProgress: Boolean = false,
  val oauthClientId: String = "",
  val oauthRedirectUri: String = GitHubOAuthManager.REDIRECT_URI,
  val error: AppError? = null,
  val infoBanner: String? = null,
)

class RepositoryViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedRepo = MutableStateFlow<GitHubRepo?>(null)
  private val branches = MutableStateFlow<List<GitHubBranch>>(emptyList())
  private val selectedBranch = MutableStateFlow("main")
  private val commits = MutableStateFlow<List<GitHubCommit>>(emptyList())
  private val isLoading = MutableStateFlow(false)
  private val isLoadingBranches = MutableStateFlow(false)
  private val isOAuthInProgress = MutableStateFlow(false)
  private val oauthClientId = MutableStateFlow(container.oauthManager.getConfiguredClientId())
  private val error = MutableStateFlow<AppError?>(null)
  private val infoBanner = MutableStateFlow<String?>(null)
  private var lastConsumedCallbackUri: String? = null

  private val repoStatePart = combine(
    container.gitHubAuthRepository.currentUser,
    container.gitHubRepoRepository.cachedRepos,
    searchQuery,
    selectedRepo,
    branches,
  ) { user, repos, query, selRepo, branchList ->
    RepoTuple(user, repos, query, selRepo, branchList)
  }

  private val statusPart = combine(
    selectedBranch,
    commits,
    isLoading,
    isLoadingBranches,
    error,
  ) { selBranch, commitList, loading, branchLoading, err ->
    StatusTuple(selBranch, commitList, loading, branchLoading, err)
  }

  private val oauthPart = combine(
    isOAuthInProgress,
    oauthClientId,
    infoBanner,
  ) { inProgress, clientId, banner ->
    Triple(inProgress, clientId, banner)
  }

  val uiState: StateFlow<RepositoryUiState> = combine(repoStatePart, statusPart, oauthPart) { r, s, oauth ->
    val filtered = if (r.query.isBlank()) {
      r.repos
    } else {
      r.repos.filter {
        it.fullName.contains(r.query, ignoreCase = true) ||
          it.description.orEmpty().contains(r.query, ignoreCase = true)
      }
    }
    RepositoryUiState(
      user = r.user,
      hasToken = r.user != null,
      repos = filtered,
      searchQuery = r.query,
      selectedRepo = r.selRepo,
      branches = r.branchList,
      selectedBranch = s.selectedBranch,
      commits = s.commits,
      isLoading = s.loading,
      isLoadingBranches = s.branchLoading,
      isOAuthInProgress = oauth.first,
      oauthClientId = oauth.second,
      error = s.error,
      infoBanner = oauth.third,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = RepositoryUiState(oauthClientId = container.oauthManager.getConfiguredClientId()),
  )

  init {
    refreshAll()
    observeOAuthCallbacks()
  }

  private fun observeOAuthCallbacks() {
    viewModelScope.launch {
      container.oauthCallbacks.collect { uri ->
        val raw = uri.toString()
        if (raw == lastConsumedCallbackUri) return@collect
        lastConsumedCallbackUri = raw

        isOAuthInProgress.value = false
        isLoading.value = true
        error.value = null
        infoBanner.value = null
        when (val res = container.gitHubAuthRepository.completeOAuthLogin(uri)) {
          is AppResult.Success -> {
            infoBanner.value = "Connected @${res.data.login} via GitHub OAuth"
            refreshRepositories()
          }
          is AppResult.Failure -> {
            error.value = res.error
          }
        }
        isLoading.value = false
      }
    }
  }

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun saveOAuthClientId(clientId: String) {
    container.oauthManager.saveCustomClientId(clientId)
    oauthClientId.value = container.oauthManager.getConfiguredClientId()
    error.value = null
    if (oauthClientId.value.isNotEmpty()) {
      infoBanner.value = "Saved OAuth Client ID"
    }
  }

  fun startBrowserOAuth(
    clientIdInput: String? = null,
    launchBrowserTab: (Uri) -> AppResult<Unit>,
  ) {
    if (!clientIdInput.isNullOrBlank()) {
      saveOAuthClientId(clientIdInput)
    }
    val effectiveId = oauthClientId.value.trim()
    error.value = null
    infoBanner.value = null
    when (val authRes = container.oauthManager.beginAuthorization(effectiveId)) {
      is AppResult.Failure -> {
        isOAuthInProgress.value = false
        error.value = authRes.error
      }
      is AppResult.Success -> {
        isOAuthInProgress.value = true
        when (val launchRes = launchBrowserTab(authRes.data)) {
          is AppResult.Success -> Unit
          is AppResult.Failure -> {
            isOAuthInProgress.value = false
            container.oauthManager.cancelPendingSession()
            error.value = launchRes.error
          }
        }
      }
    }
  }

  fun cancelPendingOAuth() {
    container.oauthManager.cancelPendingSession()
    isOAuthInProgress.value = false
  }

  fun notifyInfo(message: String) {
    infoBanner.value = message
  }

  fun connectWithToken(token: String) {
    viewModelScope.launch {
      isLoading.value = true
      error.value = null
      infoBanner.value = null
      when (val res = container.gitHubAuthRepository.validateAndSaveToken(token)) {
        is AppResult.Success -> {
          infoBanner.value = "Connected @${res.data.login}"
          refreshRepositories()
        }
        is AppResult.Failure -> {
          error.value = res.error
        }
      }
      isLoading.value = false
    }
  }

  fun disconnect(deleteCredentials: Boolean) {
    viewModelScope.launch {
      container.gitHubAuthRepository.disconnect(clearCredentials = deleteCredentials)
      selectedRepo.value = null
      branches.value = emptyList()
      commits.value = emptyList()
      error.value = null
      infoBanner.value = if (deleteCredentials) "Credentials wiped from Keystore" else "Disconnected session"
    }
  }

  fun refreshAll() {
    viewModelScope.launch {
      if (!container.gitHubAuthRepository.hasSavedToken()) return@launch
      isLoading.value = true
      error.value = null
      when (val res = container.gitHubAuthRepository.refreshSavedSession()) {
        is AppResult.Success -> refreshRepositories()
        is AppResult.Failure -> error.value = res.error
      }
      isLoading.value = false
    }
  }

  fun refreshRepositories() {
    viewModelScope.launch {
      isLoading.value = true
      error.value = null
      when (val res = container.gitHubRepoRepository.fetchRepositories()) {
        is AppResult.Success -> {
          if (selectedRepo.value == null && res.data.isNotEmpty()) {
            selectRepository(res.data.first())
          }
        }
        is AppResult.Failure -> error.value = res.error
      }
      isLoading.value = false
    }
  }

  fun selectRepository(repo: GitHubRepo) {
    selectedRepo.value = repo
    selectedBranch.value = repo.defaultBranch
    viewModelScope.launch {
      container.settingsRepository.setDefaultRepoAndBranch(repo.fullName, repo.defaultBranch)
      loadBranchesAndCommits(repo)
    }
  }

  fun selectBranch(branchName: String) {
    val repo = selectedRepo.value ?: return
    selectedBranch.value = branchName
    viewModelScope.launch {
      container.settingsRepository.setDefaultRepoAndBranch(repo.fullName, branchName)
      when (val commitRes = container.gitHubRepoRepository.fetchCommits(repo.ownerLogin, repo.name, branchName)) {
        is AppResult.Success -> commits.value = commitRes.data
        is AppResult.Failure -> commits.value = emptyList()
      }
    }
  }

  private suspend fun loadBranchesAndCommits(repo: GitHubRepo) {
    isLoadingBranches.value = true
    when (val bRes = container.gitHubRepoRepository.fetchBranches(repo.ownerLogin, repo.name)) {
      is AppResult.Success -> {
        branches.value = bRes.data
        val branch = bRes.data.firstOrNull { it.name == repo.defaultBranch }?.name
          ?: bRes.data.firstOrNull()?.name
          ?: repo.defaultBranch
        selectedBranch.value = branch
        when (val cRes = container.gitHubRepoRepository.fetchCommits(repo.ownerLogin, repo.name, branch)) {
          is AppResult.Success -> commits.value = cRes.data
          is AppResult.Failure -> commits.value = emptyList()
        }
      }
      is AppResult.Failure -> {
        branches.value = emptyList()
        commits.value = emptyList()
      }
    }
    isLoadingBranches.value = false
  }

  fun createRepository(name: String, description: String?, isPrivate: Boolean) {
    if (name.isBlank()) return
    viewModelScope.launch {
      isLoading.value = true
      error.value = null
      when (val res = container.gitHubRepoRepository.createRepository(name, description, isPrivate)) {
        is AppResult.Success -> {
          infoBanner.value = "Created ${res.data.fullName}"
          selectRepository(res.data)
        }
        is AppResult.Failure -> error.value = res.error
      }
      isLoading.value = false
    }
  }

  fun createBranch(newBranchName: String) {
    val repo = selectedRepo.value ?: return
    val baseSha = branches.value.firstOrNull { it.name == selectedBranch.value }?.commitSha
      ?: branches.value.firstOrNull()?.commitSha
      ?: return
    viewModelScope.launch {
      isLoadingBranches.value = true
      error.value = null
      when (val res = container.gitHubRepoRepository.createBranch(repo.ownerLogin, repo.name, newBranchName, baseSha)) {
        is AppResult.Success -> {
          infoBanner.value = "Created branch '${res.data.name}'"
          loadBranchesAndCommits(repo)
          selectBranch(res.data.name)
        }
        is AppResult.Failure -> error.value = res.error
      }
      isLoadingBranches.value = false
    }
  }

  fun cloneZipballSnapshot() {
    val repo = selectedRepo.value ?: return
    val branch = selectedBranch.value
    viewModelScope.launch {
      isLoading.value = true
      val dest = "${container.zipManager.let { "" }}${repo.name}_$branch.zip"
      // Store inside cacheDir safely via ZipManager's context dir or external cache
      val targetPath = java.io.File(
        System.getProperty("java.io.tmpdir") ?: "/tmp",
        "qusin_clone_${repo.name}_$branch.zip",
      ).absolutePath
      when (val res = container.gitHubRepoRepository.downloadRepoZipball(repo.ownerLogin, repo.name, branch, targetPath)) {
        is AppResult.Success -> {
          infoBanner.value = "Cloned ${repo.name}@$branch (${res.data} bytes) to local cache"
        }
        is AppResult.Failure -> error.value = res.error
      }
      isLoading.value = false
    }
  }

  private data class RepoTuple(
    val user: GitHubUser?,
    val repos: List<GitHubRepo>,
    val query: String,
    val selRepo: GitHubRepo?,
    val branchList: List<GitHubBranch>,
  )

  private data class StatusTuple(
    val selectedBranch: String,
    val commits: List<GitHubCommit>,
    val loading: Boolean,
    val branchLoading: Boolean,
    val error: AppError?,
  )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return RepositoryViewModel(container) as T
    }
  }
}
