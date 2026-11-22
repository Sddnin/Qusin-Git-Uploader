package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.LocalStrings
import com.example.core.common.resolveStrings
import com.example.core.designsystem.QusinTheme
import com.example.domain.model.AppSettings
import com.example.navigation.QusinAppRoot
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = (application as QusinApplication).container
    if (savedInstanceState == null) {
      container.dispatchOAuthCallback(intent?.data)
    }

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
      )
      val configuration = LocalConfiguration.current
      val deviceLocale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
      val strings = resolveStrings(settings.language, deviceLocale)
      val view = LocalView.current

      if (!view.isInEditMode) {
        SideEffect {
          val controller = WindowCompat.getInsetsController(window, view)
          controller.isAppearanceLightStatusBars = !settings.isDarkMode
          controller.isAppearanceLightNavigationBars = !settings.isDarkMode
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
        ) { _ -> }
        LaunchedEffect(Unit) {
          val granted = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
          if (!granted) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      }

      CompositionLocalProvider(LocalStrings provides strings) {
        QusinTheme(darkTheme = settings.isDarkMode) {
          QusinAppRoot(container = container, isDarkMode = settings.isDarkMode)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val container = (application as QusinApplication).container
    container.dispatchOAuthCallback(intent.data)
  }
}
