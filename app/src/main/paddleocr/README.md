Place official PaddleOCR native dependencies here before enabling native build:

- PaddleLite/cxx/include
- PaddleLite/cxx/libs/<abi>/*.so
- OpenCV/sdk/native/jni

Use the official Android demo assets/libs as the source:
https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/android_demo

The native dependencies are intentionally not committed because they are large.
The current integration expects arm64-v8a PaddleLite/OpenCV libraries and is only
runtime-testable on an ARM64 Android device or emulator.

Default x86-friendly build:

```sh
./gradlew assembleDebug
```

PaddleOCR opt-in build:

```sh
./gradlew assembleDebug -PenablePaddleOcr=true
```
