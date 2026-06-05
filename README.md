# Memeizer

Native Android app for indexing memes from user-selected folders and searching them by OCR text.

The app does not scan the whole device. Users explicitly add folders through Android's Storage Access Framework, and Memeizer indexes only images from those folders.

## Status

This is an early Android-only prototype.

Current OCR pipeline:

- Cyrillic/Russian: local NCNN PaddleOCR module based on `equationl/paddleocr4android`.
- Latin/English: Google ML Kit Text Recognition.
- Search index: Room FTS table populated from combined OCR text.

## Features

- SAF folder picker for user-selected meme folders.
- Image scanner for JPEG, PNG, and WebP files.
- Background indexing with WorkManager.
- Local Room database for folders, images, OCR results, and FTS search.
- Compose UI for managing folders, searching OCR text, and previewing images.
- Debug adb broadcast for forced reindexing.

## Requirements

- Android Studio / Android Gradle Plugin compatible with the checked-in project.
- JDK 17.
- Android SDK with `compileSdk 35`.
- Android NDK/CMake for the NCNN native module.
- Minimum Android version: Android 12 / API 31.

The app package is:

```text
com.darkesttrololo.memeizer
```

## Build

From the repository root:

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch manually:

```bash
adb shell am start -n com.darkesttrololo.memeizer/.MainActivity
```

## Indexing Flow

1. Open the app.
2. Add a folder through the folder picker.
3. The app scans selected folders for supported image files.
4. `IndexWorker` runs OCR and updates the FTS table.
5. Use the Search tab to search recognized meme text.

## Debug Reindex

The debug receiver can force a full reindex:

```bash
adb shell am broadcast \
  -n com.darkesttrololo.memeizer/.DebugReindexReceiver \
  -a com.darkesttrololo.memeizer.DEBUG_REINDEX \
  --ez force_reindex true
```

If the package was force-stopped, launch the app once before sending the broadcast.

## OCR Notes

PaddleOCR NCNN assets are packaged under `app/src/main/assets`:

- `PP_OCRv5_mobile_det.ncnn.bin`
- `PP_OCRv5_mobile_det.ncnn.param`
- `PP_OCRv5_mobile_rec.ncnn.bin`
- `PP_OCRv5_mobile_rec.ncnn.param`

The NCNN wrapper is vendored as:

```text
third_party/ncnnAndroidPPOCR
```

The recognizer dictionary is compiled into:

```text
third_party/ncnnAndroidPPOCR/src/main/jni/ppocrv5_dict.h
```

This dictionary includes the space token required by the `eslav_PP-OCRv5_mobile_rec` recognizer.

For PaddleOCR Cyrillic output, visually equivalent Latin glyphs are normalized to Cyrillic in stored OCR text. For example, Latin `H`, `O`, `B`, `C`, `P`, `K` are mapped to Cyrillic `Н`, `О`, `В`, `С`, `Р`, `К`. Symbols without a Cyrillic homoglyph are left unchanged. ML Kit Latin output is not normalized this way.

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

- `third_party/ncnnAndroidPPOCR` is vendored and large because it includes native NCNN/OpenCV dependencies.
- `DebugReindexReceiver` is exported for adb-driven testing.
