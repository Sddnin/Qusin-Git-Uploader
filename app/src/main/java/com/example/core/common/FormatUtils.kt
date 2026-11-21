package com.example.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
      bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
      bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
      bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
      else -> "$bytes B"
    }
  }

  fun formatSpeed(bytesPerSec: Double): String {
    if (bytesPerSec <= 0.0) return "0 KB/s"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
      bytesPerSec >= mb -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / mb)
      else -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / kb)
    }
  }

  fun formatEta(seconds: Long): String {
    if (seconds <= 0L) return "--"
    val mins = seconds / 60
    val secs = seconds % 60
    val hours = mins / 60
    return when {
      hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, mins % 60)
      mins > 0 -> String.format(Locale.US, "%dm %02ds", mins, secs)
      else -> "${secs}s"
    }
  }

  fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
  }

  fun extensionOf(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot in 1 until fileName.length - 1) {
      fileName.substring(dot + 1).lowercase(Locale.ROOT)
    } else {
      ""
    }
  }

  fun mimeCategory(fileName: String): String {
    return when (extensionOf(fileName)) {
      "kt", "java", "py", "js", "ts", "tsx", "jsx", "cpp", "c", "h", "rs", "go", "swift", "dart" -> "Source Code"
      "zip", "tar", "gz", "7z", "rar" -> "Archive"
      "apk", "aab", "ipa" -> "Package"
      "png", "jpg", "jpeg", "webp", "svg", "gif" -> "Image"
      "json", "xml", "yml", "yaml", "toml", "gradle", "kts", "md", "txt", "properties" -> "Config / Doc"
      "so", "jar", "aar", "bin", "onnx", "tflite" -> "Binary / Asset"
      else -> "File"
    }
  }
}
