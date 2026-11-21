package com.example.gitlfs

import com.example.core.common.AppError
import com.example.core.common.AppErrorException
import com.example.core.network.StreamingProgressRequestBody
import com.example.core.security.TokenStore
import com.example.domain.model.LfsClassification
import com.example.domain.model.LfsFilePlan
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.InputStream
import java.security.MessageDigest
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class LfsObjectRefDto(
  val oid: String,
  val size: Long,
)

@JsonClass(generateAdapter = true)
data class LfsBatchRequestDto(
  val operation: String = "upload",
  val transfers: List<String> = listOf("basic"),
  @Json(name = "ref") val gitRef: LfsGitRefDto? = null,
  val objects: List<LfsObjectRefDto>,
  @Json(name = "hash_algo") val hashAlgo: String = "sha256",
)

@JsonClass(generateAdapter = true)
data class LfsGitRefDto(
  val name: String,
)

@JsonClass(generateAdapter = true)
data class LfsActionDetailDto(
  val href: String,
  val header: Map<String, String>? = null,
  @Json(name = "expires_in") val expiresIn: Long? = null,
)

@JsonClass(generateAdapter = true)
data class LfsActionsDto(
  val upload: LfsActionDetailDto? = null,
  val verify: LfsActionDetailDto? = null,
)

@JsonClass(generateAdapter = true)
data class LfsObjectErrorDto(
  val code: Int,
  val message: String,
)

@JsonClass(generateAdapter = true)
data class LfsBatchObjectResponseDto(
  val oid: String,
  val size: Long,
  val authenticated: Boolean? = null,
  val actions: LfsActionsDto? = null,
  val error: LfsObjectErrorDto? = null,
)

@JsonClass(generateAdapter = true)
data class LfsBatchResponseDto(
  val transfer: String? = "basic",
  val objects: List<LfsBatchObjectResponseDto> = emptyList(),
  val message: String? = null,
)

data class LfsUploadSource(
  val relativePath: String,
  val sizeBytes: Long,
  val openStream: () -> InputStream,
)

object GitLfsClassifier {
  const val STANDARD_LIMIT_BYTES: Long = 50L * 1024L * 1024L
  const val HARD_GIT_LIMIT_BYTES: Long = 100L * 1024L * 1024L

  fun classify(sizeBytes: Long): LfsClassification {
    return when {
      sizeBytes >= HARD_GIT_LIMIT_BYTES -> LfsClassification.LFS_REQUIRED
      sizeBytes >= STANDARD_LIMIT_BYTES -> LfsClassification.LFS_RECOMMENDED
      else -> LfsClassification.STANDARD_GIT
    }
  }

  fun shouldDefaultUseLfs(sizeBytes: Long, thresholdMb: Int, lfsEnabled: Boolean): Boolean {
    if (sizeBytes >= HARD_GIT_LIMIT_BYTES) return true
    if (!lfsEnabled) return false
    return sizeBytes >= thresholdMb.toLong() * 1024L * 1024L
  }
}

object GitAttributesHelper {
  private const val LFS_DIRECTIVE = "filter=lfs diff=lfs merge=lfs -text"

  fun buildPointerText(oidSha256: String, sizeBytes: Long): String {
    return "version https://git-lfs.github.com/spec/v1\noid sha256:$oidSha256\nsize $sizeBytes\n"
  }

  fun mergeGitAttributes(existingContent: String?, lfsRelativePaths: List<String>): String {
    val lines = existingContent
      ?.lines()
      ?.map { it.trimEnd() }
      ?.filter { it.isNotEmpty() }
      ?.toMutableList()
      ?: mutableListOf()

    for (path in lfsRelativePaths) {
      val escapedPattern = escapePattern(path)
      val rule = "$escapedPattern $LFS_DIRECTIVE"
      val alreadyTracked = lines.any { line ->
        line.startsWith("$escapedPattern ") && line.contains("filter=lfs")
      }
      if (!alreadyTracked) {
        lines.add(rule)
      }
    }
    return if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n"
  }

  private fun escapePattern(path: String): String {
    // Gitattributes escapes spaces as [[:space:]] or backslash
    return path.replace(" ", "\\ ")
  }
}

class GitLfsEngine(
  private val lfsHttpClient: OkHttpClient,
  private val moshi: Moshi,
  private val tokenStore: TokenStore,
) {
  private val batchReqAdapter = moshi.adapter(LfsBatchRequestDto::class.java)
  private val batchResAdapter = moshi.adapter(LfsBatchResponseDto::class.java)
  private val verifyReqAdapter = moshi.adapter(LfsObjectRefDto::class.java)

  fun computeSha256(openStream: () -> InputStream, isCancelled: () -> Boolean): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    openStream().use { input ->
      while (true) {
        if (isCancelled()) throw AppErrorException(AppError.GitLfsFailure("Cancelled during SHA-256 scan"))
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun uploadObjectsViaBatchApi(
    owner: String,
    repo: String,
    branch: String,
    sources: List<LfsUploadSource>,
    isCancelled: () -> Boolean,
    onLog: (String) -> Unit,
    onProgressDelta: (bytesDelta: Long) -> Unit,
  ): List<LfsFilePlan> {
    if (sources.isEmpty()) return emptyList()
    val token = tokenStore.getToken()
      ?: throw AppErrorException(AppError.Http401("Missing GitHub token for Git LFS"))

    // 1. Compute SHA-256 OIDs cleanly via streaming
    val plansWithSource = sources.map { source ->
      onLog("[LFS] Hashing SHA-256: ${source.relativePath} (${source.sizeBytes} bytes)")
      val oid = computeSha256(source.openStream, isCancelled)
      val pointer = GitAttributesHelper.buildPointerText(oid, source.sizeBytes)
      LfsFilePlan(
        relativePath = source.relativePath,
        sizeBytes = source.sizeBytes,
        sha256Hex = oid,
        pointerContent = pointer,
      ) to source
    }

    // 2. Request Batch API upload & verify actions
    val batchUrl = "https://github.com/$owner/$repo.git/info/lfs/objects/batch"
    val requestDto = LfsBatchRequestDto(
      operation = "upload",
      transfers = listOf("basic"),
      gitRef = LfsGitRefDto(name = "refs/heads/$branch"),
      objects = plansWithSource.map { (plan, _) -> LfsObjectRefDto(plan.sha256Hex, plan.sizeBytes) },
    )
    val jsonBody = batchReqAdapter.toJson(requestDto)
    val basicAuth = Credentials.basic("x-access-token", token)

    onLog("[LFS] POST Batch API -> $batchUrl (${plansWithSource.size} object(s))")
    val batchRequest = Request.Builder()
      .url(batchUrl)
      .header("Accept", LFS_MEDIA_TYPE)
      .header("Content-Type", LFS_MEDIA_TYPE)
      .header("Authorization", basicAuth)
      .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")
      .post(jsonBody.toRequestBody(LFS_MEDIA_TYPE.toMediaType()))
      .build()

    val batchResponseBody = lfsHttpClient.newCall(batchRequest).execute().use { res ->
      val bodyStr = res.body?.string().orEmpty()
      if (!res.isSuccessful) {
        throw AppErrorException(parseLfsHttpError(res.code, bodyStr))
      }
      bodyStr
    }

    val batchResponse = batchResAdapter.fromJson(batchResponseBody)
      ?: throw AppErrorException(AppError.GitLfsFailure("Invalid JSON from Git LFS Batch API"))

    val responseMap = batchResponse.objects.associateBy { it.oid }

    // 3. Upload & Verify each object
    for ((plan, source) in plansWithSource) {
      if (isCancelled()) throw AppErrorException(AppError.GitLfsFailure("Upload paused or cancelled"))
      val objRes = responseMap[plan.sha256Hex]
        ?: throw AppErrorException(AppError.GitLfsFailure("LFS server omitted object ${plan.sha256Hex}"))

      objRes.error?.let { objErr ->
        throw AppErrorException(parseLfsHttpError(objErr.code, objErr.message))
      }

      val uploadAction = objRes.actions?.upload
      if (uploadAction == null) {
        onLog("[LFS] Object already exists on GitHub LFS (verified skip): ${plan.sha256Hex.take(12)}...")
        onProgressDelta(plan.sizeBytes)
      } else {
        onLog("[LFS] Streaming PUT ${source.relativePath} -> ${uploadAction.href.substringBefore('?')}")
        val putBuilder = Request.Builder()
          .url(uploadAction.href)
          .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")

        uploadAction.header?.forEach { (k, v) ->
          putBuilder.header(k, v)
        }

        val streamingBody = StreamingProgressRequestBody(
          contentLengthBytes = source.sizeBytes,
          openStream = source.openStream,
          isCancelled = isCancelled,
          onBytesWritten = { delta, _ -> onProgressDelta(delta) },
        )
        putBuilder.put(streamingBody)

        lfsHttpClient.newCall(putBuilder.build()).execute().use { putRes ->
          val putErr = if (!putRes.isSuccessful) putRes.body?.string().orEmpty() else ""
          if (!putRes.isSuccessful) {
            throw AppErrorException(parseLfsHttpError(putRes.code, putErr))
          }
        }
        onLog("[LFS] Object PUT completed: ${plan.sha256Hex.take(12)}")
      }

      // 4. Verify action if provided by server
      val verifyAction = objRes.actions?.verify
      if (verifyAction != null) {
        onLog("[LFS] Executing verify action for ${plan.sha256Hex.take(12)}...")
        val verifyPayload = verifyReqAdapter.toJson(LfsObjectRefDto(plan.sha256Hex, plan.sizeBytes))
        val verifyReqBuilder = Request.Builder()
          .url(verifyAction.href)
          .header("Accept", LFS_MEDIA_TYPE)
          .header("Content-Type", LFS_MEDIA_TYPE)
          .header("User-Agent", "Qusin-Git-Uploader/1.0 (Android)")
          .post(verifyPayload.toRequestBody(LFS_MEDIA_TYPE.toMediaType()))

        verifyAction.header?.forEach { (k, v) -> verifyReqBuilder.header(k, v) }
        if (verifyAction.href.contains("github.com") && verifyAction.header?.containsKey("Authorization") != true) {
          verifyReqBuilder.header("Authorization", basicAuth)
        }

        lfsHttpClient.newCall(verifyReqBuilder.build()).execute().use { verRes ->
          if (!verRes.isSuccessful) {
            val err = verRes.body?.string().orEmpty()
            throw AppErrorException(AppError.GitLfsFailure("LFS verify failed (${verRes.code}): $err"))
          }
        }
        onLog("[LFS] Object verified by GitHub LFS: ${plan.sha256Hex.take(12)}")
      }
    }

    return plansWithSource.map { it.first }
  }

  private fun parseLfsHttpError(code: Int, body: String): AppError {
    val lower = body.lowercase()
    if (code == 507 ||
      lower.contains("quota") ||
      lower.contains("bandwidth") ||
      lower.contains("storage limit") ||
      lower.contains("over your data quota")
    ) {
      return AppError.LfsQuotaExceeded("LFS Quota Error ($code): $body")
    }
    return when (code) {
      401, 403, 404, 422, 429, in 500..599 -> AppError.fromHttpStatus(code, body)
      else -> AppError.GitLfsFailure("HTTP $code: $body")
    }
  }

  companion object {
    const val LFS_MEDIA_TYPE = "application/vnd.git-lfs+json"
  }
}
