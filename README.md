# Memeizer

Memeizer is a native Android app for building a searchable local gallery of memes.

Pick one or more folders, let the app OCR the images, then search your meme collection by the text inside the pictures. The app is designed around explicit user-selected folders and local indexing, not around scanning the whole phone.

## What It Does

- Shows selected meme folders as a searchable gallery.
- Indexes JPEG, PNG, and WebP images from folders selected through Android's Storage Access Framework.
- Runs OCR locally for Cyrillic/Russian text with NCNN PaddleOCR.
- Runs OCR for Latin/English text with Google ML Kit Text Recognition.
- Stores indexed images, OCR output, and search data in a local Room database.
- Lets you search recognized meme text from a Compose UI.
- Keeps the search box pinned at the top while image results scroll below it.
- Shows all indexed images when the search field is empty.

## Privacy Model

Memeizer does not scan your entire device.

The app only indexes folders that you explicitly add through the Android folder picker. OCR and search indexing happen on-device. The current app does not include account login, cloud sync, or any server-side indexing.

## Current Status

This is an early Android-only prototype.

It is usable for local testing, but it is not a polished Play Store release yet. Expect rough edges around indexing progress, error reporting, large libraries, and debug-only tooling.

## Screens And Flow

1. Open the app.
2. Go to the `Folders` tab.
3. Add a folder with memes.
4. Wait for indexing to finish in the background.
5. Go to the `Search` tab.
6. Leave the search field empty to browse all indexed images.
7. Type text to filter memes by recognized OCR text.
8. Tap a meme to preview it and inspect the OCR text.

## Tech Stack

- Kotlin
- Jetpack Compose
- Room + FTS
- WorkManager
- Storage Access Framework
- Coil 2
- Google ML Kit Text Recognition
- NCNN PaddleOCR native module

The Android package name is:

```text
com.darkesttrololo.memeizer
```

## OCR Pipeline

Memeizer combines OCR results from two engines:

- Cyrillic/Russian: local NCNN PaddleOCR module based on `equationl/paddleocr4android`.
- Latin/English: Google ML Kit Text Recognition.

The combined OCR text is written into a Room FTS table and queried locally.

For PaddleOCR Cyrillic output, visually equivalent Latin glyphs are normalized to Cyrillic before storage. For example, Latin `H`, `O`, `B`, `C`, `P`, and `K` are mapped to Cyrillic `Н`, `О`, `В`, `С`, `Р`, and `К`. ML Kit Latin output is not normalized this way.

## Requirements

- Android Studio
- JDK 17
- Android SDK with `compileSdk 35`
- Android NDK and CMake for the NCNN native module
- Android 12 / API 31 or newer on the device/emulator

The project currently uses Android Gradle Plugin `8.7.3`, Kotlin `2.0.21`, and KSP `2.0.21-1.0.28`.

## Build

From the repository root:

```bash
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If you use the Android Studio bundled JBR directly:

```bash
JAVA_HOME="/path/to/android-studio/jbr" ./gradlew assembleDebug
```

## Install And Launch

Install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app:

```bash
adb shell am start -n com.darkesttrololo.memeizer/.MainActivity
```

## Debug Reindex

Debug builds include an adb broadcast receiver that can force a reindex:

```bash
adb shell am broadcast \
  -n com.darkesttrololo.memeizer/.DebugReindexReceiver \
  -a com.darkesttrololo.memeizer.DEBUG_REINDEX \
  --ez force_reindex true
```

If the package was force-stopped, launch the app once before sending the broadcast.

## Project Layout

```text
app/src/main/java/com/darkesttrololo/memeizer/
```

Main Android app code.

```text
app/src/main/assets/
```

Packaged PaddleOCR NCNN model assets.

```text
third_party/ncnnAndroidPPOCR/
```

Vendored NCNN PaddleOCR Android module.

```text
third_party/ncnnAndroidPPOCR/src/main/jni/ppocrv5_dict.h
```

Compiled recognizer dictionary used by the native PaddleOCR wrapper.

## Packaged OCR Assets

The PaddleOCR NCNN assets are packaged under `app/src/main/assets`:

- `PP_OCRv5_mobile_det.ncnn.bin`
- `PP_OCRv5_mobile_det.ncnn.param`
- `PP_OCRv5_mobile_rec.ncnn.bin`
- `PP_OCRv5_mobile_rec.ncnn.param`

The recognizer dictionary includes the space token required by the `eslav_PP-OCRv5_mobile_rec` recognizer.

## Database Inspection

Copy the app database from a debug install:

```bash
adb exec-out run-as com.darkesttrololo.memeizer cat databases/memeizer.db > /tmp/memeizer.db
adb exec-out run-as com.darkesttrololo.memeizer cat databases/memeizer.db-wal > /tmp/memeizer.db-wal
adb exec-out run-as com.darkesttrololo.memeizer cat databases/memeizer.db-shm > /tmp/memeizer.db-shm
```

Check indexing status:

```bash
sqlite3 /tmp/memeizer.db "select index_status, count(*) from indexed_images group by index_status;"
```

Check OCR engines:

```bash
sqlite3 /tmp/memeizer.db "select engine, language, count(*) from ocr_results group by engine, language;"
```

Check an FTS query:

```bash
sqlite3 /tmp/memeizer.db "select count(*) from meme_search_fts where meme_search_fts match 'челябинск*';"
```

## Known Caveats

- The app is Android-only.
- The UI is still prototype-level.
- The NCNN PaddleOCR module is vendored and large because it includes native NCNN/OpenCV dependencies.
- The debug reindex receiver is currently exported for adb-driven testing.
- OCR quality depends heavily on image resolution, text style, language, and meme compression artifacts.
- Very large folders can take time to index and may use noticeable memory during OCR.
