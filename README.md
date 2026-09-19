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

The project pins Android Gradle Plugin (AGP) `8.8.2`, Gradle `9.0.0`,
Kotlin `2.0.21`, and KSP `2.0.21-1.0.28`.

- Compose lint checks [require AGP/Lint 8.8.2 or newer](https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.9.0).
  Reverting AGP to `8.7.3` would require upgrading Lint separately.
  With the current dependencies, AGP `8.7.3` reproduces an
  `IncompatibleClassChangeError` in `ComposableFlowOperatorDetector`
  (`KaFunctionCall`: class found where an interface was expected).
- Gradle `8.10.2` is the [minimum and default version for AGP 8.8](https://developer.android.com/build/releases/agp-8-8-0-release-notes#compatibility).
  The wrapper uses Gradle `9.0.0`, which passed local debug assembly, lint,
  and JVM tests with AGP `8.8.2` and JDK 17.
- Lint explicitly selects K2 UAST via `android.lint.useK2Uast=true`.
  This is separate from the Kotlin compiler version.
- Kotlin `2.0.21` is retained with its matching Compose compiler plugin and KSP.
  Gradle `9.0.0` and AGP `8.8.2` are outside its [fully supported range](https://kotlinlang.org/docs/gradle-configure-project.html).
  Successful local builds do not extend that support range; a future Kotlin upgrade
  should update and validate the compiler, Compose compiler plugin, and KSP together.

The app version for the first release is `versionName 0.1.0` and
`versionCode 1`. Every later release must increase `versionCode`; use the
next user-facing version in `versionName` (for example, `0.1.1` or `0.2.0`).

## Build

From the repository root:

```bash
export JAVA_HOME=/absolute/path/to/jdk-17
./gradlew --version
./gradlew assembleDebug
```

Use the wrapper and check that `./gradlew --version` reports Gradle `9.0.0`
and the intended JVM. On Arch Linux, a typical JDK 17 path is
`/usr/lib/jvm/java-17-openjdk`. Configure Android Studio's **Gradle JDK**
separately: the IDE and terminal can otherwise use different Java versions.
`sourceCompatibility` and `jvmTarget` control compilation targets, not the JVM
that launches Gradle.

Do not launch this wrapper with a system JDK 26: [Gradle requires 9.4.0 or
newer to run on Java 26](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime).
An unsupported JVM can fail during configuration with only a Java version
string such as `26.0.2.1`. Select JDK 17 instead of changing Gradle
just to match the system Java installation.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Local Release Build And Signing

Release signing is deliberately local-only. The keystore and the properties
file must stay outside the repository. The default properties path is:

```text
~/.config/memeizer/signing.properties
```

The file must contain paths and secrets, for example:

```properties
storeFile=/absolute/path/to/memeizer-release.jks
storePassword=replace-with-keystore-password
keyAlias=memeizer
keyPassword=replace-with-key-password
```

Create the permanent key once with `keytool`, store it in an encrypted backup,
and never commit it:

```bash
mkdir -p ~/.config/memeizer
keytool -genkeypair -v \
  -keystore ~/.config/memeizer/memeizer-release.jks \
  -alias memeizer -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass 'choose-a-long-password' -keypass 'choose-a-long-password' \
  -dname 'CN=Memeizer Release, OU=Memeizer, O=Memeizer'
chmod 600 ~/.config/memeizer/memeizer-release.jks
```

Put the passwords in `signing.properties` with mode `600`. Back up the JKS
file and both passwords separately (for example, an encrypted offline copy
and a password manager). Losing the key makes future updates impossible for
the same Android package; do not rotate it for a patch release.

Build the signed APK:

```bash
./gradlew clean assembleRelease
```

If the keystore or any required property is missing, the release packaging
task fails with an explicit signing-configuration error. To use another
properties file without putting it in the repository:

```bash
./gradlew -PmemeizerSigningProperties=/absolute/path/signing.properties assembleRelease
```

Verify the result before attaching it to a GitHub Release. The Android SDK
build-tools provide `apksigner`, `zipalign`, and `apkanalyzer`:

```bash
APK=app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 "$APK"
apksigner verify --verbose "$APK"
apkanalyzer manifest application-id "$APK"
apkanalyzer manifest version-name "$APK"
apkanalyzer manifest version-code "$APK"
sha256sum "$APK" > release-artifacts/Memeizer-v0.1.0.apk.sha256
```

The release APK must report `debuggable=false`, contain the four files listed
in [Packaged OCR Assets](#packaged-ocr-assets), and pass `zipalign -P 16`.
The release currently packages only `arm64-v8a` and `x86_64`: the bundled
32-bit ML Kit/native OCR binaries are ELF-aligned for 4 KiB pages and are
therefore excluded from the 16 KiB-compatible release.
For a 16 KB-page device or emulator, install this exact APK and exercise OCR;
the native module is built with flexible page-size support. If APK Analyzer
or the 16 KB environment reports an incompatible native library, update or
rebuild that dependency before publishing.

Do not install a release APK over an existing debug installation: the debug
key is different. Uninstall debug first, or use a clean package. For the
upgrade test, install signed build A, create folders and indexed data, then
install signed build B with a higher `versionCode` and confirm the folders,
database, search results, and scheduled indexing remain present.

## Release checklist

Before calling a release ready, manually verify folder selection, Russian and
English OCR, empty and non-empty search, image viewing, image sharing, and
background indexing on the target device. Run the JVM tests and lint, inspect
all remaining warnings, and record the APK SHA-256 next to the release asset.
The release description for `v0.1.0` is kept in
[`RELEASE_NOTES_v0.1.0.md`](RELEASE_NOTES_v0.1.0.md). GitHub upload is manual;
this repository does not add GitHub Actions.

If you use the Android Studio bundled JBR directly, check its Java version
first; JBR 17 or 21 is compatible with Gradle `9.0.0`, but newer
Android Studio installations may bundle a different JVM:

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

### Backend Harness (Debug Only)

Install the current debug APK first. The harness requires Python 3 and `adb` on
your PATH; it does not require sqlite3 or root. Run from the repository root:

```bash
tools/backend status
tools/backend search "челябинск"
tools/backend search ""                 # browse the gallery
tools/backend image 42
tools/backend index --wait
tools/backend index --force --wait --timeout 600
tools/backend --serial emulator-5554 status
```

`--serial` (before the command) or `ANDROID_SERIAL` selects a device. With neither,
adb's default device selection applies. Commands print JSON to stdout; wait progress
goes to stderr. Exit codes: `0` success, `1` command/job failure, `2` invalid CLI
arguments, `124` wait timeout, `130` interrupted. Timeout or interruption does not
cancel work. `index` without `--wait` returns the queued work ID immediately.

`status` reports selected/disabled folders and persisted read grants, image counts
grouped by active flag and indexing status, and manual/periodic WorkManager jobs.
A persisted grant does not guarantee that the underlying folder is still available.
`search` uses the same repository as the UI, including query normalization and the
100-result search / 500-image gallery limits. `image` returns the image, OCR and FTS
records, including inactive cached images; a missing ID returns an error.

`index` appends a new manual job without cancelling existing jobs. It processes
selected folders incrementally unless `--force` is supplied. `--wait` follows that
specific job, including time spent queued (default timeout: 300 seconds). Job success
does not imply that every image passed OCR: inspect `status` and `image` for per-image
failures. Worker-level failures include an error message. If an ADB request fails
while submitting a job, check `status` before retrying: it may already be queued.

The provider and legacy reindex receiver exist only in debug builds and require
the shell's `android.permission.DUMP` permission. No network server is started.

Host-side transport/wait regression tests:

```bash
python3 -B -m unittest discover -s tools -p 'test_*.py'
```

### Manual Database Copy

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
