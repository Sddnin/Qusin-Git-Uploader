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
