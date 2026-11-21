package com.example

import com.example.core.common.AppLanguage
import com.example.core.common.resolveAppLanguage
import com.example.core.common.resolveStrings
import com.example.core.update.AppUpdateManager
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndAppUpdateTest {

  @Test
  fun systemLanguageResolvesSupportedDeviceLocalesAndFallsBackToEnglish() {
    assertEquals(AppLanguage.VI, resolveAppLanguage(AppLanguage.SYSTEM, Locale("vi", "VN")))
    assertEquals(AppLanguage.EN, resolveAppLanguage(AppLanguage.SYSTEM, Locale.US))
    assertEquals(AppLanguage.ZH, resolveAppLanguage(AppLanguage.SYSTEM, Locale.SIMPLIFIED_CHINESE))
    assertEquals(AppLanguage.JA, resolveAppLanguage(AppLanguage.SYSTEM, Locale.JAPANESE))
    assertEquals(AppLanguage.KO, resolveAppLanguage(AppLanguage.SYSTEM, Locale.KOREAN))
    assertEquals(AppLanguage.ES, resolveAppLanguage(AppLanguage.SYSTEM, Locale("es", "ES")))
    assertEquals(AppLanguage.FR, resolveAppLanguage(AppLanguage.SYSTEM, Locale.FRENCH))
    assertEquals(AppLanguage.DE, resolveAppLanguage(AppLanguage.SYSTEM, Locale.GERMAN))
    assertEquals(AppLanguage.PT, resolveAppLanguage(AppLanguage.SYSTEM, Locale("pt", "BR")))
    assertEquals(AppLanguage.RU, resolveAppLanguage(AppLanguage.SYSTEM, Locale("ru", "RU")))
    assertEquals(AppLanguage.ID, resolveAppLanguage(AppLanguage.SYSTEM, Locale("id", "ID")))
    // Legacy Indonesian JVM language tag "in"
    assertEquals(AppLanguage.ID, resolveAppLanguage(AppLanguage.SYSTEM, Locale("in", "ID")))
    // Unsupported language falls back safely to English
    assertEquals(AppLanguage.EN, resolveAppLanguage(AppLanguage.SYSTEM, Locale("th", "TH")))
  }

  @Test
  fun explicitLanguageOverridesDeviceLocaleAndProvidesTranslatedStrings() {
    val jaOnViDevice = resolveAppLanguage(AppLanguage.JA, Locale("vi", "VN"))
    assertEquals(AppLanguage.JA, jaOnViDevice)

    for (lang in AppLanguage.entries) {
      val strings = resolveStrings(lang, Locale.US)
      assertTrue("appName blank for $lang", strings.appName.isNotBlank())
      assertTrue("settingsAppearance blank for $lang", strings.settingsAppearance.isNotBlank())
      assertTrue("appUpdateSectionTitle blank for $lang", strings.appUpdateSectionTitle.isNotBlank())
      if (lang == AppLanguage.VI) {
        assertTrue(strings.isVi)
      } else {
        assertFalse(strings.isVi)
      }
    }
  }

  @Test
  fun semanticVersionComparisonDetectsNewerReleasesCorrectly() {
    assertTrue(AppUpdateManager.compareSemanticVersions("v1.0.1", "1.0") > 0)
    assertTrue(AppUpdateManager.compareSemanticVersions("v1.1", "1.0") > 0)
    assertTrue(AppUpdateManager.compareSemanticVersions("2.0.0", "1.9.9") > 0)
    assertEquals(0, AppUpdateManager.compareSemanticVersions("v1.0", "1.0"))
    assertEquals(0, AppUpdateManager.compareSemanticVersions("1.0.0", "1.0"))
    assertTrue(AppUpdateManager.compareSemanticVersions("v1.0", "1.1") < 0)
  }

  @Test
  fun apkDownloadUrlValidatorAllowsOnlyHttpsGitHubHosts() {
    assertTrue(
      AppUpdateManager.isAllowedApkDownloadUrl(
        "https://github.com/Sddnin/Qusin-Git-Uploader/releases/download/v1.1/app-release.apk",
      ),
    )
    assertTrue(
      AppUpdateManager.isAllowedApkDownloadUrl(
        "https://objects.githubusercontent.com/github-production-release-asset-2e65be/app.apk",
      ),
    )
    assertTrue(
      AppUpdateManager.isAllowedApkDownloadUrl(
        "https://release-assets.githubusercontent.com/github-production-release-asset/app.apk",
      ),
    )
    // Reject cleartext HTTP and arbitrary hosts
    assertFalse(
      AppUpdateManager.isAllowedApkDownloadUrl(
        "http://github.com/Sddnin/Qusin-Git-Uploader/releases/download/v1.1/app.apk",
      ),
    )
    assertFalse(
      AppUpdateManager.isAllowedApkDownloadUrl(
        "https://evil-example.com/app.apk",
      ),
    )
  }
}
