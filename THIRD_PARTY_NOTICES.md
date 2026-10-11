# Third-party notices

Memeizer includes or links the following third-party components. The app's
own source code has no license selected yet.

## Apache License 2.0

- AndroidX, Jetpack Compose, Room, WorkManager, Navigation, and related
  Android libraries.
- Coil 2.
- Kotlin and kotlinx.coroutines.
- PaddleOCR / PP-OCRv5 model assets and the vendored PaddleOCR wrapper,
  according to the PaddleOCR project notice.

The applicable short license notice is in
[`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt). The authoritative
license text and each dependency's notices are available from the component
source repositories linked from the dependency declarations.

## BSD 3-Clause

- Tencent ncnn native libraries and the ncnn-based OCR wrapper.
- OpenCV Mobile native libraries. The upstream license is also retained at
  `third_party/ncnnAndroidPPOCR/src/main/jni/opencv-mobile-2.4.13.7-android/LICENSE`.

The applicable notice is in [`LICENSES/BSD-3-Clause.txt`](LICENSES/BSD-3-Clause.txt).

## Google ML Kit

The Latin/English OCR path uses Google's on-device ML Kit Text Recognition
SDK. Distribution is subject to Google's ML Kit terms and the notices shipped
with the SDK; see <https://developers.google.com/ml-kit/terms> and
<https://developers.google.com/ml-kit/vision/text-recognition>. ML Kit is
not redistributed as source code by this repository.

## Model provenance

The four packaged `PP_OCRv5_mobile_*` files are PaddleOCR model assets used
by the ncnn wrapper. Keep this notice and the PaddleOCR attribution with any
binary redistribution, and re-check the upstream model terms before changing
the model files or adding additional models.

## MIT

- TinyCLIP ViT-40M/32 Text-19M LAION400M, checkpoint
  `95ec8197b3f2fe7f747865c61ca556cf0768b2f7`, from
  [wkcn/TinyCLIP](https://huggingface.co/wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M).
  The model license is retained in the APK at
  [`app/src/main/assets/clip/LICENSE-TinyCLIP.txt`](app/src/main/assets/clip/LICENSE-TinyCLIP.txt).
- ONNX Runtime 1.25.0, CPU Android runtime. The license is retained at
  [`app/src/main/assets/clip/LICENSE-ONNXRuntime.txt`](app/src/main/assets/clip/LICENSE-ONNXRuntime.txt).

The APK includes one local CLIP encoder pair and checkpoint vocabulary/BPE merges.
Its provenance, checksums, tool versions, preprocessing and provisional selection
status are recorded in
[`app/src/main/assets/clip/manifest.json`](app/src/main/assets/clip/manifest.json).
OpenAI CLIP ViT-B/32 was exported for comparison; its weights are not packaged.
