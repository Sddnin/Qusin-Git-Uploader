package com.example.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.github.api.GitHubApiService
import com.example.github.dto.GitHubReleaseDto
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppReleaseInfo(
  val tagName: String,
  val releaseName: String,
  val notes: String,
  val htmlUrl: String,
  val publishedAt: String?,
  val apkAssetName: String?,
  val apkDownloadUrl: String?,
  val apkSizeBytes: Long,
  val isNewerThanCurrent: Boolean,
)

sealed interface AppReleaseCheckResult {
  data class Success(val release: AppReleaseInfo) : AppReleaseCheckResult
  data object NoReleasesPublished : AppReleaseCheckResult
  data class Failure(val message: String) : AppReleaseCheckResult
}

class AppUpdateManager(
  private val context: Context,
  private val api: GitHubApiService,
  private val downloadClient: OkHttpClient,
) {
  companion object {
    const val REPO_OWNER = "Sddnin"
    const val REPO_NAME = "Qusin-Git-Uploader"
    const val REPO_URL = "https://github.com/Sddnin/Qusin-Git-Uploader"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_URL = "$REPO_URL/releases/latest"
    const val ACTIONS_URL = "$REPO_URL/actions"

    fun isAllowedApkDownloadUrl(url: String): Boolean {
      val parsed = runCatching { URI(url) }.getOrNull() ?: return false
      if (!parsed.scheme.equals("https", ignoreCase = true)) return false
      val host = parsed.host?.lowercase() ?: return false
      return host == "github.com" ||
        host.endsWith(".github.com") ||
        host == "objects.githubusercontent.com" ||
        host == "release-assets.githubusercontent.com" ||
        host.endsWith(".githubusercontent.com")
    }

    /**
     * Compares version strings like "v1.2.0" vs "1.0" or "v1.0.1" vs "1.0".
     * Returns > 0 if candidate is newer than current.
     */
    fun compareSemanticVersions(candidate: String, current: String): Int {
      val cleanA = candidate.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
      val cleanB = current.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
      val partsA = cleanA.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
      val partsB = cleanB.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
      val maxLen = maxOf(partsA.size, partsB.size)
      for (i in 0 until maxLen) {
        val a = partsA.getOrElse(i) { 0 }
        val b = partsB.getOrElse(i) { 0 }
        if (a != b) return a.compareTo(b)
      }
      return 0
    }
  }

  val currentVersionName: String = BuildConfig.VERSION_NAME
  val currentVersionCode: Int = BuildConfig.VERSION_CODE

  suspend fun checkLatestRelease(): AppReleaseCheckResult = withContext(Dispatchers.IO) {
    try {
      val latestResp = api.getLatestRelease(REPO_OWNER, REPO_NAME)
      val release: GitHubReleaseDto? = if (latestResp.isSuccessful) {
        latestResp.body()
      } else if (latestResp.code() == 404) {
        val listResp = api.listReleases(REPO_OWNER, REPO_NAME, perPage = 5)
        if (listResp.isSuccessful) {
          listResp.body().orEmpty().firstOrNull { !it.draft }
        } else {
          null
        }
      } else {
        return@withContext AppReleaseCheckResult.Failure("HTTP ${latestResp.code()}: ${latestResp.message()}")
      }

      if (release == null) {
        return@withContext AppReleaseCheckResult.NoReleasesPublished
      }

      val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
      val preferredApk = apkAssets.firstOrNull { it.name.contains("release", ignoreCase = true) }
        ?: apkAssets.firstOrNull()

      val isNewer = compareSemanticVersions(release.tagName, currentVersionName) > 0
      AppReleaseCheckResult.Success(
        AppReleaseInfo(
          tagName = release.tagName.ifBlank { release.name ?: currentVersionName },
          releaseName = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
          notes = release.body.orEmpty().trim(),
          htmlUrl = release.htmlUrl.ifBlank { LATEST_RELEASE_URL },
          publishedAt = release.publishedAt,
          apkAssetName = preferredApk?.name,
          apkDownloadUrl = preferredApk?.browserDownloadUrl,
          apkSizeBytes = preferredApk?.size ?: 0L,
          isNewerThanCurrent = isNewer,
        ),
      )
    } catch (t: Throwable) {
      AppReleaseCheckResult.Failure(t.message ?: "Network error while checking releases")
    }
  }

  suspend fun downloadApk(
    downloadUrl: String,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): File = withContext(Dispatchers.IO) {
    require(isAllowedApkDownloadUrl(downloadUrl)) {
      "Blocked untrusted APK download URL: must be HTTPS on github.com or githubusercontent.com"
    }

    val updatesDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
    updatesDir.listFiles()?.forEach { old -> old.delete() }

    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "qusin-update.apk" }
    val targetFile = File(updatesDir, safeName)

    val request = Request.Builder()
      .url(downloadUrl)
      .header("Accept", "application/octet-stream")
      .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")
      .build()

    downloadClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        error("HTTP ${response.code} downloading APK update")
      }
      val body = response.body ?: error("Empty response body")
      val totalBytes = body.contentLength()
      val buffer = ByteArray(64 * 1024)
      var downloaded = 0L

      body.byteStream().use { input ->
        FileOutputStream(targetFile).use { output ->
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            onProgress(downloaded, totalBytes)
          }
          output.flush()
        }
      }
    }

    // Verify ZIP/APK magic header (PK\x03\x04)
    val magic = ByteArray(4)
    val readCount = targetFile.inputStream().use { it.read(magic) }
    val validApkHeader = readCount == 4 &&
      magic[0] == 0x50.toByte() &&
      magic[1] == 0x4B.toByte() &&
      magic[2] == 0x03.toByte() &&
      magic[3] == 0x04.toByte()
    if (!validApkHeader) {
      targetFile.delete()
      error("Downloaded file is not a valid Android APK package")
    }
    targetFile
  }

  fun canInstallPackages(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun createManageUnknownSourcesIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun createInstallApkIntent(apkFile: File): Intent {
    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      apkFile,
    )
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
}
