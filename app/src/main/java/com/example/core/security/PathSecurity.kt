package com.example.core.security

import java.io.File

object PathSecurity {
  fun sanitizeRelativeGitPath(rawPath: String): String? {
    if (rawPath.contains('\u0000')) return null
    val normalized = rawPath.replace('\\', '/').trimStart('/')
    if (normalized.isBlank()) return null

    val segments = normalized.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    if (segments.any { it == "." || it == ".." }) return null
    // Block tampering inside internal .git metadata directory
    if (segments.first().equals(".git", ignoreCase = true)) return null
    return segments.joinToString("/")
  }

  fun resolveSafeExtractTarget(baseDir: File, entryName: String): File? {
    if (entryName.contains('\u0000')) return null
    val cleanEntry = entryName.replace('\\', '/').trimStart('/')
    if (cleanEntry.isBlank() || cleanEntry.startsWith("/") || cleanEntry.contains("..")) {
      return null
    }
    val baseCanonical = baseDir.canonicalFile
    val targetFile = File(baseCanonical, cleanEntry)
    val targetCanonical = targetFile.canonicalFile
    val basePrefix = baseCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
    if (!targetCanonical.path.startsWith(basePrefix)) {
      return null
    }
    return targetCanonical
  }

  fun isExcludedPath(relativePath: String, excludedFolders: Set<String>): Boolean {
    if (excludedFolders.isEmpty()) return false
    val normalized = relativePath.replace('\\', '/')
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    // Check directory segments (exclude last segment if it's a file, unless directory itself)
    val dirSegments = if (segments.size > 1) segments.dropLast(1) else emptyList()
    return dirSegments.any { seg -> excludedFolders.any { ex -> ex.equals(seg, ignoreCase = true) } }
  }
}
