package com.example.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.common.LocalStrings
import com.example.core.designsystem.AppErrorCard
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.CyberGradientProgressBar
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.core.designsystem.SkeletonShimmerBox
import com.example.core.designsystem.StatusBeacon
import com.example.domain.model.UploadStage

enum class UploadQuickLaunchMode {
  PROJECT,
  ZIP,
  FOLDER,
  FILES,
}

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  onNavigateUpload: (UploadQuickLaunchMode) -> Unit,
  onNavigateUpdate: () -> Unit,
  onNavigateRepos: () -> Unit,
  onNavigateHistory: () -> Unit,
  onNavigateSettings: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 680.dp

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("dashboard_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Banner & Account Header
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(0.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(176.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_cyber_hero_banner),
              contentDescription = strings.appName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color(0x66050811),
                      Color(0xE6080E1E),
                    ),
                  ),
                ),
            )
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  if (state.user?.avatarUrl != null) {
                    AsyncImage(
                      model = state.user?.avatarUrl,
                      contentDescription = state.user?.login,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    )
                  } else {
                    Image(
                      painter = painterResource(id = R.drawable.img_avatar_placeholder),
                      contentDescription = "Avatar",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    )
                  }
                  Column {
                    Text(
                      text = state.user?.let { "@${it.login}" } ?: strings.appName,
                      style = MaterialTheme.typography.titleLarge,
                      color = Color.White,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = state.user?.name ?: strings.orbitalUplinkReady,
                      style = MaterialTheme.typography.bodyMedium,
                      color = Color(0xFFCBD5E1),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                StatusBeacon(
                  active = state.user != null,
                  label = if (state.user != null) strings.connectedStatus else strings.disconnectedStatus,
                )
              }

              if (state.user == null && !state.isConnecting) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = strings.connectGithubPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(Modifier.width(10.dp))
                  Button(
                    onClick = onNavigateRepos,
                    modifier = Modifier.testTag("dashboard_connect_button"),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = palette.cyanGlow,
                      contentColor = Color(0xFF050811),
                    ),
                  ) {
                    Text(strings.connectNow, fontWeight = FontWeight.Bold)
                  }
                }
              } else if (state.isConnecting) {
                SkeletonShimmerBox(
                  modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(22.dp),
                )
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CyberBadge(
                    text = "Repos: ${(state.user?.publicRepos ?: 0) + (state.user?.privateRepos ?: 0)}",
                    color = palette.cyanGlow,
                  )
                  CyberBadge(
                    text = if (state.user?.hasWriteAccess == true) "Push Ready" else "Read-Only",
                    color = if (state.user?.hasWriteAccess == true) palette.successAccent else palette.warningAccent,
                  )
                }
              }
            }
          }
        }
      }

      if (state.connectionError != null) {
        item {
          AppErrorCard(
            error = state.connectionError!!,
            isVi = strings.isVi,
            onRetry = { viewModel.refreshSessionIfAvailable() },
            retryLabel = strings.retryTask,
          )
        }
      }

      // Telemetry Metrics Strip (Total Uploads, Total Size, Git LFS Status)
      item {
        if (isWide) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MetricTile(
              icon = Icons.Rounded.CloudDone,
              label = strings.totalUploads,
              value = state.totalUploads.toString(),
              accent = palette.cyanGlow,
              modifier = Modifier.weight(1f),
            )
            MetricTile(
              icon = Icons.Rounded.Storage,
              label = strings.totalDataSent,
              value = FormatUtils.formatBytes(state.totalBytesUploaded),
              accent = palette.purpleGlow,
              modifier = Modifier.weight(1f),
            )
            MetricTile(
              icon = Icons.Rounded.Dns,
              label = strings.gitLfsStatus,
              value = strings.lfsReadyLabel,
              accent = palette.successAccent,
              modifier = Modifier.weight(1.3f),
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              MetricTile(
                icon = Icons.Rounded.CloudDone,
                label = strings.totalUploads,
                value = state.totalUploads.toString(),
                accent = palette.cyanGlow,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                icon = Icons.Rounded.Storage,
                label = strings.totalDataSent,
                value = FormatUtils.formatBytes(state.totalBytesUploaded),
                accent = palette.purpleGlow,
                modifier = Modifier.weight(1f),
              )
            }
            MetricTile(
              icon = Icons.Rounded.Dns,
              label = strings.gitLfsStatus,
              value = strings.lfsReadyLabel,
              accent = palette.successAccent,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      // Active Running Tasks
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          glowColor = if (state.activeTasks.isNotEmpty()) palette.cyanGlow else palette.purpleGlow,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = strings.activeMission,
              style = MaterialTheme.typography.titleLarge,
            )
            CyberBadge(
              text = "${state.activeTasks.size} active",
              color = if (state.activeTasks.isNotEmpty()) palette.cyanGlow else palette.purpleGlow,
            )
          }
          Spacer(Modifier.height(10.dp))
          if (state.activeTasks.isEmpty()) {
            Text(
              text = strings.noActiveMission,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            state.activeTasks.forEach { task ->
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp),
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
                      text = "${task.repoFullName} (${task.branch}) • ${task.stage.name}",
                      style = MaterialTheme.typography.labelMedium,
                      color = palette.cyanGlow,
                    )
                  }
                  IconButton(
                    onClick = { viewModel.pauseTask(task.id) },
                    modifier = Modifier.testTag("dashboard_pause_task_${task.id}"),
                  ) {
                    Icon(Icons.Rounded.Pause, contentDescription = strings.pauseTask)
                  }
                }
                Spacer(Modifier.height(6.dp))
                CyberGradientProgressBar(progress = task.progressFraction)
                Spacer(Modifier.height(4.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "${FormatUtils.formatBytes(task.uploadedBytes)} / ${FormatUtils.formatBytes(task.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "${FormatUtils.formatSpeed(task.speedBytesPerSec)} • ETA ${FormatUtils.formatEta(task.etaSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      // Quick Actions Grid (All 7 Requested Functions!)
      item {
        Text(
          text = strings.quickActions,
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        val actions = listOf(
          QuickActionItem(strings.actionUploadProject, Icons.Rounded.RocketLaunch, palette.cyanGlow, "qa_upload_project") {
            onNavigateUpload(UploadQuickLaunchMode.PROJECT)
          },
          QuickActionItem(strings.actionUpdateSync, Icons.Rounded.Sync, palette.successAccent, "qa_update_sync", onNavigateUpdate),
          QuickActionItem(strings.actionUploadZip, Icons.Rounded.Archive, palette.purpleGlow, "qa_upload_zip") {
            onNavigateUpload(UploadQuickLaunchMode.ZIP)
          },
          QuickActionItem(strings.actionSelectFolder, Icons.Rounded.CreateNewFolder, palette.successAccent, "qa_select_folder") {
            onNavigateUpload(UploadQuickLaunchMode.FOLDER)
          },
          QuickActionItem(strings.actionSelectFiles, Icons.Rounded.InsertDriveFile, Color(0xFF38BDF8), "qa_select_files") {
            onNavigateUpload(UploadQuickLaunchMode.FILES)
          },
          QuickActionItem(strings.actionRepoManager, Icons.Rounded.AccountTree, palette.warningAccent, "qa_repo_manager", onNavigateRepos),
          QuickActionItem(strings.actionUploadHistory, Icons.Rounded.History, Color(0xFF818CF8), "qa_upload_history", onNavigateHistory),
          QuickActionItem(strings.actionSettings, Icons.Rounded.Settings, Color(0xFFA78BFA), "qa_settings", onNavigateSettings),
        )

        val columns = if (isWide) 4 else 2
        val rows = actions.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          rows.forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              rowItems.forEach { action ->
                GlassCard(
                  modifier = Modifier
                    .weight(1f)
                    .testTag(action.tag),
                  glowColor = action.color,
                  onClick = action.onClick,
                  contentPadding = PaddingValues(14.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(38.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(action.color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = action.icon,
                      contentDescription = action.title,
                      tint = action.color,
                      modifier = Modifier.size(22.dp),
                    )
                  }
                  Spacer(Modifier.height(10.dp))
                  Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              if (rowItems.size < columns) {
                repeat(columns - rowItems.size) {
                  Spacer(Modifier.weight(1f))
                }
              }
            }
          }
        }
      }

      // Recent Repositories
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.recentRepositories, style = MaterialTheme.typography.titleLarge)
          IconButton(onClick = onNavigateRepos) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = strings.actionRepoManager)
          }
        }
        if (state.recentRepos.isEmpty()) {
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = strings.noRecentRepos,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.recentRepos, key = { it.id }) { repo ->
              GlassCard(
                modifier = Modifier.width(230.dp),
                onClick = onNavigateRepos,
              ) {
                Text(
                  text = repo.name,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = repo.fullName,
                  style = MaterialTheme.typography.labelMedium,
                  color = palette.cyanGlow,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  CyberBadge(
                    text = if (repo.isPrivate) "Private" else "Public",
                    color = if (repo.isPrivate) palette.purpleGlow else palette.successAccent,
                  )
                  CyberBadge(text = repo.defaultBranch, color = palette.cyanGlow)
                }
              }
            }
          }
        }
      }

      // Recent Uploads
      item {
        Text(strings.recentUploads, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (state.recentHistory.isEmpty()) {
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = strings.noRecentUploads,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.recentHistory.forEach { item ->
              val ok = item.status == UploadStage.COMPLETED
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (ok) palette.successAccent else palette.errorAccent,
                onClick = onNavigateHistory,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(item.projectName, style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = "${item.repoFullName}@${item.branch} • ${item.fileCount} files",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  CyberBadge(
                    text = if (ok) FormatUtils.formatBytes(item.totalSizeBytes) else item.status.name,
                    color = if (ok) palette.successAccent else palette.errorAccent,
                  )
                }
              }
            }
          }
        }
      }

      // System Status Notification Bar
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          glowColor = palette.cyanGlow,
          contentPadding = PaddingValues(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Terminal,
              contentDescription = strings.systemTelemetry,
              tint = palette.cyanGlow,
            )
            Column {
              Text(strings.systemTelemetry, style = MaterialTheme.typography.labelMedium, color = palette.cyanGlow)
              Text(
                text = state.systemMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricTile(
  icon: ImageVector,
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  GlassCard(
    modifier = modifier,
    glowColor = accent,
    contentPadding = PaddingValues(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private data class QuickActionItem(
  val title: String,
  val icon: ImageVector,
  val color: Color,
  val tag: String,
  val onClick: () -> Unit,
)
