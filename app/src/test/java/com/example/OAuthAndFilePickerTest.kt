package com.example

import com.example.core.common.AppError
import com.example.core.security.GitHubOAuthManager
import com.example.core.security.OAuthCallbackValidation
import com.example.core.security.PendingOAuthSession
import com.example.feature.upload.DeviceFilePickerSpecs
import com.example.feature.upload.FilePickerCategory
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthAndFilePickerTest {

  @Test
  fun `PKCE S256 challenge matches RFC 7636 SHA-256 base64url digest`() {
    val verifier = GitHubOAuthManager.generateCodeVerifier()
    val challenge = GitHubOAuthManager.computeCodeChallengeS256(verifier)
    val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
      MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
    )
    assertEquals(expected, challenge)
    assertTrue(!challenge.contains("="))
    assertTrue(!challenge.contains("+"))
    assertTrue(!challenge.contains("/"))
  }

  @Test
  fun `authorization URL includes PKCE S256 challenge, scopes, and redirect URI`() {
    val state = GitHubOAuthManager.generateState()
    val challenge = GitHubOAuthManager.computeCodeChallengeS256("test_verifier_12345")
    val url = GitHubOAuthManager.buildAuthorizationUrlString(
      clientId = "Ov23liTestClient",
      state = state,
      codeChallenge = challenge,
    )
    assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"))
    assertTrue(url.contains("client_id=Ov23liTestClient"))
    assertTrue(url.contains("redirect_uri=qusin%3A%2F%2Foauth%2Fcallback"))
    assertTrue(url.contains("code_challenge=$challenge"))
    assertTrue(url.contains("code_challenge_method=S256"))
    assertTrue(url.contains("state=$state"))
  }

  @Test
  fun `callback validation accepts matching state and rejects mismatch or expiry`() {
    val now = 1_700_000_000_000L
    val pending = PendingOAuthSession(
      state = "csrf_state_token_abc",
      codeVerifier = "pkce_verifier_xyz",
      clientId = "Ov23liTestClient",
      createdAtMillis = now - 10_000L,
    )

    val ok = GitHubOAuthManager.validateCallbackParameters(
      error = null,
      errorDescription = null,
      code = "gh_auth_code_999",
      returnedState = "csrf_state_token_abc",
      pending = pending,
      nowMillis = now,
    )
    assertTrue(ok is OAuthCallbackValidation.Authorized)
    val authorized = ok as OAuthCallbackValidation.Authorized
    assertEquals("gh_auth_code_999", authorized.code)
    assertEquals("pkce_verifier_xyz", authorized.codeVerifier)
    assertEquals("Ov23liTestClient", authorized.clientId)

    val mismatch = GitHubOAuthManager.validateCallbackParameters(
      error = null,
      errorDescription = null,
      code = "gh_auth_code_999",
      returnedState = "tampered_state",
      pending = pending,
      nowMillis = now,
    )
    assertTrue(mismatch is OAuthCallbackValidation.Failed)
    assertTrue((mismatch as OAuthCallbackValidation.Failed).error is AppError.Http401)

    val expired = GitHubOAuthManager.validateCallbackParameters(
      error = null,
      errorDescription = null,
      code = "gh_auth_code_999",
      returnedState = "csrf_state_token_abc",
      pending = pending.copy(createdAtMillis = now - (20 * 60 * 1000L)),
      nowMillis = now,
    )
    assertTrue(expired is OAuthCallbackValidation.Failed)

    val denied = GitHubOAuthManager.validateCallbackParameters(
      error = "access_denied",
      errorDescription = "User cancelled login",
      code = null,
      returnedState = null,
      pending = pending,
      nowMillis = now,
    )
    assertTrue(denied is OAuthCallbackValidation.Failed)
  }

  @Test
  fun `file picker MIME specs cover source code, archives, and APKs cleanly`() {
    val sourceMimes = DeviceFilePickerSpecs.mimeTypesFor(FilePickerCategory.SOURCE_CODE).toSet()
    assertTrue("text/*" in sourceMimes)
    assertTrue("application/json" in sourceMimes)

    val archiveMimes = DeviceFilePickerSpecs.mimeTypesFor(FilePickerCategory.ARCHIVES).toSet()
    assertTrue("application/zip" in archiveMimes)
    assertTrue("application/x-tar" in archiveMimes)

    val apkMimes = DeviceFilePickerSpecs.mimeTypesFor(FilePickerCategory.APK_BUILDS).toSet()
    assertTrue("application/vnd.android.package-archive" in apkMimes)

    assertNotEquals(
      DeviceFilePickerSpecs.extensionHintsFor(FilePickerCategory.SOURCE_CODE),
      DeviceFilePickerSpecs.extensionHintsFor(FilePickerCategory.APK_BUILDS),
    )
  }
}
