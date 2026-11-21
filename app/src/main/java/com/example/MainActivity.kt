package com.example

import android.Manifest
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
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppLanguage
import com.example.core.common.EnStrings
import com.example.core.common.LocalStrings
import com.example.core.common.ViStrings
import com.example.core.designsystem.QusinTheme
import com.example.domain.model.AppSettings
import com.example.navigation.QusinAppRoot

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = (application as QusinApplication).container

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
      )
      val strings = if (settings.language == AppLanguage.VI) ViStrings else EnStrings

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
}
