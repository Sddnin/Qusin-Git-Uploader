package com.example.feature.settings

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppLanguage
import com.example.core.common.FormatUtils
import com.example.core.common.LocalStrings
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette

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

    // Appearance & Language
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
        Spacer(Modifier.height(10.dp))
        Text(strings.languageLabel, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AppLanguage.entries.forEach { lang ->
            FilterChip(
              selected = s.language == lang,
              onClick = { viewModel.setLanguage(lang) },
              label = { Text(lang.displayName) },
              modifier = Modifier.testTag("lang_chip_${lang.code}"),
            )
          }
        }
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
          valueRange = 1f..4f,
          steps = 2,
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
      GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = palette.purpleGlow) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.purpleGlow)
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
          text = "com.qusin.gituploader • v1.0",
          style = MaterialTheme.typography.labelSmall,
          color = palette.cyanGlow,
        )
      }
    }
  }
}
