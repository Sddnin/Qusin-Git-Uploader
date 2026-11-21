# Qusin Git Uploader 1.0

**Qusin Git Uploader** là ứng dụng Android native chuyên dụng cho phép lập trình viên tải source code, thư mục dự án, file ZIP, APK và các file dung lượng lớn (>50MB / ≥100MB) trực tiếp từ điện thoại lên GitHub mà không cần Termux, không cần gõ lệnh Git và không cần máy tính.

## Tính năng chính

- **Giao diện Cyberpunk / Space Tech (Material 3)**: Hỗ trợ Cyberpunk Dark Mode & Space Lab Light Mode (chuyển nhanh 1 chạm trên thanh tiêu đề), hỗ trợ **11 ngôn ngữ** (`Tiếng Việt`, `English`, `中文`, `日本語`, `한국어`, `Español`, `Français`, `Deutsch`, `Português`, `Русский`, `Bahasa Indonesia`) cùng chế độ **Theo ngôn ngữ máy (System Auto)**, thiết kế responsive cho cả điện thoại và tablet.
- **Xác thực GitHub an toàn**: Hỗ trợ Classic & Fine-grained Personal Access Token (PAT), mã hóa bằng **Android Keystore (AES-256-GCM)**, kiểm tra scope/quyền ghi, hỗ trợ ngắt kết nối và xóa thông tin xác thực.
- **File Manager (Storage Access Framework)**: Chọn 1 file, nhiều file, thư mục dự án hoặc file ZIP mà không đòi quyền đọc toàn bộ bộ nhớ; lọc theo phần mở rộng, loại trừ thư mục rác (`node_modules`, `.next`, `.git`, `build`, `.gradle`, `dist`, `.idea`), bỏ chọn từng file.
- **Git LFS Batch API thực tế**:
  - Tự động phân loại `<50MB` (Git thường), `50MB – <100MB` (cảnh báo & đề xuất Git LFS), `≥100MB` (bắt buộc Git LFS).
  - Tính SHA-256 streaming (không tràn RAM), tạo pointer file chuẩn `git-lfs.github.com/spec/v1`, tự động cập nhật `.gitattributes`, gọi Batch API, stream PUT object, chạy verify action và phát hiện lỗi quota LFS.
- **Git Data Operations**: Khởi tạo repository trống, kiểm tra remote HEAD, phát hiện xung đột branch (Branch Conflict), tạo tree/commit nguyên tử, cập nhật ref, quản lý branch và xem lịch sử commit.
- **Advanced Project Update & Sync (Section XXIII)**:
  - Quét source mới (ZIP/thư mục) và so sánh với remote tree theo `path`, `size`, Git blob `SHA-1` và Git LFS `SHA-256` OID (tối ưu tái sử dụng LFS object đã tồn tại).
  - **4 chế độ cập nhật**: `Update Changes` (chỉ thêm/sửa), `Sync Project` (đồng bộ và đề xuất xóa kèm xác nhận), `Overwrite Project` (thay thế trong phạm vi thư mục đích), `Update Selected Files` (tự chọn file).
  - **Safe Update & Rollback**: Kiểm tra commit mới từ remote trước khi commit, xem trước danh sách thêm/sửa/xóa, retry không tạo commit trùng lặp, và hỗ trợ **Safe Rollback** không phá hủy lịch sử (tạo commit đảo ngược khôi phục tree cũ kèm xem trước danh sách file ảnh hưởng).
- **ZIP Manager**:
  - **Mode A**: Upload ZIP nguyên bản (tự bật Git LFS nếu vượt ngưỡng).
  - **Mode B**: Giải nén an toàn vào bộ nhớ tạm (chặn Zip Slip, chống ZIP bomb, giữ cấu trúc thư mục, xử lý trùng tên, giữ nguyên file ZIP gốc).
- **Upload Manager (WorkManager)**: Chạy nền bền bỉ, hiển thị tốc độ truyền, phần trăm, ETA, Tạm dừng/Tiếp tục/Thử lại an toàn không trùng commit, xem log & giải thích lỗi chi tiết.
- **Upload History & Settings & App Updater**: Lưu lịch sử upload & cập nhật bằng Room Database; phần Cài đặt tích hợp liên kết trực tiếp tới `https://github.com/Sddnin/Qusin-Git-Uploader`, kiểm tra GitHub Releases mới và tải/cài đè APK qua `FileProvider` ngay trong ứng dụng.

## Cấu trúc thư mục chính

- `core/` (`common`, `network`, `security`, `storage`, `designsystem`)
- `domain/` (`model`, `repository`)
- `data/` (`AppContainer`, `HistoryRepositoryImpl`, `SettingsRepositoryImpl`)
- `github/` (`api`, `dto`, `GitHubRepositoryImpl`)
- `git/` (`GitEngine`, `ProjectDiffEngine`)
- `gitlfs/` (`GitLfsEngine`, `GitLfsClassifier`, `GitAttributesHelper`)
- `upload/` (`SafFileScanner`, `ZipManager`, `UploadCoordinator`, `UploadWorker`)
- `feature/` (`dashboard`, `upload`, `update`, `repository`, `history`, `settings`)
- `.github/workflows/android-build.yml`
