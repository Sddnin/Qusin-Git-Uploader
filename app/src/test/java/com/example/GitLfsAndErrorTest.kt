package com.example

import com.example.core.common.AppError
import com.example.core.security.PathSecurity
import com.example.domain.model.LfsClassification
import com.example.gitlfs.GitAttributesHelper
import com.example.gitlfs.GitLfsClassifier
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitLfsAndErrorTest {

  @Test
  fun `classify 10MB, 52MB, 99MB, 150MB, and 500MB files accurately`() {
    val mb = 1024L * 1024L
    assertEquals(LfsClassification.STANDARD_GIT, GitLfsClassifier.classify(10L * mb))
    assertFalse(GitLfsClassifier.shouldDefaultUseLfs(10L * mb, thresholdMb = 50, lfsEnabled = true))

    assertEquals(LfsClassification.LFS_RECOMMENDED, GitLfsClassifier.classify(52L * mb))
    assertTrue(GitLfsClassifier.shouldDefaultUseLfs(52L * mb, thresholdMb = 50, lfsEnabled = true))

    assertEquals(LfsClassification.LFS_RECOMMENDED, GitLfsClassifier.classify(99L * mb))
    assertTrue(GitLfsClassifier.shouldDefaultUseLfs(99L * mb, thresholdMb = 50, lfsEnabled = true))

    assertEquals(LfsClassification.LFS_REQUIRED, GitLfsClassifier.classify(150L * mb))
    // >= 100MB forces LFS even when optional LFS toggle is off
    assertTrue(GitLfsClassifier.shouldDefaultUseLfs(150L * mb, thresholdMb = 50, lfsEnabled = false))

    assertEquals(LfsClassification.LFS_REQUIRED, GitLfsClassifier.classify(500L * mb))
  }

  @Test
  fun `pointer format and gitattributes merge follow Git LFS v1 spec`() {
    val oid = "4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393"
    val pointer = GitAttributesHelper.buildPointerText(oid, 157286400L)
    assertTrue(pointer.contains("version https://git-lfs.github.com/spec/v1"))
    assertTrue(pointer.contains("oid sha256:$oid"))
    assertTrue(pointer.contains("size 157286400"))

    val merged = GitAttributesHelper.mergeGitAttributes(
      existingContent = "*.png filter=lfs diff=lfs merge=lfs -text\n",
      lfsRelativePaths = listOf("releases/app release.apk", "*.png"),
    )
    assertTrue(merged.contains("releases/app\\ release.apk filter=lfs diff=lfs merge=lfs -text"))
    assertTrue(merged.contains("*.png filter=lfs diff=lfs merge=lfs -text"))
  }

  @Test
  fun `path security blocks zip slip and internal git directory`() {
    assertNull(PathSecurity.sanitizeRelativeGitPath("../secret.txt"))
    assertNull(PathSecurity.sanitizeRelativeGitPath(".git/config"))
    assertNull(PathSecurity.sanitizeRelativeGitPath("foo/\u0000bar"))
    assertEquals("app/src/Main.kt", PathSecurity.sanitizeRelativeGitPath("/app\\src/Main.kt"))
  }

  @Test
  fun `error mapper translates HTTP, network, LFS quota, and storage errors`() {
    assertTrue(AppError.fromHttpStatus(401) is AppError.Http401)
    assertTrue(AppError.fromHttpStatus(403) is AppError.Http403)
    assertTrue(AppError.fromHttpStatus(409) is AppError.Http409)
    assertTrue(AppError.fromHttpStatus(507, "LFS data quota exceeded") is AppError.LfsQuotaExceeded)
    assertTrue(AppError.fromHttpStatus(403, "LFS bandwidth quota reached") is AppError.LfsQuotaExceeded)
    assertTrue(AppError.fromThrowable(SocketTimeoutException("timeout")) is AppError.NetworkTimeout)
    assertTrue(AppError.fromThrowable(UnknownHostException("api.github.com")) is AppError.DnsFailure)
    assertTrue(AppError.fromThrowable(IOException("write failed: ENOSPC (No space left on device)")) is AppError.StorageFull)
  }
}
