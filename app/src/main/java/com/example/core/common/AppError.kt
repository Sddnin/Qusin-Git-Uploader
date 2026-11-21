package com.example.core.common

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class AppError(
  val code: String,
  val titleVi: String,
  val titleEn: String,
  val causeVi: String,
  val causeEn: String,
  val fixVi: String,
  val fixEn: String,
  val retryable: Boolean,
  val technicalDetail: String? = null,
) {
  fun title(isVi: Boolean) = if (isVi) titleVi else titleEn
  fun cause(isVi: Boolean) = if (isVi) causeVi else causeEn
  fun fix(isVi: Boolean) = if (isVi) fixVi else fixEn

  class Http400(detail: String? = null) : AppError(
    code = "HTTP 400",
    titleVi = "Yêu cầu không hợp lệ",
    titleEn = "Bad Request",
    causeVi = "Dữ liệu gửi tới GitHub không đúng định dạng hoặc tên nhánh/đường dẫn chứa ký tự không hợp lệ.",
    causeEn = "The request payload or path/branch name contains invalid characters.",
    fixVi = "Kiểm tra lại tên repository, tên branch và đường dẫn file rồi thử lại.",
    fixEn = "Verify the repository name, branch name, and file paths before retrying.",
    retryable = false,
    technicalDetail = detail,
  )

  class Http401(detail: String? = null) : AppError(
    code = "HTTP 401",
    titleVi = "Token không hợp lệ hoặc đã hết hạn",
    titleEn = "Authentication Failed / Token Expired",
    causeVi = "Personal Access Token bị sai, đã bị thu hồi hoặc hết hạn sử dụng.",
    causeEn = "Your Personal Access Token is invalid, revoked, or expired.",
    fixVi = "Mở Quản lý GitHub hoặc Cài đặt để cập nhật Personal Access Token mới.",
    fixEn = "Open Repository Manager or Settings to update your Personal Access Token.",
    retryable = false,
    technicalDetail = detail,
  )

  class Http403(detail: String? = null) : AppError(
    code = "HTTP 403",
    titleVi = "Không có quyền truy cập hoặc vượt giới hạn API",
    titleEn = "Forbidden / Permission Denied",
    causeVi = "Token thiếu quyền ghi (Contents: Read & Write / repo scope) hoặc GitHub tạm giới hạn tần suất.",
    causeEn = "Token lacks write permissions (Contents: Read & Write / repo scope) or API rate limit was reached.",
    fixVi = "Kiểm tra quyền token trong GitHub Settings -> Developer settings hoặc đợi vài phút rồi thử lại.",
    fixEn = "Grant repository write scope to your token or wait briefly for rate limit reset.",
    retryable = true,
    technicalDetail = detail,
  )

  class Http404(detail: String? = null) : AppError(
    code = "HTTP 404",
    titleVi = "Không tìm thấy Repository hoặc Branch",
    titleEn = "Repository or Branch Not Found",
    causeVi = "Repository không tồn tại, là private nhưng token không có quyền, hoặc branch chưa được tạo.",
    causeEn = "The repository does not exist, is private without token access, or the branch is missing.",
    fixVi = "Làm mới danh sách repository và chọn đúng repository/branch đích.",
    fixEn = "Refresh repositories and verify the target repository and branch.",
    retryable = false,
    technicalDetail = detail,
  )

  class Http409(detail: String? = null) : AppError(
    code = "HTTP 409",
    titleVi = "Xung đột nhánh (Branch Conflict)",
    titleEn = "Branch Conflict",
    causeVi = "Nhánh trên GitHub đã có commit mới hơn kể từ lúc bạn bắt đầu chuẩn bị upload.",
    causeEn = "The remote branch received newer commits since you started preparing the upload.",
    fixVi = "Đồng bộ lại thông tin nhánh mới nhất (Pull/Sync) hoặc xác nhận cập nhật trước khi Push.",
    fixEn = "Sync with the latest remote commit or confirm branch overwrite before pushing.",
    retryable = true,
    technicalDetail = detail,
  )

  class Http422(detail: String? = null) : AppError(
    code = "HTTP 422",
    titleVi = "Dữ liệu Git không hợp lệ hoặc trùng tên",
    titleEn = "Unprocessable Entity",
    causeVi = "Tên repository/branch đã tồn tại hoặc kích thước file vượt quá giới hạn Git thông thường (>100MB).",
    causeEn = "Repository/branch already exists or a file exceeds standard Git size limits (>100MB).",
    fixVi = "Đổi tên mới hoặc bật Git LFS cho các file lớn hơn 50MB.",
    fixEn = "Choose a unique name or enable Git LFS for files larger than 50MB.",
    retryable = false,
    technicalDetail = detail,
  )

  class Http429(detail: String? = null) : AppError(
    code = "HTTP 429",
    titleVi = "Quá nhiều yêu cầu tới GitHub",
    titleEn = "Rate Limit Exceeded",
    causeVi = "Tốc độ gửi request vượt giới hạn cho phép của GitHub API.",
    causeEn = "Request frequency exceeded GitHub API rate limits.",
    fixVi = "Giảm số luồng upload đồng thời trong Cài đặt và thử lại sau 1–2 phút.",
    fixEn = "Lower upload concurrency in Settings and retry in 1–2 minutes.",
    retryable = true,
    technicalDetail = detail,
  )

  class Http5xx(statusCode: Int, detail: String? = null) : AppError(
    code = "HTTP $statusCode",
    titleVi = "Lỗi máy chủ GitHub",
    titleEn = "GitHub Server Error",
    causeVi = "Máy chủ GitHub hoặc Git LFS đang gặp sự cố tạm thời.",
    causeEn = "GitHub or Git LFS servers returned a temporary server error.",
    fixVi = "Nhấn Thử lại để tiếp tục an toàn mà không tạo commit trùng lặp.",
    fixEn = "Tap Retry to safely resume without creating duplicate commits.",
    retryable = true,
    technicalDetail = detail,
  )

  class NetworkTimeout(detail: String? = null) : AppError(
    code = "NET_TIMEOUT",
    titleVi = "Hết thời gian kết nối mạng",
    titleEn = "Network Timeout",
    causeVi = "Kết nối Internet chậm hoặc bị gián đoạn khi đang truyền dữ liệu.",
    causeEn = "The network connection timed out while transferring data.",
    fixVi = "Kiểm tra Wi-Fi/4G và nhấn Thử lại. Các object LFS đã upload sẽ được bỏ qua tự động.",
    fixEn = "Check your connection and tap Retry. Already uploaded LFS objects will be reused.",
    retryable = true,
    technicalDetail = detail,
  )

  class DnsFailure(detail: String? = null) : AppError(
    code = "DNS_FAILURE",
    titleVi = "Không thể phân giải máy chủ GitHub",
    titleEn = "DNS Resolution Failed",
    causeVi = "Thiết bị đang ngoại tuyến hoặc DNS không truy cập được api.github.com.",
    causeEn = "Device is offline or unable to resolve api.github.com.",
    fixVi = "Bật Wi-Fi/Dữ liệu di động hoặc kiểm tra VPN/DNS rồi thử lại.",
    fixEn = "Enable internet access or verify VPN/DNS settings and retry.",
    retryable = true,
    technicalDetail = detail,
  )

  class LfsQuotaExceeded(detail: String? = null) : AppError(
    code = "LFS_QUOTA_EXCEEDED",
    titleVi = "Vượt hạn mức Git LFS của tài khoản",
    titleEn = "Git LFS Quota Exceeded",
    causeVi = "Dung lượng lưu trữ hoặc băng thông Git LFS trên gói GitHub hiện tại đã hết.",
    causeEn = "Your GitHub account's Git LFS storage or bandwidth quota has been exhausted.",
    fixVi = "Kiểm tra mục Billing -> Git LFS Data trên GitHub hoặc xóa bớt file lớn không cần thiết.",
    fixEn = "Check GitHub Billing -> Git LFS Data or remove unnecessary large binary files.",
    retryable = false,
    technicalDetail = detail,
  )

  class GitLfsFailure(detail: String? = null) : AppError(
    code = "GIT_LFS_ERROR",
    titleVi = "Lỗi giao thức Git LFS",
    titleEn = "Git LFS Transfer Error",
    causeVi = "Quá trình Batch API, upload object hoặc verify object với Git LFS gặp sự cố.",
    causeEn = "Git LFS Batch API, object PUT upload, or verify step failed.",
    fixVi = "Kiểm tra quyền ghi repository và thử lại. Hệ thống sẽ xác minh lại object trên server.",
    fixEn = "Verify repository write access and retry; existing objects will be verified automatically.",
    retryable = true,
    technicalDetail = detail,
  )

  class StorageFull(detail: String? = null) : AppError(
    code = "STORAGE_FULL",
    titleVi = "Bộ nhớ thiết bị không đủ",
    titleEn = "Device Storage Full",
    causeVi = "Không đủ dung lượng trống để giải nén file ZIP hoặc tạo dữ liệu tạm.",
    causeEn = "Insufficient free space to extract ZIP archives or stage temporary data.",
    fixVi = "Dọn cache ứng dụng trong Cài đặt hoặc giải phóng dung lượng điện thoại.",
    fixEn = "Clear app cache in Settings or free up device storage.",
    retryable = false,
    technicalDetail = detail,
  )

  class ZipExtractionFailure(detail: String? = null) : AppError(
    code = "ZIP_ERROR",
    titleVi = "Lỗi giải nén hoặc bảo mật ZIP",
    titleEn = "ZIP Extraction / Security Error",
    causeVi = "File ZIP bị hỏng, vượt quá giới hạn dung lượng/số file giải nén hoặc chứa đường dẫn nguy hiểm (Zip Slip).",
    causeEn = "The ZIP archive is corrupted, exceeds extraction safety limits, or contains unsafe paths (Zip Slip).",
    fixVi = "Sử dụng chế độ 'Upload ZIP nguyên bản' hoặc kiểm tra lại file ZIP nguồn.",
    fixEn = "Switch to 'Upload Raw ZIP' mode or verify the source archive.",
    retryable = false,
    technicalDetail = detail,
  )

  class Unknown(detail: String? = null) : AppError(
    code = "UNKNOWN_ERROR",
    titleVi = "Lỗi không xác định",
    titleEn = "Unexpected Error",
    causeVi = "Đã xảy ra sự cố ngoài dự kiến trong quá trình xử lý.",
    causeEn = "An unexpected error occurred during execution.",
    fixVi = "Xem chi tiết kỹ thuật bên dưới và thử lại.",
    fixEn = "Review the technical details below and try again.",
    retryable = true,
    technicalDetail = detail,
  )

  companion object {
    fun fromHttpStatus(code: Int, body: String? = null): AppError {
      val lower = body?.lowercase().orEmpty()
      if (lower.contains("lfs") && (lower.contains("quota") || lower.contains("bandwidth") || lower.contains("storage") || code == 507)) {
        return LfsQuotaExceeded(body)
      }
      return when (code) {
        400 -> Http400(body)
        401 -> Http401(body)
        403 -> Http403(body)
        404 -> Http404(body)
        409 -> Http409(body)
        422 -> Http422(body)
        429 -> Http429(body)
        in 500..599 -> Http5xx(code, body)
        else -> Unknown("HTTP $code: ${body.orEmpty()}")
      }
    }

    fun fromThrowable(t: Throwable): AppError {
      return when (t) {
        is AppErrorException -> t.error
        is UnknownHostException -> DnsFailure(t.message)
        is SocketTimeoutException -> NetworkTimeout(t.message)
        is IOException -> {
          val msg = t.message.orEmpty()
          if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space left", ignoreCase = true)) {
            StorageFull(msg)
          } else if (msg.contains("timeout", ignoreCase = true)) {
            NetworkTimeout(msg)
          } else {
            Unknown(msg)
          }
        }
        else -> Unknown(t.message ?: t.javaClass.simpleName)
      }
    }
  }
}

class AppErrorException(val error: AppError) : Exception("${error.code}: ${error.technicalDetail ?: error.titleEn}")

sealed interface AppResult<out T> {
  data class Success<T>(val data: T) : AppResult<T>
  data class Failure(val error: AppError) : AppResult<Nothing>
}
