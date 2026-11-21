package com.example.upload

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.core.common.FormatUtils
import com.example.core.security.PathSecurity
import com.example.domain.model.AppSettings
import com.example.domain.model.SelectedFileItem
import com.example.gitlfs.GitLfsClassifier
import java.util.UUID

class SafFileScanner(private val context: Context) {
  private val resolver: ContentResolver = context.contentResolver

  fun takePersistableReadPermission(uri: Uri) {
    try {
      resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      // Transient URI grants from OpenDocument still work within active lifecycle/worker
    }
  }

  fun scanSingleOrMultipleUris(
    uris: List<Uri>,
    settings: AppSettings,
  ): List<SelectedFileItem> {
    val result = mutableListOf<SelectedFileItem>()
    for (uri in uris) {
      takePersistableReadPermission(uri)
      val (name, size) = queryDisplayNameAndSize(uri)
      val safeName = name.ifBlank { "file_${UUID.randomUUID().toString().take(6)}" }
      val ext = FormatUtils.extensionOf(safeName)
      val useLfs = GitLfsClassifier.shouldDefaultUseLfs(size, settings.lfsThresholdMb, settings.lfsEnabled)
      result.add(
        SelectedFileItem(
          id = uri.toString(),
          name = safeName,
          relativePath = safeName,
          extension = ext,
          fileType = FormatUtils.mimeCategory(safeName),
          sizeBytes = size,
          sourceUri = uri,
          isSelected = true,
          useLfs = useLfs,
          lfsClassification = GitLfsClassifier.classify(size),
        ),
      )
    }
    return result
  }

  fun scanTreeUri(
    treeUri: Uri,
    settings: AppSettings,
  ): Pair<String, List<SelectedFileItem>> {
    takePersistableReadPermission(treeUri)
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootName = queryDocumentName(treeUri, rootDocId).ifBlank { "project" }
    val items = mutableListOf<SelectedFileItem>()

    traverseTree(
      treeUri = treeUri,
      parentDocId = rootDocId,
      relativePrefix = "",
      settings = settings,
      outItems = items,
    )
    return rootName to items
  }

  private fun traverseTree(
    treeUri: Uri,
    parentDocId: String,
    relativePrefix: String,
    settings: AppSettings,
    outItems: MutableList<SelectedFileItem>,
  ) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
    )

    resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
      val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

      while (cursor.moveToNext()) {
        val docId = cursor.getString(idCol) ?: continue
        val displayName = cursor.getString(nameCol) ?: continue
        val mimeType = cursor.getString(mimeCol) ?: ""
        val size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

        val relPath = if (relativePrefix.isEmpty()) displayName else "$relativePrefix/$displayName"
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
          val isExcluded = settings.excludedFolders.any { it.equals(displayName, ignoreCase = true) }
          if (!isExcluded) {
            traverseTree(treeUri, docId, relPath, settings, outItems)
          }
        } else {
          val sanitized = PathSecurity.sanitizeRelativeGitPath(relPath) ?: continue
          if (PathSecurity.isExcludedPath(sanitized, settings.excludedFolders)) continue
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
          val actualSize = if (size > 0L) size else queryDisplayNameAndSize(docUri).second
          val ext = FormatUtils.extensionOf(displayName)
          val useLfs = GitLfsClassifier.shouldDefaultUseLfs(actualSize, settings.lfsThresholdMb, settings.lfsEnabled)
          outItems.add(
            SelectedFileItem(
              id = docUri.toString(),
              name = displayName,
              relativePath = sanitized,
              extension = ext,
              fileType = FormatUtils.mimeCategory(displayName),
              sizeBytes = actualSize,
              sourceUri = docUri,
              isSelected = true,
              useLfs = useLfs,
              lfsClassification = GitLfsClassifier.classify(actualSize),
            ),
          )
        }
      }
    }
  }

  private fun queryDocumentName(treeUri: Uri, docId: String): String {
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    return queryDisplayNameAndSize(docUri).first
  }

  fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
    var name = ""
    var size = 0L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
        if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
        if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
      }
    }
    if (size <= 0L) {
      try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
          if (afd.length > 0L) size = afd.length
        }
      } catch (_: Exception) {
      }
    }
    return name to size
  }
}
