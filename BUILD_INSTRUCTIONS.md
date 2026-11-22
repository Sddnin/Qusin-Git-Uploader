# Hướng dẫn Build APK — Qusin Git Uploader 1.0

## 1. Build tự động bằng GitHub Actions (Khuyên dùng)

1. Tạo hoặc sử dụng repository `Sddnin/Qusin-Git-Uploader` trên GitHub (Public hoặc Private).
2. Đẩy toàn bộ mã nguồn dự án (xem tổng quan tính năng tại `README.md`) lên nhánh `main` của repository.
3. Đảm bảo file `.github/workflows/android-build.yml` và `debug.keystore.base64` nằm đúng tại gốc repository.
4. Vào tab **Actions** trên GitHub -> chọn workflow **Android CI Build (Qusin Git Uploader)**.
5. Sau khi workflow chạy xong (`testDebugUnitTest`, `assembleDebug`), file `.apk` được xuất bản trực tiếp tại mục **Releases** trên trang chủ repo (và bản artifact **Qusin-Git-Uploader-APK** trong tab Actions).

> Workflow tự động khôi phục chữ ký cố định từ `debug.keystore.base64` nên các bản build Debug sau này cài đè trực tiếp không cần gỡ bản cũ. Nếu muốn ký bằng keystore Release riêng, cấu hình thêm `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD` và `KEY_PASSWORD` trong GitHub Repository Secrets.

## 2. Build cục bộ bằng Android Studio hoặc CLI

### Yêu cầu môi trường
- JDK 21 (Temurin / OpenJDK 21)
- Android SDK Platform 36 & Build Tools tương ứng

### Lệnh Gradle
```bash
# Chạy unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug
```

File APK đầu ra:
`app/build/outputs/apk/debug/app-debug.apk`
