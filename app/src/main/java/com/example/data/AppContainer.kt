package com.example.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.example.core.common.AppDispatchers
import com.example.core.common.DefaultAppDispatchers
import com.example.core.network.NetworkClientProvider
import com.example.core.security.GitHubOAuthManager
import com.example.core.security.KeystoreTokenManager
import com.example.core.security.TokenStore
import com.example.core.storage.AppDatabase
import com.example.core.storage.SettingsDataStore
import com.example.core.update.AppUpdateManager
import com.example.domain.repository.GitHubAuthRepository
import com.example.domain.repository.GitHubRepoRepository
import com.example.domain.repository.HistoryRepository
import com.example.domain.repository.SettingsRepository
import com.example.git.GitEngine
import com.example.git.ProjectDiffEngine
import com.example.github.GitHubRepositoryImpl
import com.example.github.api.GitHubApiService
import com.example.gitlfs.GitLfsEngine
import com.example.upload.SafFileScanner
import com.example.upload.UploadCoordinator
import com.example.upload.ZipManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext

  val dispatchers: AppDispatchers = DefaultAppDispatchers
  val tokenStore: TokenStore = KeystoreTokenManager(appContext)
  val networkProvider = NetworkClientProvider(tokenStore)
  val gitHubApiService: GitHubApiService = networkProvider.retrofit.create(GitHubApiService::class.java)
  val oauthManager = GitHubOAuthManager(
    context = appContext,
    httpClient = networkProvider.lfsTransferClient,
    moshi = networkProvider.moshi,
  )

  private val _oauthCallbacks = MutableSharedFlow<Uri>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val oauthCallbacks: SharedFlow<Uri> = _oauthCallbacks.asSharedFlow()

  fun dispatchOAuthCallback(uri: Uri?) {
    if (GitHubOAuthManager.isOAuthCallbackUri(uri) && uri != null) {
      _oauthCallbacks.tryEmit(uri)
    }
  }

  val database: AppDatabase = Room.databaseBuilder(
    appContext,
    AppDatabase::class.java,
    "qusin_git_uploader.db",
  ).addMigrations(AppDatabase.MIGRATION_1_2).build()

  val settingsDataStore = SettingsDataStore(appContext)
  val settingsRepository: SettingsRepository = SettingsRepositoryImpl(settingsDataStore)
  val historyRepository: HistoryRepository = HistoryRepositoryImpl(
    historyDao = database.uploadHistoryDao(),
    taskDao = database.uploadTaskDao(),
  )

  private val gitHubRepoImpl = GitHubRepositoryImpl(
    api = gitHubApiService,
    tokenStore = tokenStore,
    dispatchers = dispatchers,
    oauthManager = oauthManager,
  )
  val gitHubAuthRepository: GitHubAuthRepository = gitHubRepoImpl
  val gitHubRepoRepository: GitHubRepoRepository = gitHubRepoImpl

  val gitLfsEngine = GitLfsEngine(
    lfsHttpClient = networkProvider.lfsTransferClient,
    moshi = networkProvider.moshi,
    tokenStore = tokenStore,
  )
  val gitEngine = GitEngine(
    api = gitHubApiService,
    lfsEngine = gitLfsEngine,
    moshi = networkProvider.moshi,
  )
  val projectDiffEngine = ProjectDiffEngine(
    api = gitHubApiService,
    gitEngine = gitEngine,
    lfsEngine = gitLfsEngine,
  )

  val safFileScanner = SafFileScanner(appContext)
  val zipManager = ZipManager(appContext)
  val uploadCoordinator = UploadCoordinator(
    context = appContext,
    taskDao = database.uploadTaskDao(),
  )
  val appUpdateManager = AppUpdateManager(
    context = appContext,
    api = gitHubApiService,
    downloadClient = networkProvider.lfsTransferClient,
  )
}
