package com.example.feature.upload

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.core.common.AppStrings
import com.example.core.common.FormatUtils
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.CyberGradientProgressBar
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.domain.model.UploadStage
import com.example.domain.model.UploadTaskModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadTaskCard(
  task: UploadTaskModel,
  strings: AppStrings,
  onPause: () -> Unit,
  onResumeOrRetry: () -> Unit,
  onCancel: () -> Unit,
) {
  val palette = LocalCyberPalette.current
  val context = LocalContext.current
  var showLogs by remember { mutableStateOf(false) }
  var showError by remember { mutableStateOf(false) }

  val statusColor = when (task.stage) {
    UploadStage.COMPLETED -> palette.successAccent
    UploadStage.FAILED -> palette.errorAccent
    UploadStage.PAUSED -> palette.warningAccent
    else -> palette.cyanGlow
  }

  GlassCard(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("upload_task_card_${task.id}"),
    glowColor = statusColor,
    contentPadding = PaddingValues(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = task.projectName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${task.repoFullName} • ${task.branch}",
          style = MaterialTheme.typography.labelMedium,
          color = palette.cyanGlow,
        )
      }
      CyberBadge(text = task.stage.name, color = statusColor)
    }

    Spacer(Modifier.height(8.dp))
    CyberGradientProgressBar(progress = task.progressFraction)
    Spacer(Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "${FormatUtils.formatBytes(task.uploadedBytes)} / ${FormatUtils.formatBytes(task.totalBytes)} (${(task.progressFraction * 100).toInt()}%)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "${FormatUtils.formatSpeed(task.speedBytesPerSec)} • ETA ${FormatUtils.formatEta(task.etaSeconds)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (task.commitSha != null) {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Commit: ${task.commitSha.take(12)}",
        style = MaterialTheme.typography.labelMedium,
        color = palette.successAccent,
      )
    }

    Spacer(Modifier.height(10.dp))
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val isRunning = task.stage != UploadStage.COMPLETED &&
        task.stage != UploadStage.FAILED &&
        task.stage != UploadStage.PAUSED

      if (isRunning) {
        OutlinedButton(
          onClick = onPause,
          modifier = Modifier.testTag("task_pause_${task.id}"),
        ) {
          Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(strings.pauseTask)
        }
      } else if (task.stage == UploadStage.PAUSED || task.stage == UploadStage.FAILED) {
        Button(
          onClick = onResumeOrRetry,
          modifier = Modifier.testTag("task_retry_${task.id}"),
        ) {
          Icon(
            if (task.stage == UploadStage.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(if (task.stage == UploadStage.PAUSED) strings.resumeTask else strings.retryTask)
        }
      }

      if (task.stage == UploadStage.COMPLETED) {
        OutlinedButton(
          onClick = {
            val targetUrl = if (!task.commitSha.isNullOrBlank()) {
              "https://github.com/${task.repoFullName}/tree/${task.branch}"
            } else {
              "https://github.com/${task.repoFullName}"
            }
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            }
          },
          modifier = Modifier.testTag("task_open_github_${task.id}"),
        ) {
          Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(strings.viewOnGithub)
        }
      }

      OutlinedButton(onClick = { showLogs = true }) {
        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(strings.viewLogs)
      }

      if (task.errorCode != null) {
        OutlinedButton(
          onClick = { showError = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.errorAccent),
        ) {
          Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(strings.viewErrorDetails)
        }
      }

      TextButton(onClick = onCancel) {
        Icon(Icons.Rounded.Cancel, contentDescription = strings.cancelTask, modifier = Modifier.size(18.dp))
      }
    }
  }

  if (showLogs) {
    AlertDialog(
      onDismissRequest = { showLogs = false },
      title = { Text("${task.projectName} • Logs") },
      text = {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = palette.terminalBg,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = task.logs.ifBlank { "No logs yet." },
            style = MaterialTheme.typography.labelSmall,
            color = palette.terminalText,
            modifier = Modifier
              .padding(12.dp)
              .verticalScroll(rememberScrollState()),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showLogs = false }) { Text(strings.close) }
      },
    )
  }

  if (showError && task.errorCode != null) {
    AlertDialog(
      onDismissRequest = { showError = false },
      title = { Text("${task.errorCode}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(task.errorCause.orEmpty(), style = MaterialTheme.typography.bodyMedium)
          Text(task.errorFix.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = palette.cyanGlow)
        }
      },
      confirmButton = {
        TextButton(onClick = { showError = false }) { Text(strings.close) }
      },
    )
  }
}
