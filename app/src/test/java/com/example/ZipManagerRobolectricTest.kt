package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.AppError
import com.example.core.common.AppErrorException
import com.example.domain.model.AppSettings
import com.example.upload.ZipManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZipManagerRobolectricTest {

  private fun buildZipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      for ((name, content) in entries) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content)
        zos.closeEntry()
      }
    }
    return baos.toByteArray()
  }

  @Test
  fun `extracts nested folders, skips excluded dirs, and renames duplicate entries`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val zipManager = ZipManager(context)
    val bytes = buildZipBytes(
      listOf(
        "src/main/App.kt" to "fun main() {}".toByteArray(),
        "/src/main/App.kt" to "fun mainV2() {}".toByteArray(),
        "node_modules/pkg/index.js" to "console.log(1)".toByteArray(),
        ".git/HEAD" to "ref: refs/heads/main".toByteArray(),
      ),
    )

    val result = zipManager.extractFromInputStream(
      inputStream = ByteArrayInputStream(bytes),
      zipName = "project.zip",
      zipSizeBytes = bytes.size.toLong(),
      settings = AppSettings(),
    )

    assertEquals(2, result.entries.size)
    assertEquals(2, result.skippedExcludedCount)
    assertEquals(1, result.duplicateCount)
    assertTrue(result.entries.any { it.relativePath == "src/main/App.kt" })
    assertTrue(result.entries.any { it.relativePath == "src/main/App_dup1.kt" })
    zipManager.clearTempCache()
  }

  @Test
  fun `blocks Zip Slip traversal entries immediately`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val zipManager = ZipManager(context)
    val bytes = buildZipBytes(
      listOf("../../evil.sh" to "rm -rf /".toByteArray()),
    )

    try {
      zipManager.extractFromInputStream(
        inputStream = ByteArrayInputStream(bytes),
        zipName = "evil.zip",
        zipSizeBytes = bytes.size.toLong(),
        settings = AppSettings(),
      )
      fail("Expected AppErrorException for Zip Slip")
    } catch (e: AppErrorException) {
      assertTrue(e.error is AppError.ZipExtractionFailure)
    }
  }
}
