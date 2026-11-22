package com.example.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.common.LocalStrings
import com.example.core.designsystem.LocalCyberPalette
import com.example.core.designsystem.SpaceBackground
import com.example.data.AppContainer
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.dashboard.DashboardViewModel
import com.example.feature.history.HistoryScreen
import com.example.feature.history.HistoryViewModel
import com.example.feature.repository.RepositoryScreen
import com.example.feature.repository.RepositoryViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.update.UpdateProjectScreen
import com.example.feature.update.UpdateProjectViewModel
import com.example.feature.upload.UploadScreen
import com.example.feature.upload.UploadViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object UploadRoute
@Serializable object UpdateRoute
@Serializable object RepositoriesRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute

private data class TopLevelDest(
  val route: Any,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QusinAppRoot(
  container: AppContainer,
  isDarkMode: Boolean,
) {
  val navController = rememberNavController()
  val strings = LocalStrings.current
  val palette = LocalCyberPalette.current
  val scope = rememberCoroutineScope()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = navBackStackEntry?.destination

  val dashboardVm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(container))
  val uploadVm: UploadViewModel = viewModel(factory = UploadViewModel.Factory(container))
  val updateVm: UpdateProjectViewModel = viewModel(factory = UpdateProjectViewModel.Factory(container))
  val repoVm: RepositoryViewModel = viewModel(factory = RepositoryViewModel.Factory(container))
  val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(container))
  val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))

  val phoneDestinations = listOf(
    TopLevelDest(DashboardRoute, strings.navDashboard, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    TopLevelDest(UploadRoute, strings.navUpload, Icons.Rounded.CloudUpload, Icons.Outlined.CloudUpload, "nav_upload"),
    TopLevelDest(UpdateRoute, strings.navUpdate, Icons.Rounded.Sync, Icons.Outlined.Sync, "nav_update"),
    TopLevelDest(RepositoriesRoute, strings.navRepos, Icons.Rounded.AccountTree, Icons.Outlined.AccountTree, "nav_repos"),
    TopLevelDest(HistoryRoute, strings.navHistory, Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
  )

  val railDestinations = phoneDestinations + TopLevelDest(
    SettingsRoute,
    strings.navSettings,
    Icons.Rounded.Settings,
    Icons.Outlined.Settings,
    "nav_settings",
  )

  fun navigateSingleTop(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(container) {
    container.oauthCallbacks.collect {
      navigateSingleTop(RepositoriesRoute)
    }
  }

  SpaceBackground {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 600.dp

      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          Column {
            TopAppBar(
              title = {
                Text(
                  text = strings.appName,
                  style = MaterialTheme.typography.titleLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isDarkMode) {
                  MaterialTheme.colorScheme.background
                } else {
                  MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                },
                titleContentColor = MaterialTheme.colorScheme.onSurface,
              ),
              actions = {
                IconButton(
                  onClick = { scope.launch { container.settingsRepository.setDarkMode(!isDarkMode) } },
                  modifier = Modifier.testTag("topbar_theme_toggle_btn"),
                ) {
                  Icon(
                    imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = strings.toggleThemeTooltip,
                    tint = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else palette.cyanGlow,
                  )
                }
                IconButton(
                  onClick = { navigateSingleTop(SettingsRoute) },
                  modifier = Modifier.testTag("topbar_settings_btn"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.navSettings,
                    tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
              },
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
          }
        },
        bottomBar = {
          if (!useRail) {
            Column {
              HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
              NavigationBar(
                containerColor = if (isDarkMode) {
                  MaterialTheme.colorScheme.surface
                } else {
                  MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                },
              ) {
                phoneDestinations.forEach { dest ->
                  val selected = currentDest?.hasRoute(dest.route::class) == true
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateSingleTop(dest.route) },
                    colors = NavigationBarItemDefaults.colors(
                      selectedIconColor = palette.cyanGlow,
                      selectedTextColor = MaterialTheme.colorScheme.onSurface,
                      indicatorColor = palette.cyanGlow.copy(alpha = if (isDarkMode) 0.14f else 0.18f),
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = {
                      Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label,
                      )
                    },
                    label = {
                      Text(
                        text = dest.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    modifier = Modifier.testTag(dest.tag),
                  )
                }
              }
            }
          }
        },
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          if (useRail) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            ) {
              railDestinations.forEach { dest ->
                val selected = currentDest?.hasRoute(dest.route::class) == true
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateSingleTop(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = dest.label,
                      tint = if (selected) palette.cyanGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  },
                  label = { Text(dest.label, maxLines = 1) },
                  modifier = Modifier.testTag(dest.tag),
                )
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.weight(1f),
            enterTransition = {
              fadeIn(animationSpec = tween(220)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
            },
            exitTransition = {
              fadeOut(animationSpec = tween(180))
            },
            popEnterTransition = {
              fadeIn(animationSpec = tween(220)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
            },
            popExitTransition = {
              fadeOut(animationSpec = tween(180))
            },
          ) {
            composable<DashboardRoute> {
              DashboardScreen(
                viewModel = dashboardVm,
                onNavigateUpload = { mode ->
                  uploadVm.prepareQuickLaunchMode(mode)
                  navigateSingleTop(UploadRoute)
                },
                onNavigateUpdate = { navigateSingleTop(UpdateRoute) },
                onNavigateRepos = { navigateSingleTop(RepositoriesRoute) },
                onNavigateHistory = { navigateSingleTop(HistoryRoute) },
                onNavigateSettings = { navigateSingleTop(SettingsRoute) },
              )
            }
            composable<UploadRoute> {
              UploadScreen(viewModel = uploadVm)
            }
            composable<UpdateRoute> {
              UpdateProjectScreen(viewModel = updateVm)
            }
            composable<RepositoriesRoute> {
              RepositoryScreen(viewModel = repoVm)
            }
            composable<HistoryRoute> {
              HistoryScreen(viewModel = historyVm)
            }
            composable<SettingsRoute> {
              SettingsScreen(
                viewModel = settingsVm,
                onOpenGitHubAuth = { navigateSingleTop(RepositoriesRoute) },
              )
            }
          }
        }
      }
    }
  }
}
