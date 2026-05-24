PaddleOCR assets are not committed yet.

Expected layout for the Cyrillic engine:

- paddleocr/models/cyrillic_PP-OCRv3/det.nb
- paddleocr/models/cyrillic_PP-OCRv3/rec.nb
- paddleocr/models/cyrillic_PP-OCRv3/cls.nb
- paddleocr/labels/cyrillic_dict.txt

The Java/JNI wrapper is based on the official PaddleOCR Android demo:
https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/android_demo

Native build is opt-in for now because the local SDK must have NDK and CMake installed.
Build with `-PenablePaddleOcr=true` after adding:

- app/src/main/paddleocr/PaddleLite
- app/src/main/paddleocr/OpenCV
