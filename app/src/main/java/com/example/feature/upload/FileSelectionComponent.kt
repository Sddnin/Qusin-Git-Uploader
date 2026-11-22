package com.example.feature.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.AppStrings
import com.example.core.common.FormatUtils
import com.example.core.designsystem.CyberBadge
import com.example.core.designsystem.GlassCard
import com.example.core.designsystem.LocalCyberPalette

enum class FilePickerCategory {
  SOURCE_CODE,
  ARCHIVES,
  APK_BUILDS,
  PROJECT_FOLDER,
  ANY_FILES,
}

object DeviceFilePickerSpecs {
  val sourceCodeMimeTypes: Array<String> = arrayOf(
    "text/*",
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-javascript",
    "application/x-sh",
    "application/octet-stream",
  )

  val archiveMimeTypes: Array<String> = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/gzip",
    "application/x-gzip",
    "application/x-tar",
    "application/x-7z-compressed",
    "application/vnd.rar",
    "application/x-rar-compressed",
    "application/octet-stream",
  )

  val apkMimeTypes: Array<String> = arrayOf(
    "application/vnd.android.package-archive",
    "application/octet-stream",
  )

  val anyFileMimeTypes: Array<String> = arrayOf("*/*")

  fun mimeTypesFor(category: FilePickerCategory): Array<String> = when (category) {
    FilePickerCategory.SOURCE_CODE -> sourceCodeMimeTypes
    FilePickerCategory.ARCHIVES -> archiveMimeTypes
    FilePickerCategory.APK_BUILDS -> apkMimeTypes
    FilePickerCategory.PROJECT_FOLDER -> emptyArray()
    FilePickerCategory.ANY_FILES -> anyFileMimeTypes
  }

  fun extensionHintsFor(category: FilePickerCategory): String = when (category) {
    FilePickerCategory.SOURCE_CODE -> ".kt, .java, .py, .js, .ts, .go, .rs, .json, .xml, .md"
    FilePickerCategory.ARCHIVES -> ".zip, .tar.gz, .7z, .rar"
    FilePickerCategory.APK_BUILDS -> ".apk, .aab"
    FilePickerCategory.PROJECT_FOLDER -> "SAF Directory Tree"
    FilePickerCategory.ANY_FILES -> "*/*"
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceFileSelectionComponent(
  selectedCategory: FilePickerCategory,
  onSelectCategory: (FilePickerCategory) -> Unit,
  onFilesPicked: (List<Uri>) -> Unit,
  onArchivePicked: (Uri) -> Unit,
  onFolderPicked: (Uri) -> Unit,
  isScanning: Boolean,
  stagedFileCount: Int,
  stagedTotalBytes: Long,
  stagedLfsCount: Int,
  strings: AppStrings,
  modifier: Modifier = Modifier,
) {
  val palette = LocalCyberPalette.current

  val multiDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) {
      onFilesPicked(uris)
    }
  }

  val singleDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      onFilesPicked(listOf(uri))
    }
  }

  val archiveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      onArchivePicked(uri)
    }
  }

  val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      onFolderPicked(uri)
    }
  }

  fun launchForCategory(category: FilePickerCategory) {
    onSelectCategory(category)
    when (category) {
      FilePickerCategory.SOURCE_CODE -> multiDocLauncher.launch(DeviceFilePickerSpecs.sourceCodeMimeTypes)
      FilePickerCategory.ARCHIVES -> archiveLauncher.launch(DeviceFilePickerSpecs.archiveMimeTypes)
      FilePickerCategory.APK_BUILDS -> multiDocLauncher.launch(DeviceFilePickerSpecs.apkMimeTypes)
      FilePickerCategory.PROJECT_FOLDER -> folderLauncher.launch(null)
      FilePickerCategory.ANY_FILES -> multiDocLauncher.launch(DeviceFilePickerSpecs.anyFileMimeTypes)
    }
  }

  GlassCard(modifier = modifier.fillMaxWidth()) {
    Text(strings.uploadWorkbench, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
      text = strings.step1SelectSource,
      style = MaterialTheme.typography.titleMedium,
      color = palette.cyanGlow,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = strings.filePickerSubtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    // Category selector pills
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val categories = listOf(
        Triple(FilePickerCategory.SOURCE_CODE, strings.pickerCatSourceCode, Icons.Rounded.Code to "picker_category_source"),
        Triple(FilePickerCategory.ARCHIVES, strings.pickerCatArchives, Icons.Rounded.Archive to "picker_category_archive"),
        Triple(FilePickerCategory.APK_BUILDS, strings.pickerCatApk, Icons.Rounded.Android to "picker_category_apk"),
        Triple(FilePickerCategory.PROJECT_FOLDER, strings.pickerCatFolder, Icons.Rounded.CreateNewFolder to "picker_category_folder"),
        Triple(FilePickerCategory.ANY_FILES, strings.pickerCatAny, Icons.Rounded.InsertDriveFile to "picker_category_any"),
      )

      categories.forEach { (category, label, iconAndTag) ->
        val (icon, tag) = iconAndTag
        val isSelected = selectedCategory == category
        FilterChip(
          selected = isSelected,
          onClick = { onSelectCategory(category) },
          leadingIcon = {
            Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.cyanGlow.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = palette.cyanGlow,
          ),
          modifier = Modifier.testTag(tag),
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    // Active category info strip
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = categoryDescription(selectedCategory, strings),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = DeviceFilePickerSpecs.extensionHintsFor(selectedCategory),
          style = MaterialTheme.typography.labelSmall,
          color = palette.cyanGlow,
        )
      }
    }

    Spacer(Modifier.height(10.dp))

    // Primary SAF Launcher CTA for active category
    Button(
      onClick = { launchForCategory(selectedCategory) },
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("device_file_picker_launch_btn"),
      colors = ButtonDefaults.buttonColors(
        containerColor = palette.cyanGlow,
        contentColor = Color(0xFF08111E),
      ),
    ) {
      Icon(
        imageVector = categoryIcon(selectedCategory),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = primaryPickerActionLabel(selectedCategory, strings),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(Modifier.height(10.dp))

    // 1-tap direct SAF shortcuts (preserves rapid workflows across code, archives, APKs, folders)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { launchForCategory(FilePickerCategory.PROJECT_FOLDER) },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_folder_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickProjectFolder, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      OutlinedButton(
        onClick = { launchForCategory(FilePickerCategory.ARCHIVES) },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_zip_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickZipArchive, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }

    Spacer(Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { launchForCategory(FilePickerCategory.APK_BUILDS) },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_apk_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickApkFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      OutlinedButton(
        onClick = {
          onSelectCategory(FilePickerCategory.SOURCE_CODE)
          multiDocLauncher.launch(DeviceFilePickerSpecs.sourceCodeMimeTypes)
        },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_source_code_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickSourceCodeFiles, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }

    Spacer(Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = {
          onSelectCategory(FilePickerCategory.ANY_FILES)
          singleDocLauncher.launch(DeviceFilePickerSpecs.anyFileMimeTypes)
        },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_single_file_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickSingleFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      OutlinedButton(
        onClick = { launchForCategory(FilePickerCategory.ANY_FILES) },
        modifier = Modifier
          .weight(1f)
          .testTag("pick_multi_file_btn"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.pickMultipleFiles, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }

    if (stagedFileCount > 0) {
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CyberBadge(
          text = "$stagedFileCount files • ${FormatUtils.formatBytes(stagedTotalBytes)}",
          color = palette.successAccent,
        )
        if (stagedLfsCount > 0) {
          CyberBadge(
            text = "LFS: $stagedLfsCount",
            color = palette.warningAccent,
          )
        }
      }
    }

    if (isScanning) {
      Spacer(Modifier.height(10.dp))
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}

private fun categoryIcon(category: FilePickerCategory): ImageVector = when (category) {
  FilePickerCategory.SOURCE_CODE -> Icons.Rounded.Code
  FilePickerCategory.ARCHIVES -> Icons.Rounded.Archive
  FilePickerCategory.APK_BUILDS -> Icons.Rounded.Android
  FilePickerCategory.PROJECT_FOLDER -> Icons.Rounded.CreateNewFolder
  FilePickerCategory.ANY_FILES -> Icons.Rounded.UploadFile
}

private fun categoryDescription(category: FilePickerCategory, strings: AppStrings): String = when (category) {
  FilePickerCategory.SOURCE_CODE -> strings.pickerDescSourceCode
  FilePickerCategory.ARCHIVES -> strings.pickerDescArchives
  FilePickerCategory.APK_BUILDS -> strings.pickerDescApk
  FilePickerCategory.PROJECT_FOLDER -> strings.pickerDescFolder
  FilePickerCategory.ANY_FILES -> strings.pickerDescAny
}

private fun primaryPickerActionLabel(category: FilePickerCategory, strings: AppStrings): String = when (category) {
  FilePickerCategory.SOURCE_CODE -> strings.pickSourceCodeFiles
  FilePickerCategory.ARCHIVES -> strings.pickZipArchive
  FilePickerCategory.APK_BUILDS -> strings.pickApkFile
  FilePickerCategory.PROJECT_FOLDER -> strings.pickProjectFolder
  FilePickerCategory.ANY_FILES -> strings.pickMultipleFiles
}
