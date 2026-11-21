# Qusin Git Uploader 1.0

### Push your code to GitHub straight from the phone in your pocket.

No computer. No Termux. Not a single Git command to type.

**Qusin Git Uploader** is a native Android app that lets you upload source code, project folders, ZIP files, APKs, and even files larger than 100MB to GitHub in just a few taps.

---

## ✨ Why you'll love it

### 📱 Beautiful, and right at home on any screen
A **Cyberpunk / Space Tech** look built on Material 3. Switch between Dark Mode and Light Mode with one tap, and enjoy a smooth experience on both phones and tablets.
Supports **11 languages**: Tiếng Việt, English, 中文, 日本語, 한국어, Español, Français, Deutsch, Português, Русский, Bahasa Indonesia, plus automatic system language detection.

### 🔐 Secure sign-in
Use a Personal Access Token (Classic or Fine-grained), encrypted with the **Android Keystore (AES-256-GCM)**. The app automatically checks write permissions, and you can disconnect and wipe your credentials at any time.

### 📂 Pick files freely, without extra permissions
Select a single file, multiple files, a whole folder, or a ZIP file **without granting access to all device storage** (Storage Access Framework). The app automatically skips junk folders such as `node_modules`, `.next`, `.git`, `build`, `.gradle`, `dist`, and `.idea`, and you can still deselect individual files.

### 🚀 Large files? Let Git LFS handle it
The app classifies files by size automatically:

| Size | How it's handled |
|---|---|
| Under 50MB | Regular Git |
| 50MB to under 100MB | Warning, with a suggestion to use Git LFS |
| 100MB and above | Git LFS required |

Everything runs behind the scenes: streaming SHA-256 hashing (no RAM overflow), standard pointer files per `git-lfs.github.com/spec/v1`, automatic `.gitattributes` updates, Batch API calls, streamed PUT uploads, verify actions, and LFS quota error detection.

### 🧬 Professional Git operations, no commands needed
Initialize empty repositories, check the remote HEAD, detect branch conflicts, create atomic trees and commits, update refs, manage branches, and browse commit history.

### 🔄 Smart project updates
The app scans your new source and compares it with what's on GitHub by `path`, `size`, Git blob `SHA-1`, and Git LFS `SHA-256`, so it only pushes what has actually changed and reuses LFS objects that already exist.

Choose from **4 modes**:

- **Update Changes**: add and modify only
- **Sync Project**: synchronize, with confirmation before any deletion
- **Overwrite Project**: replace content within the target folder
- **Update Selected Files**: hand-pick the files to update

Preview the list of added, modified, and deleted files before committing, and retries never create duplicate commits. If you make a mistake, **Safe Rollback** restores the previous state with a reverting commit that keeps your history intact, and shows you a preview of the affected files first.

### 🗜️ Safe ZIP management
- **Mode A**: upload the ZIP as-is, with Git LFS enabled automatically if it exceeds the threshold.
- **Mode B**: extract safely, then upload, with Zip Slip and ZIP bomb protection, preserved folder structure, duplicate-name handling, and the original ZIP left untouched.

### ⏱️ Reliable background uploads
Powered by **WorkManager**, uploads keep running even when your screen is off. The app shows transfer speed, percentage, and ETA, and lets you pause, resume, and safely retry, with logs and clear explanations of any errors.

### 📜 History and self-updating
Every upload and update is recorded in a Room Database. The app checks GitHub Releases for new versions, then downloads and installs the APK over the existing one via `FileProvider`, all inside the app.

---

## 🔗 Links

Source code: https://github.com/Sddnin/Qusin-Git-Uploader
