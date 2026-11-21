package com.example.feature.repository

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.LocalStrings
import com.example.core.designsystem.AppErrorCard
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette
import com.example.core.designsystem.SkeletonShimmerBox

@Composable
fun RepositoryScreen(
  viewModel: RepositoryViewModel,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current
  val context = LocalContext.current

  var tokenInput by remember { mutableStateOf("") }
  var showToken by remember { mutableStateOf(false) }
  var showCreateRepoDialog by remember { mutableStateOf(false) }
  var showCreateBranchDialog by remember { mutableStateOf(false) }
  var showUpdateTokenInput by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("repository_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // GitHub Authentication Card
    item {
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (state.user != null) palette.successAccent else palette.cyanGlow,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(Icons.Rounded.Security, contentDescription = null, tint = palette.cyanGlow)
          Text(strings.githubAuthTitle, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(10.dp))

        if (state.user != null && !showUpdateTokenInput) {
          val u = state.user!!
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            AsyncImage(
              model = u.avatarUrl,
              contentDescription = u.login,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text("@${u.login}", style = MaterialTheme.typography.titleMedium)
              Text(
                text = if (u.hasWriteAccess) strings.writePermissionOk else strings.writePermissionMissing,
                style = MaterialTheme.typography.labelMedium,
                color = if (u.hasWriteAccess) palette.successAccent else palette.warningAccent,
              )
              if (u.scopes.isNotEmpty()) {
                Text(
                  text = "${strings.tokenScopesLabel}: ${u.scopes.joinToString(", ")}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { showUpdateTokenInput = true },
              modifier = Modifier.weight(1f).testTag("repo_update_token_btn"),
            ) {
              Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(strings.updateToken, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = { viewModel.disconnect(deleteCredentials = false) },
              modifier = Modifier.weight(1f).testTag("repo_disconnect_btn"),
            ) {
              Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(strings.disconnectGithub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          Spacer(Modifier.height(6.dp))
          TextButton(
            onClick = { viewModel.disconnect(deleteCredentials = true) },
            modifier = Modifier.testTag("repo_delete_credentials_btn"),
            colors = ButtonDefaults.textButtonColors(contentColor = palette.errorAccent),
          ) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.deleteCredentials)
          }
        } else {
          Text(
            text = strings.patClassicOrFine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            placeholder = { Text(strings.tokenHint) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showToken = !showToken }) {
                Icon(
                  imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = "Toggle token visibility",
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("github_pat_input"),
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = strings.tokenSecurityNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                viewModel.connectWithToken(tokenInput)
                tokenInput = ""
                showUpdateTokenInput = false
              },
              enabled = tokenInput.isNotBlank() && !state.isLoading,
              modifier = Modifier.testTag("github_connect_button"),
            ) {
              Text(strings.validateAndConnect)
            }
            if (showUpdateTokenInput) {
              TextButton(onClick = { showUpdateTokenInput = false }) {
                Text(strings.cancel)
              }
            }
          }
        }
      }
    }

    if (state.infoBanner != null) {
      item {
        GlassCard(glowColor = palette.successAccent, contentPadding = PaddingValues(12.dp)) {
          Text(state.infoBanner!!, style = MaterialTheme.typography.bodyMedium, color = palette.successAccent)
        }
      }
    }

    if (state.error != null) {
      item {
        AppErrorCard(
          error = state.error!!,
          isVi = strings.isVi,
          onRetry = { viewModel.refreshRepositories() },
          retryLabel = strings.retryTask,
        )
      }
    }

    // Repository Search & Actions Bar
    if (state.user != null) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            placeholder = { Text(strings.searchReposHint) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("repo_search_input"),
          )
          IconButton(
            onClick = { viewModel.refreshRepositories() },
            modifier = Modifier.testTag("repo_refresh_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = strings.refresh)
          }
          Button(
            onClick = { showCreateRepoDialog = true },
            modifier = Modifier.testTag("repo_create_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.createRepo)
          }
        }
      }

      if (state.isLoading) {
        items(3) {
          SkeletonShimmerBox(
            modifier = Modifier
              .fillMaxWidth()
              .height(82.dp),
          )
        }
      } else {
        items(state.repos, key = { it.id }) { repo ->
          val isSelected = state.selectedRepo?.id == repo.id
          GlassCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("repo_card_${repo.name}"),
            glowColor = if (isSelected) palette.cyanGlow else null,
            onClick = { viewModel.selectRepository(repo) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                if (!repo.description.isNullOrBlank()) {
                  Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              IconButton(
                onClick = {
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)))
                  }
                },
              ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = strings.viewOnGithub)
              }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CyberBadge(
                text = if (repo.isPrivate) "Private" else "Public",
                color = if (repo.isPrivate) palette.purpleGlow else palette.successAccent,
              )
              CyberBadge(text = "Branch: ${repo.defaultBranch}", color = palette.cyanGlow)
              CyberBadge(
                text = if (repo.canPush) "Push OK" else "Read Only",
                color = if (repo.canPush) palette.successAccent else palette.warningAccent,
              )
            }
          }
        }
      }

      // Selected Repository Branch & Commit Inspector
      state.selectedRepo?.let { selRepo ->
        item {
          GlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = palette.purpleGlow,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${selRepo.name} • ${strings.selectBranch}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = { showCreateBranchDialog = true },
                modifier = Modifier.testTag("create_branch_button"),
              ) {
                Icon(Icons.Rounded.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.createBranch)
              }
            }

            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(state.branches, key = { it.name }) { branch ->
                FilterChip(
                  selected = branch.name == state.selectedBranch,
                  onClick = { viewModel.selectBranch(branch.name) },
                  label = { Text(branch.name) },
                )
              }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
              onClick = { viewModel.cloneZipballSnapshot() },
              modifier = Modifier.testTag("clone_repo_snapshot_btn"),
            ) {
              Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(strings.cloneInspectRepo)
            }

            if (state.commits.isNotEmpty()) {
              Spacer(Modifier.height(12.dp))
              Text(strings.recentCommitsTitle, style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.height(6.dp))
              state.commits.take(5).forEach { commit ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = commit.message.lines().firstOrNull().orEmpty(),
                      style = MaterialTheme.typography.bodyMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${commit.sha.take(7)} • ${commit.authorName}",
                      style = MaterialTheme.typography.labelSmall,
                      color = palette.cyanGlow,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showCreateRepoDialog) {
    var repoName by remember { mutableStateOf("") }
    var repoDesc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showCreateRepoDialog = false },
      title = { Text(strings.createRepo) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = repoName,
            onValueChange = { repoName = it },
            label = { Text(strings.repoNameLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = repoDesc,
            onValueChange = { repoDesc = it },
            label = { Text(strings.repoDescLabel) },
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(strings.privateRepoLabel, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createRepository(repoName, repoDesc, isPrivate)
            showCreateRepoDialog = false
          },
          enabled = repoName.isNotBlank(),
        ) {
          Text(strings.confirm)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateRepoDialog = false }) { Text(strings.cancel) }
      },
    )
  }

  if (showCreateBranchDialog) {
    var branchName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateBranchDialog = false },
      title = { Text(strings.createBranch) },
      text = {
        OutlinedTextField(
          value = branchName,
          onValueChange = { branchName = it },
          label = { Text(strings.branchNameLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createBranch(branchName)
            showCreateBranchDialog = false
          },
          enabled = branchName.isNotBlank(),
        ) {
          Text(strings.confirm)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateBranchDialog = false }) { Text(strings.cancel) }
      },
    )
  }
}
