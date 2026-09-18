# Memeizer v0.1.0 (pre-release)

First local-gallery release of Memeizer.

## Included

- Search a user-selected image gallery by locally recognized meme text.
- Russian/Cyrillic OCR with bundled PaddleOCR NCNN models.
- Latin/English OCR with on-device Google ML Kit Text Recognition.
- Empty-query browsing, image preview, and image sharing.
- Background indexing through WorkManager.
- Explicitly selected folders; no account, server-side indexing, or cloud
  backup of app data.

## Compatibility

- Android 12 / API 31 or newer.
- APK: `Memeizer-v0.1.0.apk`
- SHA-256: see the accompanying `.sha256` file.

## Known limitations

- This is an early pre-release; OCR quality depends on image quality and
  typography.
- R8/minification and APK size reduction are intentionally deferred.
- GitHub Actions and automatic publishing are intentionally not included.
