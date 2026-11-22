package com.example.core.security

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.example.core.common.AppError
import com.example.core.common.AppResult

object OAuthBrowserLauncher {
  fun launchAuthUrl(
    context: Context,
    authUri: Uri,
    isDarkMode: Boolean,
    @ColorInt toolbarColor: Int,
  ): AppResult<Unit> {
    return try {
      val colorScheme = if (isDarkMode) {
        CustomTabsIntent.COLOR_SCHEME_DARK
      } else {
        CustomTabsIntent.COLOR_SCHEME_LIGHT
      }
      val colorParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .build()

      val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .setColorScheme(colorScheme)
        .setDefaultColorSchemeParams(colorParams)
        .build()

      customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
      customTabsIntent.launchUrl(context, authUri)
      AppResult.Success(Unit)
    } catch (_: ActivityNotFoundException) {
      // Fallback to standard browser tab if Custom Tabs provider is unavailable
      try {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, authUri)
          .addCategory(Intent.CATEGORY_BROWSABLE)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallbackIntent)
        AppResult.Success(Unit)
      } catch (t: Throwable) {
        AppResult.Failure(
          AppError.Unknown(
            detail = "No browser app found to open GitHub OAuth sign-in: ${t.message ?: t.javaClass.simpleName}",
          ),
        )
      }
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }
}
