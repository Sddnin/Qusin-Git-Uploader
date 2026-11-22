package com.example.feature.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.FormatUtils
import com.example.core.common.LocalStrings
import com.example.core.designsystem.AppErrorCard
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.domain.model.UploadHistoryModel
import com.example.domain.model.UploadStage

@Composable
fun HistoryScreen(
  viewModel: HistoryViewModel,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current
  val context = LocalContext.current

  var inspectingItem by remember { mutableStateOf<UploadHistoryModel?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("history_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(strings.actionUploadHistory, style = MaterialTheme.typography.headlineSmall)
        if (state.items.isNotEmpty()) {
          IconButton(
            onClick = { showClearConfirm = true },
            modifier = Modifier.testTag("clear_history_btn"),
          ) {
            Icon(
              Icons.Rounded.DeleteSweep,
              contentDescription = strings.clearLocalHistory,
              tint = palette.errorAccent,
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::updateQuery,
        placeholder = { Text(strings.searchHistoryHint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("history_search_input"),
      )
      Spacer(Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val filters = listOf(
          HistoryStatusFilter.ALL to strings.filterAll,
          HistoryStatusFilter.COMPLETED to strings.filterCompleted,
          HistoryStatusFilter.FAILED to strings.filterFailed,
          HistoryStatusFilter.RUNNING to strings.filterRunning,
        )
        items(filters, key = { it.first.name }) { (filter, label) ->
          FilterChip(
            selected = state.filter == filter,
            onClick = { viewModel.setFilter(filter) },
            label = { Text(label) },
          )
        }
      }
    }

    if (state.isRollingBack) {
      item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
    }

    state.statusBanner?.let { banner ->
      item {
        GlassCard(glowColor = palette.successAccent, contentPadding = PaddingValues(12.dp)) {
          Text(banner, style = MaterialTheme.typography.bodyMedium, color = palette.successAccent)
        }
      }
    }

    state.rollbackError?.let { err ->
      item { AppErrorCard(error = err, isVi = strings.isVi) }
    }

    if (state.items.isEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Rounded.History,
              contentDescription = null,
              tint = palette.cyanGlow,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text = strings.noRecentUploads,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(state.items, key = { it.id }) { item ->
        val ok = item.status == UploadStage.COMPLETED
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          glowColor = if (item.status == UploadStage.FAILED) palette.errorAccent else null,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(item.projectName, style = MaterialTheme.typography.titleMedium)
              Text(
                text = "${item.repoFullName} • ${item.branch}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.cyanGlow,
              )
            }
            CyberBadge(
              text = item.updateMode ?: item.status.name,
              color = if (ok) palette.successAccent else palette.errorAccent,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = "${item.fileCount} files • ${FormatUtils.formatBytes(item.totalSizeBytes)} • ${FormatUtils.formatTimestamp(item.timestamp)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (item.addedCount > 0 || item.modifiedCount > 0 || item.deletedCount > 0) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              CyberBadge(text = "+${item.addedCount}", color = palette.successAccent)
              CyberBadge(text = "~${item.modifiedCount}", color = palette.cyanGlow)
              CyberBadge(text = "-${item.deletedCount}", color = palette.errorAccent)
            }
          }
          if (!item.commitSha.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            val prevPart = item.previousCommitSha?.take(7)?.let { "$it -> " } ?: ""
            Text(
              text = "Commit: $prevPart${item.commitSha.take(12)}",
              style = MaterialTheme.typography.labelSmall,
              color = palette.successAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (!item.errorDetails.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = item.errorDetails,
              style = MaterialTheme.typography.labelSmall,
              color = palette.errorAccent,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Spacer(Modifier.height(10.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              OutlinedButton(
                onClick = {
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.repoUrl)))
                  }
                },
              ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.viewOnGithub)
              }
            }
            if (!item.commitUrl.isNullOrBlank()) {
              item {
                OutlinedButton(
                  onClick = {
                    runCatching {
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.commitUrl)))
                    }
                  },
                ) {
                  Text(strings.openCommit)
                }
              }
            }
            item {
              OutlinedButton(onClick = { inspectingItem = item }) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.viewLogs)
              }
            }
            if (ok && !item.previousCommitSha.isNullOrBlank() && !item.previousTreeSha.isNullOrBlank()) {
              item {
                OutlinedButton(
                  onClick = { viewModel.prepareRollback(item) },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.warningAccent),
                  modifier = Modifier.testTag("rollback_btn_${item.id}"),
                ) {
                  Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(strings.rollbackBtn)
                }
              }
            }
          }
        }
      }
    }
  }

  state.rollbackPreview?.let { preview ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissRollback() },
      title = { Text(strings.rollbackDialogTitle) },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(strings.rollbackWarningMsg, style = MaterialTheme.typography.bodyMedium)
          Text(
            text = "${preview.currentHeadSha.take(7)} -> ${preview.targetCommitSha.take(7)}",
            style = MaterialTheme.typography.labelMedium,
            color = palette.cyanGlow,
          )
          preview.revertedEntries.take(25).forEach { entry ->
            Text(
              text = "• [${entry.status.name}] ${entry.relativePath}",
              style = MaterialTheme.typography.labelSmall,
            )
          }
          if (preview.revertedEntries.size > 25) {
            Text("... +${preview.revertedEntries.size - 25} more files", style = MaterialTheme.typography.labelSmall)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRollback(strings.rollbackSuccessMsg) },
          colors = ButtonDefaults.buttonColors(containerColor = palette.warningAccent),
          modifier = Modifier.testTag("confirm_rollback_dialog_btn"),
        ) {
          Text(strings.rollbackConfirmBtn)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRollback() }) { Text(strings.cancel) }
      },
    )
  }

  inspectingItem?.let { item ->
    AlertDialog(
      onDismissRequest = { inspectingItem = null },
      title = { Text("${item.projectName} • Logs") },
      text = {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = palette.terminalBg,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = item.logs.ifBlank { item.errorDetails ?: "No logs." },
            style = MaterialTheme.typography.labelSmall,
            color = palette.terminalText,
            modifier = Modifier
              .padding(12.dp)
              .verticalScroll(rememberScrollState()),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { inspectingItem = null }) { Text(strings.close) }
      },
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text(strings.clearLocalHistory) },
      text = { Text(strings.clearHistoryConfirmMsg) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearLocalHistory()
            showClearConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.errorAccent),
        ) {
          Text(strings.confirm)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) { Text(strings.cancel) }
      },
    )
  }
}
