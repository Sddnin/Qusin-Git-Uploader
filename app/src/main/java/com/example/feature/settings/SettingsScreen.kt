package com.example.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppLanguage
import com.example.core.common.FormatUtils
import com.example.core.common.LocalStrings
import com.example.core.common.resolveAppLanguage
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.core.update.AppUpdateManager
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onOpenGitHubAuth: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current
  val s = state.settings
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val configuration = LocalConfiguration.current
  val deviceLocale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
  val detectedSystemLanguage = resolveAppLanguage(AppLanguage.SYSTEM, deviceLocale)

  val openUrl: (String) -> Unit = { url ->
    runCatching {
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }
  val launchApkIntent: (Intent) -> Unit = { intent ->
    runCatching { context.startActivity(intent) }
  }
  val unknownSourcesPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    viewModel.onReturnedFromInstallPermissionSetting(onLaunchApkInstaller = launchApkIntent)
  }
  val requestInstallPermission: (Intent) -> Unit = { intent ->
    viewModel.notifyStatus(strings.grantInstallPermissionMsg)
    unknownSourcesPermLauncher.launch(intent)
  }

  var defaultRepo by remember(s.defaultRepoFullName) { mutableStateOf(s.defaultRepoFullName) }
  var defaultBranch by remember(s.defaultBranch) { mutableStateOf(s.defaultBranch) }
  var defaultCommitMsg by remember(s.defaultCommitMessage) { mutableStateOf(s.defaultCommitMessage) }
  var newExclude by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("settings_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // GitHub Account & Token shortcut
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.githubAuthTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
          text = state.user?.let { "@${it.login} (${strings.connectedStatus})" } ?: strings.disconnectedStatus,
          style = MaterialTheme.typography.bodyMedium,
          color = if (state.user != null) palette.successAccent else palette.warningAccent,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          onClick = onOpenGitHubAuth,
          modifier = Modifier.testTag("settings_open_auth_btn"),
        ) {
          Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.updateToken)
        }
      }
    }

    // Appearance & Multi-Language (with System Locale Auto)
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.settingsAppearance, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (s.isDarkMode) strings.darkModeLabel else strings.lightModeLabel,
            style = MaterialTheme.typography.bodyLarge,
          )
          Switch(
            checked = s.isDarkMode,
            onCheckedChange = viewModel::setDarkMode,
            modifier = Modifier.testTag("settings_theme_switch"),
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Rounded.Language,
            contentDescription = null,
            tint = palette.cyanGlow,
            modifier = Modifier.size(18.dp),
          )
          Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
        }
        if (s.language == AppLanguage.SYSTEM) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${strings.languageSystemDesc}: ${detectedSystemLanguage.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.cyanGlow,
          )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AppLanguage.entries.forEach { lang ->
            val labelText = if (lang == AppLanguage.SYSTEM) {
              "${strings.languageSystemLabel} (${detectedSystemLanguage.displayName})"
            } else {
              lang.displayName
            }
            FilterChip(
              selected = s.language == lang,
              onClick = { viewModel.setLanguage(lang) },
              label = { Text(labelText) },
              modifier = Modifier.testTag("lang_chip_${lang.code}"),
            )
          }
        }
      }
    }

    // App Update & GitHub Release / Overwrite Install Card
    item {
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = palette.cyanGlow)
            Text(strings.appUpdateSectionTitle, style = MaterialTheme.typography.titleMedium)
          }
          CyberBadge(
            text = "v${viewModel.currentVersionName} (${viewModel.currentVersionCode})",
            color = palette.cyanGlow,
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(strings.repoLinkLabel, style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          TextButton(
            onClick = { openUrl(AppUpdateManager.REPO_URL) },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("settings_repo_link_btn"),
          ) {
            Text(
              text = AppUpdateManager.REPO_URL,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = palette.cyanGlow,
            )
          }
          IconButton(
            onClick = {
              clipboard.setText(AnnotatedString(AppUpdateManager.REPO_URL))
              viewModel.notifyStatus(strings.linkCopiedMsg)
            },
            modifier = Modifier.testTag("settings_copy_repo_link_btn"),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = strings.copyLinkBtn, modifier = Modifier.size(18.dp))
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { openUrl(AppUpdateManager.REPO_URL) },
            modifier = Modifier.testTag("settings_open_repo_btn"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.openRepoBtn)
          }
          OutlinedButton(
            onClick = { openUrl(AppUpdateManager.LATEST_RELEASE_URL) },
            modifier = Modifier.testTag("settings_open_releases_btn"),
          ) {
            Icon(Icons.Rounded.NewReleases, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.openReleasesBtn)
          }
          OutlinedButton(
            onClick = { openUrl(AppUpdateManager.ACTIONS_URL) },
            modifier = Modifier.testTag("settings_open_actions_btn"),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.openActionsBtn)
          }
        }

        Spacer(Modifier.height(10.dp))
        Button(
          onClick = { viewModel.checkForAppUpdate() },
          enabled = state.updateState !is AppUpdateUiState.Checking &&
            state.updateState !is AppUpdateUiState.Downloading,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_check_update_btn"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.checkUpdateBtn)
        }

        when (val upd = state.updateState) {
          AppUpdateUiState.Idle -> Unit
          AppUpdateUiState.Checking -> {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(strings.checkingUpdateStatus, style = MaterialTheme.typography.bodySmall)
          }
          AppUpdateUiState.NoReleasesPublished -> {
            Spacer(Modifier.height(10.dp))
            Text(
              text = strings.noReleasePublishedMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = palette.warningAccent,
            )
          }
          is AppUpdateUiState.ReleaseFound -> {
            val rel = upd.release
            Spacer(Modifier.height(10.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CyberBadge(
                text = rel.tagName,
                color = if (rel.isNewerThanCurrent) palette.successAccent else palette.cyanGlow,
              )
              Text(
                text = if (rel.isNewerThanCurrent) strings.updateAvailableTitle else strings.upToDateMsg,
                style = MaterialTheme.typography.titleSmall,
                color = if (rel.isNewerThanCurrent) palette.successAccent else MaterialTheme.colorScheme.onSurface,
              )
            }
            if (rel.notes.isNotBlank()) {
              Spacer(Modifier.height(6.dp))
              Text(
                text = rel.notes.take(260),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.height(8.dp))
            if (rel.apkDownloadUrl != null) {
              Button(
                onClick = {
                  viewModel.downloadAndInstallApk(
                    release = rel,
                    onNeedUnknownSourcesPermission = requestInstallPermission,
                    onLaunchApkInstaller = launchApkIntent,
                  )
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_download_install_apk_btn"),
              ) {
                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  "${strings.downloadAndInstallApkBtn} (${FormatUtils.formatBytes(rel.apkSizeBytes)})",
                )
              }
            } else {
              OutlinedButton(
                onClick = { openUrl(rel.htmlUrl) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.openReleasesBtn)
              }
            }
          }
          is AppUpdateUiState.Downloading -> {
            val pct = if (upd.totalBytes > 0L) {
              (upd.downloadedBytes.toFloat() / upd.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
              0f
            }
            Spacer(Modifier.height(10.dp))
            Text(
              text = "${strings.downloadingApkStatus} (${FormatUtils.formatBytes(upd.downloadedBytes)} / ${FormatUtils.formatBytes(upd.totalBytes)})",
              style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            if (upd.totalBytes > 0L) {
              LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            } else {
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
          }
          is AppUpdateUiState.ReadyToInstall -> {
            Spacer(Modifier.height(10.dp))
            Button(
              onClick = {
                viewModel.installReadyApkIfPermitted(
                  apkFile = upd.apkFile,
                  onNeedUnknownSourcesPermission = requestInstallPermission,
                  onLaunchApkInstaller = launchApkIntent,
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_install_apk_btn"),
            ) {
              Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("${strings.installDownloadedApkBtn} (${upd.release.tagName})")
            }
          }
          is AppUpdateUiState.Error -> {
            Spacer(Modifier.height(10.dp))
            Text(
              text = upd.message,
              style = MaterialTheme.typography.bodySmall,
              color = palette.errorAccent,
            )
          }
        }

        Spacer(Modifier.height(8.dp))
        Text(
          text = strings.overwriteInstallNote,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Git LFS & Concurrency & Max Extraction
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.settingsGitLfs, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Git LFS Batch API", style = MaterialTheme.typography.bodyLarge)
          Switch(
            checked = s.lfsEnabled,
            onCheckedChange = { viewModel.setLfsConfig(it, s.lfsThresholdMb) },
          )
        }

        Spacer(Modifier.height(8.dp))
        Text("${strings.lfsThresholdLabel}: ${s.lfsThresholdMb} MB", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = s.lfsThresholdMb.toFloat(),
          onValueChange = { viewModel.setLfsConfig(s.lfsEnabled, it.toInt()) },
          valueRange = 10f..95f,
        )

        Spacer(Modifier.height(8.dp))
        Text("${strings.uploadConcurrencyLabel}: ${s.uploadConcurrency}", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = s.uploadConcurrency.toFloat(),
          onValueChange = { viewModel.setUploadConcurrency(it.toInt()) },
          valueRange = 1f..6f,
          steps = 4,
        )

        Spacer(Modifier.height(8.dp))
        Text("${strings.maxZipExtractLabel}: ${s.maxExtractionSizeMb} MB", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = s.maxExtractionSizeMb.toFloat(),
          onValueChange = { viewModel.setMaxExtractionSizeMb(it.toInt()) },
          valueRange = 100f..1500f,
        )
      }
    }

    // Default Repo, Branch, Commit Message & Excluded Folders
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.defaultRepoBranch, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = defaultRepo,
          onValueChange = {
            defaultRepo = it
            viewModel.setDefaultRepoAndBranch(it, defaultBranch)
          },
          label = { Text(strings.targetRepoLabel) },
          placeholder = { Text("owner/repo") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = defaultBranch,
          onValueChange = {
            defaultBranch = it
            viewModel.setDefaultRepoAndBranch(defaultRepo, it)
          },
          label = { Text(strings.targetBranchLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = defaultCommitMsg,
          onValueChange = {
            defaultCommitMsg = it
            viewModel.setDefaultCommitMessage(it)
          },
          label = { Text(strings.commitMessageLabel) },
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Text(strings.excludedFoldersTitle, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          s.excludedFolders.sorted().forEach { folder ->
            FilterChip(
              selected = true,
              onClick = { viewModel.removeExcludedFolder(folder) },
              label = { Text(folder) },
              trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
            )
          }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = newExclude,
            onValueChange = { newExclude = it },
            placeholder = { Text(strings.addExclusionHint) },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = {
              viewModel.addExcludedFolder(newExclude)
              newExclude = ""
            },
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add exclusion")
          }
        }
      }
    }

    // Cache & History Cleanup
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { viewModel.clearTempCache(strings.cacheClearedMsg) },
            modifier = Modifier
              .weight(1f)
              .testTag("settings_clear_cache_btn"),
          ) {
            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("${strings.clearCacheBtn} (${FormatUtils.formatBytes(state.cacheBytes)})")
          }
          OutlinedButton(
            onClick = { viewModel.clearUploadHistory() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.errorAccent),
            modifier = Modifier
              .weight(1f)
              .testTag("settings_clear_history_btn"),
          ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.clearLocalHistory)
          }
        }

        state.statusMessage?.let { msg ->
          Spacer(Modifier.height(8.dp))
          Text(msg, style = MaterialTheme.typography.bodyMedium, color = palette.successAccent)
        }
      }
    }

    // About App
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.cyanGlow)
          Text(strings.aboutAppTitle, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = strings.aboutAppDesc,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "com.qusin.gituploader • v${viewModel.currentVersionName}",
          style = MaterialTheme.typography.labelSmall,
          color = palette.cyanGlow,
        )
      }
    }
  }
}
