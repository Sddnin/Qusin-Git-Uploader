package com.example.upload

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.example.core.common.AppError
import com.example.core.common.AppErrorException
import com.example.core.common.FormatUtils
import com.example.core.security.PathSecurity
import com.example.domain.model.AppSettings
import com.example.domain.model.SelectedFileItem
import com.example.domain.model.ZipInspectionResult
import com.example.gitlfs.GitLfsClassifier
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ZipManager(private val context: Context) {

  fun inspectAndExtractSafely(
    zipUri: Uri,
    zipName: String,
    zipSizeBytes: Long,
    settings: AppSettings,
  ): ZipInspectionResult {
    return context.contentResolver.openInputStream(zipUri)?.use { stream ->
      extractFromInputStream(
        inputStream = stream,
        zipName = zipName,
        zipSizeBytes = zipSizeBytes,
        settings = settings,
      )
    } ?: throw AppErrorException(AppError.ZipExtractionFailure("Cannot open ZIP input stream"))
  }

  /**
   * Extracts a ZIP stream into an isolated temporary folder under cacheDir,
   * enforcing Zip Slip confinement, ZIP bomb thresholds, max file count,
   * duplicate-name protection, and excluded folder filtering.
   * Never deletes or modifies the original ZIP.
   */
  fun extractFromInputStream(
    inputStream: InputStream,
    zipName: String,
    zipSizeBytes: Long,
    settings: AppSettings,
  ): ZipInspectionResult {
    val maxBytes = settings.maxExtractionSizeMb.toLong() * 1024L * 1024L
    val extractRoot = File(context.cacheDir, "qusin_zip_${UUID.randomUUID().toString().take(8)}")
    if (!extractRoot.mkdirs() && !extractRoot.isDirectory) {
      throw AppErrorException(AppError.StorageFull("Unable to create temp extraction folder"))
    }

    // Check available space before extracting when filesystem reports capacity
    val hasInsufficientSpace = try {
      val stat = StatFs(context.cacheDir.path)
      stat.totalBytes > 0L && stat.availableBytes < zipSizeBytes
    } catch (_: Exception) {
      false
    }
    if (hasInsufficientSpace) {
      throw AppErrorException(AppError.StorageFull("Available cache space is below archive size ($zipSizeBytes bytes)"))
    }

    var totalExtracted = 0L
    var fileCount = 0
    var skippedExcluded = 0
    var duplicateCount = 0
    val seenPaths = mutableSetOf<String>()
    val entries = mutableListOf<SelectedFileItem>()
    val buffer = ByteArray(32 * 1024)

    try {
      ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
        while (true) {
          val entry = zis.nextEntry ?: break
          val rawName = entry.name

          // 1. Zip Slip & path traversal verification
          val targetFile = PathSecurity.resolveSafeExtractTarget(extractRoot, rawName)
            ?: throw AppErrorException(
              AppError.ZipExtractionFailure("Blocked dangerous path (Zip Slip): $rawName"),
            )

          if (entry.isDirectory) {
            zis.closeEntry()
            continue
          }

          val relPath = PathSecurity.sanitizeRelativeGitPath(rawName)
          if (relPath == null) {
            skippedExcluded++
            zis.closeEntry()
            continue
          }

          // 2. Skip excluded folders (node_modules, .git, build, etc.)
          if (PathSecurity.isExcludedPath(relPath, settings.excludedFolders)) {
            skippedExcluded++
            zis.closeEntry()
            continue
          }

          // 3. Enforce max file count
          fileCount++
          if (fileCount > MAX_EXTRACT_FILE_COUNT) {
            throw AppErrorException(
              AppError.ZipExtractionFailure("Archive exceeds safety limit of $MAX_EXTRACT_FILE_COUNT files (ZIP bomb guard)"),
            )
          }

          // 4. Handle duplicate entry names cleanly without silent crash
          val finalRelPath: String
          val finalTarget: File
          if (!seenPaths.add(relPath)) {
            duplicateCount++
            val dot = relPath.lastIndexOf('.')
            finalRelPath = if (dot > 0) {
              "${relPath.substring(0, dot)}_dup$duplicateCount${relPath.substring(dot)}"
            } else {
              "${relPath}_dup$duplicateCount"
            }
            finalTarget = PathSecurity.resolveSafeExtractTarget(extractRoot, finalRelPath)
              ?: throw AppErrorException(AppError.ZipExtractionFailure("Invalid resolved path: $finalRelPath"))
          } else {
            finalRelPath = relPath
            finalTarget = targetFile
          }

          finalTarget.parentFile?.mkdirs()

          var entryWritten = 0L
          FileOutputStream(finalTarget).use { fos ->
            while (true) {
              val read = zis.read(buffer)
              if (read == -1) break
              totalExtracted += read
              entryWritten += read

              // 5. Enforce total extraction size & ZIP bomb ratio
              if (totalExtracted > maxBytes) {
                throw AppErrorException(
                  AppError.ZipExtractionFailure("Extracted size exceeds ${settings.maxExtractionSizeMb} MB limit (ZIP bomb guard)"),
                )
              }
              if (zipSizeBytes > 0L && totalExtracted > zipSizeBytes * MAX_COMPRESSION_RATIO && totalExtracted > MIN_BOMB_BYTES_CHECK) {
                throw AppErrorException(
                  AppError.ZipExtractionFailure("Abnormal compression ratio detected (possible ZIP bomb)"),
                )
              }
              fos.write(buffer, 0, read)
            }
          }

          val nameOnly = finalTarget.name
          val useLfs = GitLfsClassifier.shouldDefaultUseLfs(entryWritten, settings.lfsThresholdMb, settings.lfsEnabled)
          entries.add(
            SelectedFileItem(
              id = finalTarget.absolutePath,
              name = nameOnly,
              relativePath = finalRelPath,
              extension = FormatUtils.extensionOf(nameOnly),
              fileType = FormatUtils.mimeCategory(nameOnly),
              sizeBytes = entryWritten,
              localTempPath = finalTarget.absolutePath,
              isSelected = true,
              useLfs = useLfs,
              lfsClassification = GitLfsClassifier.classify(entryWritten),
            ),
          )
          zis.closeEntry()
        }
      }
    } catch (e: AppErrorException) {
      extractRoot.deleteRecursively()
      throw e
    } catch (t: Throwable) {
      extractRoot.deleteRecursively()
      val msg = t.message.orEmpty()
      if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space", ignoreCase = true)) {
        throw AppErrorException(AppError.StorageFull(msg))
      }
      throw AppErrorException(AppError.ZipExtractionFailure(msg.ifBlank { t.javaClass.simpleName }))
    }

    val commonRoot = PathSecurity.detectCommonRootFolder(entries.map { it.relativePath })

    return ZipInspectionResult(
      zipName = zipName,
      zipSizeBytes = zipSizeBytes,
      extractedDirPath = extractRoot.absolutePath,
      entries = entries,
      commonRootFolder = commonRoot,
      skippedExcludedCount = skippedExcluded,
      duplicateCount = duplicateCount,
      totalExtractedBytes = totalExtracted,
    )
  }

  fun calculateCacheBytes(): Long {
    val dirs = context.cacheDir.listFiles()?.filter { it.name.startsWith("qusin_") } ?: return 0L
    return dirs.sumOf { dirSize(it) }
  }

  fun clearTempCache(): Long {
    val freed = calculateCacheBytes()
    context.cacheDir.listFiles()?.filter { it.name.startsWith("qusin_") }?.forEach {
      it.deleteRecursively()
    }
    return freed
  }

  private fun dirSize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
  }

  companion object {
    const val MAX_EXTRACT_FILE_COUNT = 15_000
    const val MAX_COMPRESSION_RATIO = 100L
    const val MIN_BOMB_BYTES_CHECK = 20L * 1024L * 1024L
  }
}

fun openStreamForItem(context: Context, item: SelectedFileItem): InputStream {
  return when {
    item.localTempPath != null -> FileInputStream(File(item.localTempPath))
    item.sourceUri != null -> context.contentResolver.openInputStream(item.sourceUri)
      ?: throw AppErrorException(AppError.Http404("Unable to open SAF URI: ${item.relativePath}"))
    else -> throw AppErrorException(AppError.Http400("No file source for ${item.relativePath}"))
  }
}
