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

  private val PROTECTED_PROJECT_SUBDIRS = setOf(
    ".github",
    ".vscode",
    ".idea",
    "src",
    "app",
    "lib",
    "libs",
    "docs",
    "assets",
    "res",
    "test",
    "tests",
    "scripts",
    "gradle",
    "public",
  )

  /**
   * Detects when all entries inside an archive sit inside a single top-level wrapper
   * directory (e.g. GitHub "Download ZIP" creates `Repo-main/README.md`, `Repo-main/src/...`).
   * Returns null if any file is already at root, multiple top-level dirs exist,
   * or the single directory is a known project subdirectory (`.github`, `src`, `app`, etc.).
   */
  fun detectCommonRootFolder(relativePaths: List<String>): String? {
    if (relativePaths.isEmpty()) return null
    var candidate: String? = null
    for (path in relativePaths) {
      val clean = sanitizeRelativeGitPath(path) ?: return null
      val slashIdx = clean.indexOf('/')
      if (slashIdx <= 0) return null
      val root = clean.substring(0, slashIdx)
      if (root.lowercase() in PROTECTED_PROJECT_SUBDIRS) return null
      if (candidate == null) {
        candidate = root
      } else if (candidate != root) {
        return null
      }
    }
    return candidate
  }

  fun applyRootStripAndPrefix(
    relativePath: String,
    commonRootToStrip: String? = null,
    targetPrefix: String = "",
  ): String? {
    var current = sanitizeRelativeGitPath(relativePath) ?: return null
    if (!commonRootToStrip.isNullOrBlank()) {
      val prefix = "${commonRootToStrip.trim('/')}/"
      if (current.startsWith(prefix)) {
        current = current.removePrefix(prefix)
      }
    }
    val cleanTargetPrefix = targetPrefix.trim().replace('\\', '/').trim('/')
    val combined = if (cleanTargetPrefix.isEmpty()) current else "$cleanTargetPrefix/$current"
    return sanitizeRelativeGitPath(combined)
  }
}
