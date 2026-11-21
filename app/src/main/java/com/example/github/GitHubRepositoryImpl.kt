package com.example.github

import com.example.core.common.AppDispatchers
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.example.core.security.TokenStore
import com.example.domain.model.GitHubBranch
import com.example.domain.model.GitHubCommit
import com.example.domain.model.GitHubRepo
import com.example.domain.model.GitHubUser
import com.example.domain.repository.GitHubAuthRepository
import com.example.domain.repository.GitHubRepoRepository
import com.example.github.api.GitHubApiService
import com.example.github.dto.CreateGitRefRequestDto
import com.example.github.dto.CreateRepoRequestDto
import com.example.github.dto.GitHubRepoDto
import com.example.github.dto.GitHubUserDto
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class GitHubRepositoryImpl(
  private val api: GitHubApiService,
  private val tokenStore: TokenStore,
  private val dispatchers: AppDispatchers,
) : GitHubAuthRepository, GitHubRepoRepository {

  private val _currentUser = MutableStateFlow<GitHubUser?>(null)
  override val currentUser: Flow<GitHubUser?> = _currentUser.asStateFlow()

  private val _cachedRepos = MutableStateFlow<List<GitHubRepo>>(emptyList())
  override val cachedRepos: Flow<List<GitHubRepo>> = _cachedRepos.asStateFlow()

  override suspend fun hasSavedToken(): Boolean = withContext(dispatchers.io) {
    tokenStore.hasToken()
  }

  override suspend fun validateAndSaveToken(token: String): AppResult<GitHubUser> = withContext(dispatchers.io) {
    val cleanToken = token.trim()
    if (cleanToken.isEmpty()) {
      return@withContext AppResult.Failure(AppError.Http401("Empty token"))
    }
    try {
      val response = api.validateTokenWithExplicitHeader("Bearer $cleanToken")
      if (!response.isSuccessful) {
        val errBody = response.errorBody()?.string()
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), errBody))
      }
      val dto = response.body() ?: return@withContext AppResult.Failure(AppError.Unknown("Empty user response"))
      val scopesHeader = response.headers()["X-OAuth-Scopes"].orEmpty()
      val scopes = scopesHeader.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      val hasWrite = scopes.isEmpty() || scopes.any { it == "repo" || it == "public_repo" || it == "write:packages" }

      tokenStore.saveToken(cleanToken)
      val user = dto.toDomain(scopes, hasWrite)
      _currentUser.value = user
      AppResult.Success(user)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun refreshSavedSession(): AppResult<GitHubUser> = withContext(dispatchers.io) {
    if (!tokenStore.hasToken()) {
      _currentUser.value = null
      return@withContext AppResult.Failure(AppError.Http401("No saved token"))
    }
    try {
      val response = api.getAuthenticatedUser()
      if (!response.isSuccessful) {
        val errBody = response.errorBody()?.string()
        if (response.code() == 401) {
          _currentUser.value = null
        }
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), errBody))
      }
      val dto = response.body() ?: return@withContext AppResult.Failure(AppError.Unknown("Empty user response"))
      val scopesHeader = response.headers()["X-OAuth-Scopes"].orEmpty()
      val scopes = scopesHeader.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      val hasWrite = scopes.isEmpty() || scopes.any { it == "repo" || it == "public_repo" }
      val user = dto.toDomain(scopes, hasWrite)
      _currentUser.value = user
      AppResult.Success(user)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun disconnect(clearCredentials: Boolean) = withContext(dispatchers.io) {
    if (clearCredentials) {
      tokenStore.clearToken()
    }
    _currentUser.value = null
    _cachedRepos.value = emptyList()
  }

  override suspend fun fetchRepositories(query: String): AppResult<List<GitHubRepo>> = withContext(dispatchers.io) {
    try {
      val response = api.listUserRepos()
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val allRepos = response.body().orEmpty().map { it.toDomain() }
      _cachedRepos.value = allRepos
      val filtered = if (query.isBlank()) {
        allRepos
      } else {
        allRepos.filter {
          it.fullName.contains(query, ignoreCase = true) ||
            it.description.orEmpty().contains(query, ignoreCase = true)
        }
      }
      AppResult.Success(filtered)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun createRepository(
    name: String,
    description: String?,
    isPrivate: Boolean,
  ): AppResult<GitHubRepo> = withContext(dispatchers.io) {
    try {
      val response = api.createRepo(
        CreateRepoRequestDto(
          name = name.trim(),
          description = description?.trim()?.ifEmpty { null },
          isPrivate = isPrivate,
          autoInit = true,
        ),
      )
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val created = response.body()?.toDomain()
        ?: return@withContext AppResult.Failure(AppError.Unknown("Empty create repo response"))
      _cachedRepos.value = listOf(created) + _cachedRepos.value
      AppResult.Success(created)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun fetchBranches(owner: String, repo: String): AppResult<List<GitHubBranch>> = withContext(dispatchers.io) {
    try {
      val response = api.listBranches(owner, repo)
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val branches = response.body().orEmpty().map {
        GitHubBranch(
          name = it.name,
          commitSha = it.commit.sha,
          isProtected = it.isProtected,
        )
      }
      AppResult.Success(branches)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun createBranch(
    owner: String,
    repo: String,
    newBranch: String,
    fromSha: String,
  ): AppResult<GitHubBranch> = withContext(dispatchers.io) {
    try {
      val cleanName = newBranch.trim().removePrefix("refs/heads/")
      val response = api.createRef(
        owner = owner,
        repo = repo,
        body = CreateGitRefRequestDto(ref = "refs/heads/$cleanName", sha = fromSha),
      )
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val ref = response.body() ?: return@withContext AppResult.Failure(AppError.Unknown("Empty ref response"))
      AppResult.Success(GitHubBranch(name = cleanName, commitSha = ref.obj.sha, isProtected = false))
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun fetchCommits(
    owner: String,
    repo: String,
    branch: String,
  ): AppResult<List<GitHubCommit>> = withContext(dispatchers.io) {
    try {
      val response = api.listCommits(owner, repo, branch)
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val commits = response.body().orEmpty().map {
        GitHubCommit(
          sha = it.sha,
          message = it.commit.message,
          authorName = it.commit.author?.name ?: "Unknown",
          dateIso = it.commit.author?.date ?: "",
          htmlUrl = it.htmlUrl,
        )
      }
      AppResult.Success(commits)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  override suspend fun downloadRepoZipball(
    owner: String,
    repo: String,
    ref: String,
    destinationFilePath: String,
  ): AppResult<Int> = withContext(dispatchers.io) {
    try {
      val response = api.downloadZipball(owner, repo, ref)
      if (!response.isSuccessful) {
        return@withContext AppResult.Failure(AppError.fromHttpStatus(response.code(), response.errorBody()?.string()))
      }
      val body = response.body() ?: return@withContext AppResult.Failure(AppError.Unknown("Empty zipball stream"))
      val outFile = File(destinationFilePath)
      outFile.parentFile?.mkdirs()
      var bytesCopied = 0
      body.byteStream().use { input ->
        FileOutputStream(outFile).use { output ->
          val buf = ByteArray(32 * 1024)
          while (true) {
            val r = input.read(buf)
            if (r == -1) break
            output.write(buf, 0, r)
            bytesCopied += r
          }
        }
      }
      AppResult.Success(bytesCopied)
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  private fun GitHubUserDto.toDomain(scopes: List<String>, hasWrite: Boolean): GitHubUser = GitHubUser(
    login = login,
    name = name,
    avatarUrl = avatarUrl,
    htmlUrl = htmlUrl,
    publicRepos = publicRepos,
    privateRepos = totalPrivateRepos ?: ownedPrivateRepos ?: 0,
    scopes = scopes,
    hasWriteAccess = hasWrite,
  )

  private fun GitHubRepoDto.toDomain(): GitHubRepo = GitHubRepo(
    id = id,
    name = name,
    fullName = fullName,
    ownerLogin = owner.login,
    description = description,
    isPrivate = isPrivate,
    defaultBranch = defaultBranch,
    updatedAt = updatedAt,
    htmlUrl = htmlUrl,
    cloneUrl = cloneUrl,
    canPush = permissions?.push ?: permissions?.admin ?: true,
    sizeKb = size,
  )
}
