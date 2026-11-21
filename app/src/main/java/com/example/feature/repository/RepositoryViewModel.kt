package com.example.feature.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.common.AppResult
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
  private val error = MutableStateFlow<AppError?>(null)
  private val infoBanner = MutableStateFlow<String?>(null)

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

  val uiState: StateFlow<RepositoryUiState> = combine(repoStatePart, statusPart, infoBanner) { r, s, banner ->
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
      error = s.error,
      infoBanner = banner,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = RepositoryUiState(),
  )

  init {
    refreshAll()
  }

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
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
