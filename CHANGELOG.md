# Changelog

## [1.0] - 2026-11-21
### Added
- Giao diện Cyberpunk / Space Technology (Dark Mode & Light Mode) với font Space Grotesk, Plus Jakarta Sans, JetBrains Mono và song ngữ Tiếng Việt / Tiếng Anh.
- Xác thực GitHub Personal Access Token (Classic & Fine-grained) mã hóa bằng Android Keystore AES-256-GCM.
- Repository Manager: tìm kiếm, tạo repository, chọn/tạo branch, xem commit history, tải snapshot zipball.
- File Manager qua Storage Access Framework (SAF): chọn 1 file, nhiều file, thư mục dự án, ZIP/APK; lọc đuôi file và loại trừ thư mục.
- Git LFS Batch API đầy đủ: phân loại <50MB / 50–100MB / ≥100MB, streaming SHA-256, pointer file v1, cập nhật `.gitattributes`, streaming PUT upload và verify action.
- ZIP Manager Mode A (nguyên bản) & Mode B (giải nén an toàn chống Zip Slip & ZIP bomb).
- Upload Manager dùng WorkManager với Foreground Service (`dataSync`), pause/resume/retry an toàn và Room Database Upload History.
- Workflow GitHub Actions `.github/workflows/android-build.yml` tự động test và build Debug APK.
- Hệ thống **Update & Sync Project** nâng cao với Diff Engine (SHA-1 / Git LFS OID), 4 chế độ cập nhật (`Update Changes`, `Sync Project`, `Overwrite Project`, `Update Selected Files`), kiểm tra xung đột branch, lưu lịch sử biến động (`+added ~modified -deleted`), và **Safe Rollback** phiên bản trước.
- Giao diện Sáng (**Space Lab Light Mode**) kèm nút chuyển nhanh Sáng/Tối ngay trên thanh tiêu đề ứng dụng.
- Hỗ trợ **11 ngôn ngữ** (`Tiếng Việt`, `English`, `中文`, `日本語`, `한국어`, `Español`, `Français`, `Deutsch`, `Português`, `Русский`, `Bahasa Indonesia`) cùng chế độ **Theo ngôn ngữ máy (System Auto)**.
- Tích hợp liên kết trực tiếp kho `Sddnin/Qusin-Git-Uploader`, kiểm tra GitHub Releases và tải/cài đè APK qua `FileProvider` ngay trong phần Cài đặt.
- Tối ưu tốc độ upload: gộp file text UTF-8 nhỏ trực tiếp vào `POST /git/trees`, upload song song nhiều luồng (hỗ trợ 1–6 luồng cho cả Git blob & Git LFS), mở rộng buffer 256 KB, và bỏ `runBlocking` khỏi luồng mạng.
