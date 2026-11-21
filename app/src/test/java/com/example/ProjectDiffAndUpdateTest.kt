package com.example

import com.example.core.common.AppError
import com.example.core.security.TokenStore
import com.example.domain.model.DiffStatus
import com.example.git.GitEngine
import com.example.git.ProjectDiffEngine
import com.example.git.RawTreeEntry
import com.example.gitlfs.GitAttributesHelper
import com.example.gitlfs.GitLfsEngine
import com.example.github.api.GitHubApiService
import com.example.github.dto.CreateTreeRequestDto
import com.example.github.dto.GitTreeEntryRequestDto
import com.example.github.dto.GitTreeNodeDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Proxy
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDiffAndUpdateTest {

  @Test
  fun `detects single file modified, multi-file added, new folder, deleted, and unchanged by hash`() {
    val local = mapOf(
      "README.md" to (120L to "sha_readme_same"),
      "app/Main.kt" to (450L to "sha_main_v2"),
      "feature/new_dir/Screen.kt" to (980L to "sha_new_screen"),
    )
    val remote = mapOf(
      "README.md" to (120L to "sha_readme_same"),
      "app/Main.kt" to (450L to "sha_main_v1"), // same size, different hash -> MODIFIED!
      "legacy/Old.kt" to (300L to "sha_old"),
    )

    val diff = ProjectDiffEngine.classifyHashes(localEntries = local, remoteEntries = remote)
    val byPath = diff.associateBy { it.relativePath }

    assertEquals(DiffStatus.UNCHANGED, byPath["README.md"]?.status)
    assertFalse(byPath["README.md"]?.selectedForApply == true)

    assertEquals(DiffStatus.MODIFIED, byPath["app/Main.kt"]?.status)
    assertTrue(byPath["app/Main.kt"]?.selectedForApply == true)

    assertEquals(DiffStatus.ADDED, byPath["feature/new_dir/Screen.kt"]?.status)
    assertTrue(byPath["feature/new_dir/Screen.kt"]?.selectedForApply == true)

    assertEquals(DiffStatus.DELETED, byPath["legacy/Old.kt"]?.status)
  }

  @Test
  fun `scoped target prefix preserves files outside target directory`() {
    val local = mapOf(
      "Main.kt" to (100L to "sha1"),
    )
    val remote = mapOf(
      ".github/workflows/android-build.yml" to (900L to "sha_ci"),
      "app/src/Main.kt" to (100L to "sha1"),
      "app/src/Removed.kt" to (200L to "sha_rem"),
    )

    val diff = ProjectDiffEngine.classifyHashes(
      localEntries = local,
      remoteEntries = remote,
      targetPrefix = "app/src",
    )
    val byPath = diff.associateBy { it.relativePath }

    assertNull(byPath[".github/workflows/android-build.yml"])
    assertEquals(DiffStatus.UNCHANGED, byPath["app/src/Main.kt"]?.status)
    assertEquals(DiffStatus.DELETED, byPath["app/src/Removed.kt"]?.status)
  }

  @Test
  fun `parses LFS pointer OID to distinguish unchanged vs modified large files`() {
    val oid1 = "a".repeat(64)
    val oid2 = "b".repeat(64)
    val pointer = GitAttributesHelper.buildPointerText(oid1, 157_286_400L)

    assertEquals(oid1, ProjectDiffEngine.parseLfsOidFromPointer(pointer))

    val diffSame = ProjectDiffEngine.classifyHashes(
      localEntries = mapOf("dist/app.apk" to (157_286_400L to oid1)),
      remoteEntries = mapOf("dist/app.apk" to (134L to oid1)),
    )
    assertEquals(DiffStatus.UNCHANGED, diffSame.single().status)

    val diffUpdated = ProjectDiffEngine.classifyHashes(
      localEntries = mapOf("dist/app.apk" to (158_000_000L to oid2)),
      remoteEntries = mapOf("dist/app.apk" to (134L to oid1)),
    )
    assertEquals(DiffStatus.MODIFIED, diffUpdated.single().status)
  }

  @Test
  fun `serializeNulls outputs explicit sha null for Git Data API deletions`() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val json = moshi.adapter(CreateTreeRequestDto::class.java).serializeNulls().toJson(
      CreateTreeRequestDto(
        baseTree = "base123",
        tree = listOf(
          GitTreeEntryRequestDto(path = "deleted/OldFile.kt", sha = null),
          GitTreeEntryRequestDto(path = "added/NewFile.kt", sha = "blob456"),
        ),
      ),
    )

    assertTrue(json.contains("\"sha\":null"))
    assertTrue(json.contains("\"sha\":\"blob456\""))
  }

  @Test
  fun `rollback tree delta preview identifies restored and removed files`() {
    val currentTree = listOf(
      GitTreeNodeDto(path = "app/A.kt", sha = "sha_a_v2", size = 100),
      GitTreeNodeDto(path = "app/AddedInV2.kt", sha = "sha_new", size = 50),
    )
    val previousTree = listOf(
      GitTreeNodeDto(path = "app/A.kt", sha = "sha_a_v1", size = 90),
    )

    val preview = ProjectDiffEngine.computeTreeDeltaPreview(currentTree, previousTree)
    val byPath = preview.associateBy { it.relativePath }

    assertEquals(DiffStatus.MODIFIED, byPath["app/A.kt"]?.status)
    assertEquals(DiffStatus.DELETED, byPath["app/AddedInV2.kt"]?.status)
    assertTrue(AppError.fromHttpStatus(409) is AppError.Http409)
  }

  @Test
  fun `raw tree serializer mixes inline content, blob sha, and sha null deletions safely`() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val fakeApi = Proxy.newProxyInstance(
      GitHubApiService::class.java.classLoader,
      arrayOf(GitHubApiService::class.java),
    ) { _, _, _ -> throw UnsupportedOperationException() } as GitHubApiService
    val fakeTokenStore = object : TokenStore {
      override fun saveToken(token: String) {}
      override fun getToken(): String? = null
      override fun clearToken() {}
      override fun hasToken(): Boolean = false
    }
    val engine = GitEngine(
      api = fakeApi,
      lfsEngine = GitLfsEngine(OkHttpClient(), moshi, fakeTokenStore),
      moshi = moshi,
    )

    val json = engine.serializeRawTree(
      baseTree = "baseTreeSha",
      entries = listOf(
        RawTreeEntry(path = "src/Main.kt", inlineContent = "fun main() {}"),
        RawTreeEntry(path = "assets/icon.png", sha = "blobSha123"),
        RawTreeEntry(path = "old/Removed.kt", isDeletion = true),
      ),
    )

    assertTrue(json.contains("\"base_tree\":\"baseTreeSha\""))
    assertTrue(json.contains("{\"path\":\"src/Main.kt\",\"mode\":\"100644\",\"type\":\"blob\",\"content\":\"fun main() {}\"}"))
    assertTrue(json.contains("{\"path\":\"assets/icon.png\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":\"blobSha123\"}"))
    assertTrue(json.contains("{\"path\":\"old/Removed.kt\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":null}"))

    assertNotNull(engine.tryDecodeExactUtf8("Xin chào GitHub 🚀\n".toByteArray()))
    assertNull(engine.tryDecodeExactUtf8(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x14)))
    assertNull(engine.tryDecodeExactUtf8(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)))

    val manyEntries = (1..450).map { idx ->
      RawTreeEntry(path = "src/File$idx.kt", inlineContent = "val x = $idx", originalSizeBytes = 12L)
    }
    val chunks = engine.chunkTreeEntries(manyEntries)
    assertEquals(3, chunks.size)
    assertEquals(450, chunks.sumOf { it.size })
  }
}
