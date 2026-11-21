# Hướng dẫn Build APK — Qusin Git Uploader 1.0

## 1. Build tự động bằng GitHub Actions (Khuyên dùng)

1. Tạo hoặc sử dụng repository `Sddnin/Qusin-Git-Uploader` trên GitHub (Public hoặc Private).
2. Đẩy toàn bộ mã nguồn dự án (xem tổng quan tính năng tại `README_EN.md`) lên nhánh `main` của repository.
3. Đảm bảo file `.github/workflows/android-build.yml` nằm đúng đường dẫn trong nhánh `main`.
4. Vào tab **Actions** trên GitHub -> chọn workflow **Android CI Build (Qusin Git Uploader)**.
5. Sau khi workflow chạy xong (`testDebugUnitTest` và `assembleDebug`), cuộn xuống mục **Artifacts** và tải về **Qusin-Git-Uploader-APK** (chứa `app-debug.apk`).

> Debug build không yêu cầu bất kỳ GitHub Secret nào. Nếu muốn tự động build Release APK, cấu hình thêm `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD` và `KEY_PASSWORD` trong GitHub Repository Secrets.

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
