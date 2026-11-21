package com.example.data

import com.example.core.common.AppLanguage
import com.example.core.storage.SettingsDataStore
import com.example.domain.model.AppSettings
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
  private val dataStore: SettingsDataStore,
) : SettingsRepository {
  override val settings: Flow<AppSettings> = dataStore.settingsFlow

  override suspend fun setDarkMode(dark: Boolean) = dataStore.setDarkMode(dark)

  override suspend fun setLanguage(language: AppLanguage) = dataStore.setLanguage(language)

  override suspend fun setDefaultRepoAndBranch(repoFullName: String, branch: String) =
    dataStore.setDefaultRepoAndBranch(repoFullName, branch)

  override suspend fun setDefaultCommitMessage(message: String) =
    dataStore.setDefaultCommitMessage(message)

  override suspend fun setLfsConfig(enabled: Boolean, thresholdMb: Int) =
    dataStore.setLfsConfig(enabled, thresholdMb)

  override suspend fun setUploadConcurrency(concurrency: Int) =
    dataStore.setUploadConcurrency(concurrency)

  override suspend fun setMaxExtractionSizeMb(maxMb: Int) =
    dataStore.setMaxExtractionSizeMb(maxMb)

  override suspend fun setExcludedFolders(folders: Set<String>) =
    dataStore.setExcludedFolders(folders)
}
