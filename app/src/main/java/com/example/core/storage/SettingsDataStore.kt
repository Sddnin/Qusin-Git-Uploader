package com.example.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.AppLanguage
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.qusinSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "qusin_settings")

class SettingsDataStore(context: Context) {
  private val dataStore = context.applicationContext.qusinSettingsDataStore

  val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
    val langCode = prefs[KEY_LANGUAGE] ?: AppLanguage.VI.code
    val lang = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.VI
    AppSettings(
      isDarkMode = prefs[KEY_DARK_MODE] ?: true,
      language = lang,
      defaultRepoFullName = prefs[KEY_DEFAULT_REPO] ?: "",
      defaultBranch = prefs[KEY_DEFAULT_BRANCH] ?: "main",
      defaultCommitMessage = prefs[KEY_DEFAULT_COMMIT_MSG] ?: "Update project via Qusin Git Uploader",
      lfsEnabled = prefs[KEY_LFS_ENABLED] ?: true,
      lfsThresholdMb = prefs[KEY_LFS_THRESHOLD_MB] ?: 50,
      uploadConcurrency = prefs[KEY_CONCURRENCY] ?: 2,
      maxExtractionSizeMb = prefs[KEY_MAX_EXTRACT_MB] ?: 500,
      excludedFolders = prefs[KEY_EXCLUDED_FOLDERS] ?: AppSettings.DEFAULT_EXCLUDED_FOLDERS,
    )
  }

  suspend fun setDarkMode(dark: Boolean) {
    dataStore.edit { it[KEY_DARK_MODE] = dark }
  }

  suspend fun setLanguage(language: AppLanguage) {
    dataStore.edit { it[KEY_LANGUAGE] = language.code }
  }

  suspend fun setDefaultRepoAndBranch(repoFullName: String, branch: String) {
    dataStore.edit {
      it[KEY_DEFAULT_REPO] = repoFullName
      it[KEY_DEFAULT_BRANCH] = branch
    }
  }

  suspend fun setDefaultCommitMessage(message: String) {
    dataStore.edit { it[KEY_DEFAULT_COMMIT_MSG] = message }
  }

  suspend fun setLfsConfig(enabled: Boolean, thresholdMb: Int) {
    dataStore.edit {
      it[KEY_LFS_ENABLED] = enabled
      it[KEY_LFS_THRESHOLD_MB] = thresholdMb.coerceIn(10, 100)
    }
  }

  suspend fun setUploadConcurrency(concurrency: Int) {
    dataStore.edit { it[KEY_CONCURRENCY] = concurrency.coerceIn(1, 4) }
  }

  suspend fun setMaxExtractionSizeMb(maxMb: Int) {
    dataStore.edit { it[KEY_MAX_EXTRACT_MB] = maxMb.coerceIn(100, 2048) }
  }

  suspend fun setExcludedFolders(folders: Set<String>) {
    dataStore.edit { it[KEY_EXCLUDED_FOLDERS] = folders }
  }

  companion object {
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_DEFAULT_REPO = stringPreferencesKey("default_repo")
    private val KEY_DEFAULT_BRANCH = stringPreferencesKey("default_branch")
    private val KEY_DEFAULT_COMMIT_MSG = stringPreferencesKey("default_commit_msg")
    private val KEY_LFS_ENABLED = booleanPreferencesKey("lfs_enabled")
    private val KEY_LFS_THRESHOLD_MB = intPreferencesKey("lfs_threshold_mb")
    private val KEY_CONCURRENCY = intPreferencesKey("upload_concurrency")
    private val KEY_MAX_EXTRACT_MB = intPreferencesKey("max_extract_mb")
    private val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
  }
}
