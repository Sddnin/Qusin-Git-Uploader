package com.example.core.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.common.l10n.DeStrings
import com.example.core.common.l10n.EnStrings
import com.example.core.common.l10n.EsStrings
import com.example.core.common.l10n.FrStrings
import com.example.core.common.l10n.IdStrings
import com.example.core.common.l10n.JaStrings
import com.example.core.common.l10n.KoStrings
import com.example.core.common.l10n.PtStrings
import com.example.core.common.l10n.RuStrings
import com.example.core.common.l10n.ViStrings
import com.example.core.common.l10n.ZhStrings
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
  SYSTEM("system", "System Auto"),
  VI("vi", "Tiếng Việt"),
  EN("en", "English"),
  ZH("zh", "中文 (简体)"),
  JA("ja", "日本語"),
  KO("ko", "한국어"),
  ES("es", "Español"),
  FR("fr", "Français"),
  DE("de", "Deutsch"),
  PT("pt", "Português"),
  RU("ru", "Русский"),
  ID("id", "Bahasa Indonesia"),
}

fun resolveAppLanguage(
  selected: AppLanguage,
  systemLocale: Locale = Locale.getDefault(),
): AppLanguage {
  if (selected != AppLanguage.SYSTEM) return selected
  val langCode = systemLocale.language.lowercase(Locale.ROOT)
  return when {
    langCode == "vi" -> AppLanguage.VI
    langCode == "en" -> AppLanguage.EN
    langCode == "zh" -> AppLanguage.ZH
    langCode == "ja" -> AppLanguage.JA
    langCode == "ko" -> AppLanguage.KO
    langCode == "es" -> AppLanguage.ES
    langCode == "fr" -> AppLanguage.FR
    langCode == "de" -> AppLanguage.DE
    langCode == "pt" -> AppLanguage.PT
    langCode == "ru" -> AppLanguage.RU
    langCode == "id" || langCode == "in" -> AppLanguage.ID
    else -> AppLanguage.EN
  }
}

fun resolveStrings(
  selected: AppLanguage,
  systemLocale: Locale = Locale.getDefault(),
): AppStrings {
  return when (resolveAppLanguage(selected, systemLocale)) {
    AppLanguage.SYSTEM, AppLanguage.EN -> EnStrings
    AppLanguage.VI -> ViStrings
    AppLanguage.ZH -> ZhStrings
    AppLanguage.JA -> JaStrings
    AppLanguage.KO -> KoStrings
    AppLanguage.ES -> EsStrings
    AppLanguage.FR -> FrStrings
    AppLanguage.DE -> DeStrings
    AppLanguage.PT -> PtStrings
    AppLanguage.RU -> RuStrings
    AppLanguage.ID -> IdStrings
  }
}

data class AppStrings(
  val isVi: Boolean,
  val appName: String,
  val navDashboard: String,
  val navUpload: String,
  val navUpdate: String,
  val navRepos: String,
  val navHistory: String,
  val navSettings: String,
  val toggleThemeTooltip: String,
  // Dashboard
  val orbitalUplinkReady: String,
  val connectedStatus: String,
  val disconnectedStatus: String,
  val connectGithubPrompt: String,
  val connectNow: String,
  val totalUploads: String,
  val totalDataSent: String,
  val gitLfsStatus: String,
  val lfsReadyLabel: String,
  val activeMission: String,
  val noActiveMission: String,
  val quickActions: String,
  val actionUploadProject: String,
  val actionUpdateSync: String,
  val actionUploadZip: String,
  val actionSelectFolder: String,
  val actionSelectFiles: String,
  val actionRepoManager: String,
  val actionUploadHistory: String,
  val actionSettings: String,
  val recentRepositories: String,
  val recentUploads: String,
  val systemTelemetry: String,
  val noRecentRepos: String,
  val noRecentUploads: String,
  // GitHub Auth & Repo Manager
  val githubAuthTitle: String,
  val patClassicOrFine: String,
  val tokenHint: String,
  val tokenSecurityNote: String,
  val validateAndConnect: String,
  val updateToken: String,
  val disconnectGithub: String,
  val deleteCredentials: String,
  val tokenScopesLabel: String,
  val writePermissionOk: String,
  val writePermissionMissing: String,
  val searchReposHint: String,
  val createRepo: String,
  val createBranch: String,
  val repoNameLabel: String,
  val repoDescLabel: String,
  val privateRepoLabel: String,
  val branchNameLabel: String,
  val selectBranch: String,
  val viewOnGithub: String,
  val viewCommits: String,
  val recentCommitsTitle: String,
  val refresh: String,
  val cloneInspectRepo: String,
  // Upload Manager & File Manager
  val uploadWorkbench: String,
  val step1SelectSource: String,
  val step2ReviewFiles: String,
  val step3TargetAndPush: String,
  val pickSingleFile: String,
  val pickMultipleFiles: String,
  val pickProjectFolder: String,
  val pickZipArchive: String,
  val zipModeTitle: String,
  val zipModeRawTitle: String,
  val zipModeRawDesc: String,
  val zipModeExtractTitle: String,
  val zipModeExtractDesc: String,
  val stripZipRootLabel: String,
  val stripZipRootDescOn: String,
  val stripZipRootDescOff: String,
  val totalFilesLabel: String,
  val totalSizeLabel: String,
  val filterByExtension: String,
  val allExtensions: String,
  val excludedFoldersTitle: String,
  val addExclusionHint: String,
  val lfsAutoSummary: String,
  val lfsBadgeStandard: String,
  val lfsBadgeRecommended: String,
  val lfsBadgeRequired: String,
  val targetRepoLabel: String,
  val targetBranchLabel: String,
  val commitMessageLabel: String,
  val defaultCommitMsg: String,
  val checkRemoteSync: String,
  val startUpload: String,
  val pauseTask: String,
  val resumeTask: String,
  val retryTask: String,
  val cancelTask: String,
  val viewLogs: String,
  val viewErrorDetails: String,
  val conflictDetectedTitle: String,
  val conflictSyncAction: String,
  val conflictForceAction: String,
  val conflictCancelAction: String,
  // Advanced Update & Sync (Section XXIII)
  val updateSyncTitle: String,
  val updateSyncSubtitle: String,
  val updateStepTarget: String,
  val updateStepSource: String,
  val updateStepMode: String,
  val updateStepDiffPreview: String,
  val targetSubfolderPrefix: String,
  val targetSubfolderHint: String,
  val modeUpdateChangesTitle: String,
  val modeUpdateChangesDesc: String,
  val modeSyncProjectTitle: String,
  val modeSyncProjectDesc: String,
  val modeOverwriteProjectTitle: String,
  val modeOverwriteProjectDesc: String,
  val modeUpdateSelectedTitle: String,
  val modeUpdateSelectedDesc: String,
  val analyzeDiffBtn: String,
  val analyzingDiffStatus: String,
  val diffAddedLabel: String,
  val diffModifiedLabel: String,
  val diffDeletedLabel: String,
  val diffUnchangedLabel: String,
  val showUnchangedFiles: String,
  val confirmDeletionLabel: String,
  val confirmOverwriteScopeLabel: String,
  val executeUpdateBtn: String,
  val noChangesToCommit: String,
  val rollbackBtn: String,
  val rollbackDialogTitle: String,
  val rollbackWarningMsg: String,
  val rollbackConfirmBtn: String,
  val rollbackSuccessMsg: String,
  // History
  val searchHistoryHint: String,
  val filterAll: String,
  val filterCompleted: String,
  val filterFailed: String,
  val filterRunning: String,
  val clearLocalHistory: String,
  val clearHistoryConfirmMsg: String,
  val openCommit: String,
  // Settings & App Update
  val settingsAppearance: String,
  val darkModeLabel: String,
  val lightModeLabel: String,
  val languageLabel: String,
  val languageSystemLabel: String,
  val languageSystemDesc: String,
  val settingsGitLfs: String,
  val lfsThresholdLabel: String,
  val defaultRepoBranch: String,
  val uploadConcurrencyLabel: String,
  val maxZipExtractLabel: String,
  val clearCacheBtn: String,
  val cacheClearedMsg: String,
  val appUpdateSectionTitle: String,
  val currentVersionLabel: String,
  val repoLinkLabel: String,
  val openRepoBtn: String,
  val openReleasesBtn: String,
  val openActionsBtn: String,
  val copyLinkBtn: String,
  val linkCopiedMsg: String,
  val checkUpdateBtn: String,
  val checkingUpdateStatus: String,
  val upToDateMsg: String,
  val noReleasePublishedMsg: String,
  val updateAvailableTitle: String,
  val downloadAndInstallApkBtn: String,
  val installDownloadedApkBtn: String,
  val downloadingApkStatus: String,
  val grantInstallPermissionMsg: String,
  val overwriteInstallNote: String,
  val aboutAppTitle: String,
  val aboutAppDesc: String,
  val cancel: String,
  val confirm: String,
  val close: String,
)

val LocalStrings = staticCompositionLocalOf { ViStrings }
