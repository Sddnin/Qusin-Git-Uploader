package com.example.core.network

import com.example.core.security.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class NetworkClientProvider(private val tokenStore: TokenStore) {
  val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val authHeaderInterceptor = Interceptor { chain ->
    val original = chain.request()
    val host = original.url.host.lowercase()
    val builder = original.newBuilder()
      .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")

    // Security boundary: NEVER send GitHub PAT to any third-party host (e.g. external LFS S3 signed URLs)
    val isGitHubHost = host == "api.github.com" || host == "github.com" || host.endsWith(".github.com")
    val hasExplicitAuth = original.header("Authorization") != null
    if (isGitHubHost && !hasExplicitAuth) {
      val token = tokenStore.getToken()
      if (!token.isNullOrBlank()) {
        builder.header("Authorization", "Bearer $token")
      }
    }
    chain.proceed(builder.build())
  }

  private val safeLogger = HttpLoggingInterceptor { message ->
    // Redact any accidental credential substrings just in case
    if (message.contains("Authorization:", ignoreCase = true) ||
      message.contains("ghp_") ||
      message.contains("github_pat_")
    ) {
      return@HttpLoggingInterceptor
    }
  }.apply {
    level = HttpLoggingInterceptor.Level.BASIC
    redactHeader("Authorization")
    redactHeader("Cookie")
  }

  val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .addInterceptor(authHeaderInterceptor)
    .addInterceptor(safeLogger)
    .build()

  // Separate streaming client without request body buffering for large LFS object PUTs
  val lfsTransferClient: OkHttpClient = OkHttpClient.Builder()
    .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(300, TimeUnit.SECONDS)
    .addInterceptor(safeLogger)
    .build()

  val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
}

class StreamingProgressRequestBody(
  private val contentLengthBytes: Long,
  private val contentTypeMedia: MediaType = "application/octet-stream".toMediaType(),
  private val openStream: () -> InputStream,
  private val isCancelled: () -> Boolean,
  private val onBytesWritten: (bytesDelta: Long, totalWritten: Long) -> Unit,
) : RequestBody() {
  override fun contentType(): MediaType = contentTypeMedia

  override fun contentLength(): Long = contentLengthBytes

  override fun writeTo(sink: BufferedSink) {
    val buffer = ByteArray(64 * 1024)
    var totalWritten = 0L
    openStream().use { input ->
      while (true) {
        if (isCancelled()) {
          throw java.io.InterruptedIOException("Upload paused or cancelled by user")
        }
        val read = input.read(buffer)
        if (read == -1) break
        sink.write(buffer, 0, read)
        totalWritten += read
        onBytesWritten(read.toLong(), totalWritten)
      }
    }
  }
}
