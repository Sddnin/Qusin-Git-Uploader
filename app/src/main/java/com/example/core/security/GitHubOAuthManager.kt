package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.BuildConfig
import com.example.core.common.AppError
import com.example.core.common.AppResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class PendingOAuthSession(
  val state: String,
  val codeVerifier: String,
  val clientId: String,
  val createdAtMillis: Long,
)

sealed class OAuthCallbackValidation {
  data class Authorized(
    val code: String,
    val codeVerifier: String,
    val clientId: String,
  ) : OAuthCallbackValidation()

  data class Failed(val error: AppError) : OAuthCallbackValidation()
}

@JsonClass(generateAdapter = true)
data class GitHubOAuthTokenResponseDto(
  @Json(name = "access_token") val accessToken: String? = null,
  @Json(name = "token_type") val tokenType: String? = null,
  @Json(name = "scope") val scope: String? = null,
  @Json(name = "error") val error: String? = null,
  @Json(name = "error_description") val errorDescription: String? = null,
)

class GitHubOAuthManager(
  context: Context,
  private val httpClient: OkHttpClient,
  private val moshi: Moshi,
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val tokenResponseAdapter = moshi.adapter(GitHubOAuthTokenResponseDto::class.java)

  fun getConfiguredClientId(): String {
    val custom = prefs.getString(KEY_CUSTOM_CLIENT_ID, null)?.trim().orEmpty()
    if (custom.isNotEmpty()) return custom
    return BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
  }

  fun saveCustomClientId(clientId: String) {
    val clean = clientId.trim()
    prefs.edit().apply {
      if (clean.isEmpty()) remove(KEY_CUSTOM_CLIENT_ID) else putString(KEY_CUSTOM_CLIENT_ID, clean)
    }.apply()
  }

  fun beginAuthorization(clientId: String): AppResult<Uri> {
    val cleanClientId = clientId.trim()
    if (cleanClientId.isEmpty()) {
      return AppResult.Failure(
        AppError.Http400("GitHub OAuth Client ID is not configured yet."),
      )
    }

    val state = generateState()
    val codeVerifier = generateCodeVerifier()
    val codeChallenge = computeCodeChallengeS256(codeVerifier)

    savePendingSession(
      PendingOAuthSession(
        state = state,
        codeVerifier = codeVerifier,
        clientId = cleanClientId,
        createdAtMillis = clockMillis(),
      ),
    )

    val urlString = buildAuthorizationUrlString(
      clientId = cleanClientId,
      state = state,
      codeChallenge = codeChallenge,
    )
    return AppResult.Success(Uri.parse(urlString))
  }

  fun hasPendingSession(): Boolean {
    val pending = readPendingSession() ?: return false
    return (clockMillis() - pending.createdAtMillis) <= SESSION_TTL_MILLIS
  }

  fun cancelPendingSession() {
    prefs.edit()
      .remove(KEY_PENDING_STATE)
      .remove(KEY_PENDING_VERIFIER)
      .remove(KEY_PENDING_CLIENT_ID)
      .remove(KEY_PENDING_CREATED_AT)
      .apply()
  }

  fun validateCallbackUri(uri: Uri): OAuthCallbackValidation {
    if (!isOAuthCallbackUri(uri)) {
      return OAuthCallbackValidation.Failed(
        AppError.Http400("Invalid OAuth callback URI."),
      )
    }

    val pending = readPendingSession()
    // Single-use session: consume immediately to prevent replay attacks
    cancelPendingSession()

    val validation = validateCallbackParameters(
      error = uri.getQueryParameter("error"),
      errorDescription = uri.getQueryParameter("error_description"),
      code = uri.getQueryParameter("code"),
      returnedState = uri.getQueryParameter("state"),
      pending = pending,
      nowMillis = clockMillis(),
    )
    return validation
  }

  fun exchangeCodeForToken(
    clientId: String,
    code: String,
    codeVerifier: String,
  ): AppResult<String> {
    return try {
      val formBody = FormBody.Builder()
        .add("client_id", clientId)
        .add("code", code)
        .add("redirect_uri", REDIRECT_URI)
        .add("code_verifier", codeVerifier)
        .build()

      val request = Request.Builder()
        .url(TOKEN_ENDPOINT)
        .header("Accept", "application/json")
        .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")
        .post(formBody)
        .build()

      httpClient.newCall(request).execute().use { response ->
        val rawBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          return AppResult.Failure(AppError.fromHttpStatus(response.code, rawBody))
        }
        val dto = tokenResponseAdapter.fromJson(rawBody)
          ?: return AppResult.Failure(AppError.Unknown("Invalid GitHub OAuth token response"))

        if (!dto.error.isNullOrBlank()) {
          val msg = dto.errorDescription?.takeIf { it.isNotBlank() } ?: dto.error
          return AppResult.Failure(AppError.Http401("GitHub OAuth error: $msg"))
        }

        val accessToken = dto.accessToken?.trim().orEmpty()
        if (accessToken.isEmpty()) {
          return AppResult.Failure(AppError.Http401("GitHub OAuth response did not include an access token."))
        }
        AppResult.Success(accessToken)
      }
    } catch (t: Throwable) {
      AppResult.Failure(AppError.fromThrowable(t))
    }
  }

  private fun savePendingSession(session: PendingOAuthSession) {
    prefs.edit()
      .putString(KEY_PENDING_STATE, session.state)
      .putString(KEY_PENDING_VERIFIER, session.codeVerifier)
      .putString(KEY_PENDING_CLIENT_ID, session.clientId)
      .putLong(KEY_PENDING_CREATED_AT, session.createdAtMillis)
      .apply()
  }

  private fun readPendingSession(): PendingOAuthSession? {
    val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
    val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return null
    val clientId = prefs.getString(KEY_PENDING_CLIENT_ID, null) ?: return null
    val createdAt = prefs.getLong(KEY_PENDING_CREATED_AT, 0L)
    if (createdAt <= 0L) return null
    return PendingOAuthSession(
      state = state,
      codeVerifier = verifier,
      clientId = clientId,
      createdAtMillis = createdAt,
    )
  }

  companion object {
    const val AUTHORIZE_ENDPOINT = "https://github.com/login/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token"
    const val REDIRECT_SCHEME = "qusin"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_PATH = "/callback"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"
    const val DEFAULT_SCOPES = "repo read:user"
    const val CREATE_GITHUB_APP_URL =
      "https://github.com/settings/apps/new?callback_url=qusin://oauth/callback"

    private const val SESSION_TTL_MILLIS = 15 * 60 * 1000L
    private const val PREFS_NAME = "qusin_github_oauth_state"
    private const val KEY_CUSTOM_CLIENT_ID = "custom_oauth_client_id"
    private const val KEY_PENDING_STATE = "pending_state"
    private const val KEY_PENDING_VERIFIER = "pending_code_verifier"
    private const val KEY_PENDING_CLIENT_ID = "pending_client_id"
    private const val KEY_PENDING_CREATED_AT = "pending_created_at"

    private val secureRandom = SecureRandom()

    fun isOAuthCallbackUri(uri: Uri?): Boolean {
      if (uri == null) return false
      return uri.scheme.equals(REDIRECT_SCHEME, ignoreCase = true) &&
        uri.host.equals(REDIRECT_HOST, ignoreCase = true) &&
        uri.path.orEmpty().startsWith(REDIRECT_PATH)
    }

    fun generateState(): String {
      val bytes = ByteArray(32)
      secureRandom.nextBytes(bytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateCodeVerifier(): String {
      val bytes = ByteArray(64)
      secureRandom.nextBytes(bytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun computeCodeChallengeS256(codeVerifier: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun buildAuthorizationUrlString(
      clientId: String,
      state: String,
      codeChallenge: String,
      scope: String = DEFAULT_SCOPES,
    ): String {
      fun enc(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
      return "$AUTHORIZE_ENDPOINT" +
        "?client_id=${enc(clientId)}" +
        "&redirect_uri=${enc(REDIRECT_URI)}" +
        "&scope=${enc(scope)}" +
        "&state=${enc(state)}" +
        "&code_challenge=${enc(codeChallenge)}" +
        "&code_challenge_method=S256"
    }

    fun validateCallbackParameters(
      error: String?,
      errorDescription: String?,
      code: String?,
      returnedState: String?,
      pending: PendingOAuthSession?,
      nowMillis: Long,
    ): OAuthCallbackValidation {
      if (!error.isNullOrBlank()) {
        val reason = errorDescription?.takeIf { it.isNotBlank() } ?: error
        return OAuthCallbackValidation.Failed(
          AppError.Http401("GitHub OAuth cancelled or denied: $reason"),
        )
      }
      if (pending == null) {
        return OAuthCallbackValidation.Failed(
          AppError.Http401("No active OAuth sign-in session found. Please start sign-in again."),
        )
      }
      if ((nowMillis - pending.createdAtMillis) > SESSION_TTL_MILLIS) {
        return OAuthCallbackValidation.Failed(
          AppError.Http401("OAuth sign-in session expired. Please try again."),
        )
      }
      val cleanState = returnedState?.trim().orEmpty()
      if (cleanState.isEmpty() || !constantTimeEquals(pending.state, cleanState)) {
        return OAuthCallbackValidation.Failed(
          AppError.Http401("OAuth state verification failed. Please retry sign-in."),
        )
      }
      val cleanCode = code?.trim().orEmpty()
      if (cleanCode.isEmpty()) {
        return OAuthCallbackValidation.Failed(
          AppError.Http400("GitHub OAuth callback did not contain an authorization code."),
        )
      }
      return OAuthCallbackValidation.Authorized(
        code = cleanCode,
        codeVerifier = pending.codeVerifier,
        clientId = pending.clientId,
      )
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
      return MessageDigest.isEqual(
        expected.toByteArray(StandardCharsets.UTF_8),
        actual.toByteArray(StandardCharsets.UTF_8),
      )
    }
  }
}
