# Repository Guidelines

## Project Structure & Module Organization

Memeizer is an Android app for searching a local meme gallery using on-device OCR.

- `app/src/main/java/com/darkesttrololo/memeizer/`: Kotlin application code. `ui/` contains Compose screens and ViewModels; `data/` contains Room storage, folder access, OCR, indexing, and search. `AppContainer` wires dependencies.
- `app/src/main/res/`: Android resources; `app/src/main/assets/`: packaged PaddleOCR models.
- `third_party/ncnnAndroidPPOCR/`: vendored OCR library with Kotlin wrappers, C++ sources, and native dependencies under `src/main/jni/`. Existing sample tests live in its `src/test/` and `src/androidTest/` directories.

## Build, Test, and Development Commands

Use JDK 17, Android SDK platforms 35 (app) and 33 (library), NDK `29.0.14206865`, and CMake. Run commands from the repository root:

- `./gradlew assembleDebug`: build the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:installDebug`: install on a connected device or emulator running API 31 or newer.
- `adb shell am start -n com.darkesttrololo.memeizer/.MainActivity`: launch the app.
- `./gradlew :app:lintDebug`: run Android Lint.
- `./gradlew :ncnnAndroidPPOCR:testDebugUnitTest`: run the library's JVM tests.
- `./gradlew :ncnnAndroidPPOCR:connectedDebugAndroidTest`: run library instrumentation tests on a connected device.

## Coding Style & Naming Conventions

Follow official Kotlin style, configured through `kotlin.code.style=official`, with four-space indentation. Use `PascalCase` for classes and Compose screen functions, `camelCase` for ordinary functions and properties, and lowercase package names. Match existing suffixes such as `Screen`, `ViewModel`, `Repository`, and `OcrEngine`. Keep data access in repositories and UI state in ViewModels. Use Android Studio's Kotlin formatter; no dedicated ktlint or Detekt configuration is present.

## Testing Guidelines

Whenever you change the UI, you may use Mobile MCP to test it on a connected device or emulator without asking for additional permission.

The vendored module uses JUnit 4 and AndroidX instrumentation with Espresso dependencies. Tests currently cover template examples; the app has no test suite or coverage threshold. Place new app tests in `app/src/test/` or `app/src/androidTest/`, configuring dependencies and the instrumentation runner as needed. Name classes `*Test` and methods after expected behavior. Manually verify folder selection, indexing, Cyrillic/Latin search, and empty-query browsing for relevant changes.

## Commit & Pull Request Guidelines

History favors short imperative subjects such as `feat(ui): add settings in drawer` and `feat(scheduler): reindex in inactive time`, with some unprefixed maintenance commits. Prefer scoped subjects. PRs should describe behavior changes, link relevant issues, report validation, and include screenshots for UI changes.

## Privacy & Configuration

Preserve explicit folder selection and on-device processing. Keep SDK paths in ignored `local.properties`; exclude personal images, database dumps, and credentials from commits.
