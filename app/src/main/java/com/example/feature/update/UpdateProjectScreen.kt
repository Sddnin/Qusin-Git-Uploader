package com.example.feature.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.FormatUtils
import com.example.core.common.LocalStrings
import com.example.core.designsystem.AppErrorCard
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.domain.model.ConflictStrategy
import com.example.domain.model.DiffStatus
import com.example.domain.model.UpdateMode
import com.example.feature.upload.UploadTaskCard

@Composable
fun UpdateProjectScreen(
  viewModel: UpdateProjectViewModel,
) {
  val state = viewModel.uiState.collectAsStateWithLifecycle().value
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current

  val zipLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.let { viewModel.onNewZipPicked(it) } }

  val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri -> uri?.let { viewModel.onNewFolderPicked(it) } }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("update_project_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Header + Step 1: Target Repo, Branch & Subfolder Scope
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Sync, contentDescription = null, tint = palette.cyanGlow)
          Text(strings.updateSyncTitle, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = strings.updateSyncSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(strings.updateStepTarget, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
        Spacer(Modifier.height(6.dp))

        if (state.repos.isEmpty()) {
          Text(strings.noRecentRepos, style = MaterialTheme.typography.bodyMedium, color = palette.warningAccent)
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.repos, key = { it.id }) { repo ->
              FilterChip(
                selected = state.selectedRepo?.id == repo.id,
                onClick = { viewModel.selectRepository(repo) },
                label = { Text(repo.fullName) },
              )
            }
          }
        }

        if (state.branches.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.branches, key = { it.name }) { b ->
              FilterChip(
                selected = state.selectedBranch == b.name,
                onClick = { viewModel.selectBranch(b.name) },
                label = { Text(b.name) },
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = state.targetPrefix,
          onValueChange = viewModel::updateTargetPrefix,
          label = { Text(strings.targetSubfolderPrefix) },
          placeholder = { Text(strings.targetSubfolderHint) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("update_target_prefix_input"),
        )
      }
    }

    // Step 2: Select New Version ZIP or Folder
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.updateStepSource, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
            modifier = Modifier
              .weight(1f)
              .testTag("update_pick_zip_btn"),
          ) {
            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.pickZipArchive, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = { folderLauncher.launch(null) },
            modifier = Modifier
              .weight(1f)
              .testTag("update_pick_folder_btn"),
          ) {
            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.pickProjectFolder, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        if (state.sourceLabel.isNotBlank()) {
          Spacer(Modifier.height(8.dp))
          CyberBadge(text = state.sourceLabel, color = palette.purpleGlow)
        }
        state.zipCommonRootFolder?.let { rootName ->
          Spacer(Modifier.height(10.dp))
          GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(strings.stripZipRootLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                val descTemplate = if (state.stripZipRootFolder) {
                  strings.stripZipRootDescOn
                } else {
                  strings.stripZipRootDescOff
                }
                Text(
                  text = descTemplate.replace("%s", rootName),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (state.stripZipRootFolder) palette.cyanGlow else palette.warningAccent,
                )
              }
              Spacer(Modifier.width(8.dp))
              Switch(
                checked = state.stripZipRootFolder,
                onCheckedChange = viewModel::toggleStripZipRootFolder,
                modifier = Modifier.testTag("update_strip_zip_root_switch"),
              )
            }
          }
        }
        if (state.isScanningOrDiffing) {
          Spacer(Modifier.height(10.dp))
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          Spacer(Modifier.height(4.dp))
          Text(
            text = state.progressStatus.ifBlank { strings.analyzingDiffStatus },
            style = MaterialTheme.typography.labelSmall,
            color = palette.cyanGlow,
          )
        }
      }
    }

    // Step 3: 4 Update Modes + Scan & Diff Button
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(strings.updateStepMode, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
        Spacer(Modifier.height(10.dp))

        val modes = listOf(
          Triple(UpdateMode.UPDATE_CHANGES, strings.modeUpdateChangesTitle, strings.modeUpdateChangesDesc),
          Triple(UpdateMode.SYNC_PROJECT, strings.modeSyncProjectTitle, strings.modeSyncProjectDesc),
          Triple(UpdateMode.OVERWRITE_PROJECT, strings.modeOverwriteProjectTitle, strings.modeOverwriteProjectDesc),
          Triple(UpdateMode.UPDATE_SELECTED, strings.modeUpdateSelectedTitle, strings.modeUpdateSelectedDesc),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          modes.forEach { (mode, title, desc) ->
            val selected = state.updateMode == mode
            GlassCard(
              onClick = { viewModel.setUpdateMode(mode) },
              glowColor = if (selected) palette.cyanGlow else null,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("update_mode_${mode.name}"),
              contentPadding = PaddingValues(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (selected) CyberBadge(text = "Active", color = palette.cyanGlow)
              }
              Spacer(Modifier.height(2.dp))
              Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Button(
          onClick = { viewModel.analyzeDiff() },
          enabled = state.selectedRepo != null && state.stagedLocalFiles.isNotEmpty() && !state.isScanningOrDiffing,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("analyze_diff_button"),
        ) {
          Icon(Icons.Rounded.CompareArrows, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(strings.analyzeDiffBtn, fontWeight = FontWeight.Bold)
        }
      }
    }

    if (state.error != null) {
      item {
        AppErrorCard(error = state.error, isVi = strings.isVi)
      }
    }

    // Step 4: Diff Preview & Safe Confirmation
    state.diffPlan?.let { plan ->
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(strings.updateStepDiffPreview, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            CyberBadge(text = "+${plan.addedCount} ${strings.diffAddedLabel}", color = palette.successAccent)
            CyberBadge(text = "~${plan.modifiedCount} ${strings.diffModifiedLabel}", color = palette.cyanGlow)
            CyberBadge(text = "-${plan.deletedCount} ${strings.diffDeletedLabel}", color = palette.errorAccent)
            CyberBadge(text = "=${plan.unchangedCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(strings.showUnchangedFiles, style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = state.showUnchanged,
              onCheckedChange = viewModel::toggleShowUnchanged,
            )
          }

          val needsDeleteConfirm = (state.updateMode == UpdateMode.SYNC_PROJECT || state.updateMode == UpdateMode.OVERWRITE_PROJECT) &&
            plan.deletedCount > 0
          val needsOverwriteConfirm = state.updateMode == UpdateMode.OVERWRITE_PROJECT

          if (needsDeleteConfirm || needsOverwriteConfirm) {
            Spacer(Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = state.confirmDestructiveAction,
                onCheckedChange = viewModel::setConfirmDestructive,
                modifier = Modifier.testTag("confirm_destructive_update_checkbox"),
              )
              Text(
                text = if (needsOverwriteConfirm) strings.confirmOverwriteScopeLabel else strings.confirmDeletionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.warningAccent,
              )
            }
          }

          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = state.commitMessage,
            onValueChange = viewModel::updateCommitMessage,
            label = { Text(strings.commitMessageLabel) },
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(Modifier.height(10.dp))
          val hasApplicable = when (state.updateMode) {
            UpdateMode.UPDATE_CHANGES -> plan.addedCount + plan.modifiedCount > 0
            UpdateMode.SYNC_PROJECT, UpdateMode.OVERWRITE_PROJECT ->
              plan.addedCount + plan.modifiedCount + plan.deletedCount > 0
            UpdateMode.UPDATE_SELECTED -> plan.items.any { it.selectedForApply && it.status != DiffStatus.UNCHANGED }
          }
          val confirmationSatisfied = (!needsDeleteConfirm && !needsOverwriteConfirm) || state.confirmDestructiveAction

          if (!hasApplicable) {
            Text(strings.noChangesToCommit, style = MaterialTheme.typography.bodyMedium, color = palette.successAccent)
          } else {
            Button(
              onClick = { viewModel.executeUpdate() },
              enabled = confirmationSatisfied,
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("execute_update_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = palette.cyanGlow,
                contentColor = Color(0xFF08111E),
              ),
            ) {
              Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(strings.executeUpdateBtn, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      val visibleDiffItems = plan.items.filter { state.showUnchanged || it.status != DiffStatus.UNCHANGED }
      items(visibleDiffItems.take(200), key = { "${it.status.name}_${it.relativePath}" }) { item ->
        val badgeColor = when (item.status) {
          DiffStatus.ADDED -> palette.successAccent
          DiffStatus.MODIFIED -> palette.cyanGlow
          DiffStatus.DELETED -> palette.errorAccent
          DiffStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val badgeText = when (item.status) {
          DiffStatus.ADDED -> strings.diffAddedLabel
          DiffStatus.MODIFIED -> strings.diffModifiedLabel
          DiffStatus.DELETED -> strings.diffDeletedLabel
          DiffStatus.UNCHANGED -> strings.diffUnchangedLabel
        }

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          glowColor = if (item.status == DiffStatus.DELETED) palette.errorAccent else null,
          contentPadding = PaddingValues(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (state.updateMode == UpdateMode.UPDATE_SELECTED && item.status != DiffStatus.UNCHANGED) {
              Checkbox(
                checked = item.selectedForApply,
                onCheckedChange = { viewModel.toggleDiffItemSelection(item.relativePath) },
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.relativePath,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              val sizeInfo = when (item.status) {
                DiffStatus.DELETED -> FormatUtils.formatBytes(item.remoteSizeBytes ?: 0L)
                DiffStatus.MODIFIED -> "${FormatUtils.formatBytes(item.remoteSizeBytes ?: 0L)} -> ${FormatUtils.formatBytes(item.localSizeBytes)}"
                else -> FormatUtils.formatBytes(item.localSizeBytes)
              }
              val hashInfo = listOfNotNull(item.remoteSha1OrOid, item.localSha1OrOid).joinToString(" -> ")
              Text(
                text = listOfNotNull(sizeInfo, hashInfo.ifBlank { null }, if (item.isLfs) "Git LFS" else null).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            CyberBadge(text = badgeText, color = badgeColor)
          }
        }
      }
    }

    if (state.tasks.isNotEmpty()) {
      items(state.tasks, key = { it.id }) { task ->
        UploadTaskCard(
          task = task,
          strings = strings,
          onPause = { viewModel.pauseTask(task.id) },
          onResumeOrRetry = { viewModel.resumeOrRetryTask(task.id) },
          onCancel = { viewModel.cancelTask(task.id) },
        )
      }
    }
  }

  state.conflictPrompt?.let { conflict ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissConflictPrompt() },
      title = { Text(strings.conflictDetectedTitle) },
      text = {
        Text(
          "Expected HEAD: ${conflict.expectedSha.take(7)}\nRemote HEAD: ${conflict.remoteHeadSha.take(7)}",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(onClick = { viewModel.executeUpdate(ConflictStrategy.SYNC_AND_STACK) }) {
          Text(strings.conflictSyncAction)
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { viewModel.executeUpdate(ConflictStrategy.FORCE_UPDATE) },
            colors = ButtonDefaults.textButtonColors(contentColor = palette.errorAccent),
          ) {
            Text(strings.conflictForceAction)
          }
          TextButton(onClick = { viewModel.dismissConflictPrompt() }) {
            Text(strings.conflictCancelAction)
          }
        }
      },
    )
  }
}
