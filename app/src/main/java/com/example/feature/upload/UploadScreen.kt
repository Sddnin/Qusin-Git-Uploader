package com.example.feature.upload

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.model.LfsClassification
import com.example.domain.model.ZipMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
  viewModel: UploadViewModel,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current

  var newExcludeInput by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("upload_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Step 1: System File Picker Component (Source code, Archives, APKs, Folders)
    item {
      DeviceFileSelectionComponent(
        selectedCategory = state.selectedPickerCategory,
        onSelectCategory = viewModel::selectPickerCategory,
        onFilesPicked = viewModel::onSingleOrMultiFilesPicked,
        onArchivePicked = viewModel::onZipPicked,
        onFolderPicked = viewModel::onFolderPicked,
        isScanning = state.isScanning,
        stagedFileCount = state.selectedCount,
        stagedTotalBytes = state.selectedTotalBytes,
        stagedLfsCount = state.lfsCount,
        strings = strings,
      )
    }

    // ZIP Mode A / Mode B Chooser
    if (state.pendingZipItem != null) {
      val zip = state.pendingZipItem!!
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = palette.purpleGlow)
            Text("${strings.zipModeTitle}: ${zip.name}", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(Modifier.height(10.dp))

          GlassCard(
            onClick = { viewModel.chooseZipMode(ZipMode.RAW_ZIP) },
            glowColor = if (state.selectedZipMode == ZipMode.RAW_ZIP) palette.cyanGlow else null,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("zip_mode_raw_card"),
            contentPadding = PaddingValues(12.dp),
          ) {
            Text(strings.zipModeRawTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              strings.zipModeRawDesc,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(8.dp))
          GlassCard(
            onClick = { viewModel.chooseZipMode(ZipMode.EXTRACT_AND_UPLOAD) },
            glowColor = if (state.selectedZipMode == ZipMode.EXTRACT_AND_UPLOAD) palette.cyanGlow else null,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("zip_mode_extract_card"),
            contentPadding = PaddingValues(12.dp),
          ) {
            Text(strings.zipModeExtractTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              strings.zipModeExtractDesc,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (state.zipSkippedCount > 0 || state.zipDuplicateCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
              text = "Skipped excluded: ${state.zipSkippedCount} • Duplicates renamed: ${state.zipDuplicateCount}",
              style = MaterialTheme.typography.labelMedium,
              color = palette.cyanGlow,
            )
          }
        }
      }
    }

    if (state.error != null) {
      item {
        AppErrorCard(
          error = state.error!!,
          isVi = strings.isVi,
        )
      }
    }

    // Step 2: File Inspection, Exclusions & Git LFS Routing
    if (state.allFiles.isNotEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(strings.step2ReviewFiles, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CyberBadge(
              text = "${strings.totalFilesLabel}: ${state.selectedCount}/${state.allFiles.size}",
              color = palette.cyanGlow,
            )
            CyberBadge(
              text = "${strings.totalSizeLabel}: ${FormatUtils.formatBytes(state.selectedTotalBytes)}",
              color = palette.purpleGlow,
            )
            if (state.lfsCount > 0) {
              CyberBadge(
                text = "LFS: ${state.lfsCount}",
                color = palette.warningAccent,
              )
            }
          }
          Spacer(Modifier.height(8.dp))
          Text(
            text = strings.lfsAutoSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

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
                  modifier = Modifier.testTag("strip_zip_root_switch"),
                )
              }
            }
          }

          // Extension filters
          if (state.availableExtensions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(strings.filterByExtension, style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              item {
                FilterChip(
                  selected = state.activeExtensionFilter == null,
                  onClick = { viewModel.setExtensionFilter(null) },
                  label = { Text(strings.allExtensions) },
                )
              }
              items(state.availableExtensions) { ext ->
                FilterChip(
                  selected = state.activeExtensionFilter == ext,
                  onClick = { viewModel.setExtensionFilter(ext) },
                  label = { Text(".$ext") },
                )
              }
            }
          }

          // Excluded folders editor
          Spacer(Modifier.height(10.dp))
          Text(strings.excludedFoldersTitle, style = MaterialTheme.typography.labelMedium)
          Spacer(Modifier.height(4.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            state.excludedFolders.sorted().forEach { folder ->
              FilterChip(
                selected = true,
                onClick = { viewModel.removeExcludedFolder(folder) },
                label = { Text(folder) },
                trailingIcon = {
                  Icon(Icons.Rounded.Close, contentDescription = "Remove $folder", modifier = Modifier.size(14.dp))
                },
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = newExcludeInput,
              onValueChange = { newExcludeInput = it },
              placeholder = { Text(strings.addExclusionHint) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = {
                viewModel.addExcludedFolder(newExcludeInput)
                newExcludeInput = ""
              },
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Add exclusion")
            }
          }
        }
      }

      items(state.visibleFiles.take(150), key = { it.id }) { item ->
        val lfsBadgeColor = when (item.lfsClassification) {
          LfsClassification.STANDARD_GIT -> if (item.useLfs) palette.warningAccent else palette.successAccent
          LfsClassification.LFS_RECOMMENDED -> palette.warningAccent
          LfsClassification.LFS_REQUIRED -> palette.errorAccent
        }
        val lfsBadgeText = when {
          item.lfsClassification == LfsClassification.LFS_REQUIRED -> strings.lfsBadgeRequired
          item.lfsClassification == LfsClassification.LFS_RECOMMENDED -> strings.lfsBadgeRecommended
          item.useLfs -> "Git LFS"
          else -> strings.lfsBadgeStandard
        }

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = item.isSelected,
              onCheckedChange = { viewModel.toggleFileSelected(item.id) },
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.relativePath,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${item.fileType} • .${item.extension.ifEmpty { "bin" }} • ${FormatUtils.formatBytes(item.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            CyberBadge(
              text = lfsBadgeText,
              color = lfsBadgeColor,
              modifier = Modifier.clickable { viewModel.toggleFileLfs(item.id) },
            )
          }
        }
      }
    }

    // Step 3: Target Repo, Branch, Commit Message & Push
    item {
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(strings.step3TargetAndPush, style = MaterialTheme.typography.titleMedium, color = palette.cyanGlow)
        Spacer(Modifier.height(10.dp))

        Text(strings.targetRepoLabel, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (state.repos.isEmpty()) {
          Text(
            text = strings.noRecentRepos,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.warningAccent,
          )
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
          Text(strings.targetBranchLabel, style = MaterialTheme.typography.labelMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.branches, key = { it.name }) { branch ->
              FilterChip(
                selected = state.selectedBranch == branch.name,
                onClick = { viewModel.selectBranch(branch.name) },
                label = { Text(branch.name) },
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = state.commitMessage,
          onValueChange = viewModel::updateCommitMessage,
          label = { Text(strings.commitMessageLabel) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("commit_message_input"),
        )

        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.checkRemoteSync,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = state.checkRemoteSync,
            onCheckedChange = viewModel::toggleCheckRemoteSync,
          )
        }

        Spacer(Modifier.height(12.dp))
        Button(
          onClick = { viewModel.startUpload() },
          enabled = state.selectedCount > 0 && state.selectedRepo != null,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("start_upload_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = palette.cyanGlow,
            contentColor = Color(0xFF08111E),
          ),
        ) {
          Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(strings.startUpload, fontWeight = FontWeight.Bold)
        }
      }
    }

    // Upload Queue & Tasks
    if (state.tasks.isNotEmpty()) {
      items(state.tasks, key = { it.id }) { task ->
        UploadTaskCard(
          task = task,
          strings = strings,
          onPause = { viewModel.pauseTask(task.id) },
          onResumeOrRetry = { viewModel.resumeOrRetryTask(task.id) },
          onCancel = { viewModel.cancelAndRemoveTask(task.id) },
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
        Button(
          onClick = { viewModel.startUpload(ConflictStrategy.SYNC_AND_STACK) },
        ) {
          Text(strings.conflictSyncAction)
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { viewModel.startUpload(ConflictStrategy.FORCE_UPDATE) },
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
