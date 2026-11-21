package com.example.domain.repository

import com.example.core.common.AppLanguage
import com.example.core.common.AppResult
import com.example.domain.model.AppSettings
import com.example.domain.model.GitHubBranch
import com.example.domain.model.GitHubCommit
import com.example.domain.model.GitHubRepo
import com.example.domain.model.GitHubUser
import com.example.domain.model.UploadHistoryModel
import com.example.domain.model.UploadTaskModel
import kotlinx.coroutines.flow.Flow

interface GitHubAuthRepository {
  val currentUser: Flow<GitHubUser?>
  suspend fun hasSavedToken(): Boolean
  suspend fun validateAndSaveToken(token: String): AppResult<GitHubUser>
  suspend fun refreshSavedSession(): AppResult<GitHubUser>
  suspend fun disconnect(clearCredentials: Boolean)
}

interface GitHubRepoRepository {
  val cachedRepos: Flow<List<GitHubRepo>>
  suspend fun fetchRepositories(query: String = ""): AppResult<List<GitHubRepo>>
  suspend fun createRepository(name: String, description: String?, isPrivate: Boolean): AppResult<GitHubRepo>
  suspend fun fetchBranches(owner: String, repo: String): AppResult<List<GitHubBranch>>
  suspend fun createBranch(owner: String, repo: String, newBranch: String, fromSha: String): AppResult<GitHubBranch>
  suspend fun fetchCommits(owner: String, repo: String, branch: String): AppResult<List<GitHubCommit>>
  suspend fun downloadRepoZipball(owner: String, repo: String, ref: String, destinationFilePath: String): AppResult<Int>
}

interface HistoryRepository {
  val historyItems: Flow<List<UploadHistoryModel>>
  val activeTasks: Flow<List<UploadTaskModel>>
  val totalCompletedUploads: Flow<Int>
  val totalUploadedBytes: Flow<Long>
  suspend fun saveHistory(item: UploadHistoryModel)
  suspend fun upsertTask(task: UploadTaskModel)
  suspend fun getTaskById(taskId: String): UploadTaskModel?
  suspend fun clearLocalHistory()
}

interface SettingsRepository {
  val settings: Flow<AppSettings>
  suspend fun setDarkMode(dark: Boolean)
  suspend fun setLanguage(language: AppLanguage)
  suspend fun setDefaultRepoAndBranch(repoFullName: String, branch: String)
  suspend fun setDefaultCommitMessage(message: String)
  suspend fun setLfsConfig(enabled: Boolean, thresholdMb: Int)
  suspend fun setUploadConcurrency(concurrency: Int)
  suspend fun setMaxExtractionSizeMb(maxMb: Int)
  suspend fun setExcludedFolders(folders: Set<String>)
}
