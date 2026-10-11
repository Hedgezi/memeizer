# CLIP assets and verification

The APK bundles both encoders, vocabulary, merges and licenses for one checkpoint.
The application never downloads models. **TinyCLIP FP32 is the accepted current
product choice as of 2026-10-11**. It passed a small local retrieval check, but has
not been qualified as the best model by a comparative relevance benchmark.
See [the decision and research results](../../docs/CLIP_MODEL_DECISION.md).
The checkpoint SHA, export tools, tensor dimensions, preprocessing and SHA-256 hashes
are recorded in `app/src/main/assets/clip/manifest.json`.

Large ONNX files are generated locally and ignored by Git. They are present in this
workspace and the built APK. On a fresh checkout, prepare them before invoking Gradle;
`verifyClipAssets` prevents building an APK without its local model.

## Reproduce the assets

Use a CPU-capable PyTorch installation. The recorded export used Python 3.14.7,
PyTorch 2.14.0 and the pinned versions below. A different PyTorch/Pillow version can
change export bytes or reference preprocessing, so compare new hashes and rerun parity
checks before replacing the manifest.

```bash
python3 -m venv --system-site-packages /tmp/memeizer-clip-venv
/tmp/memeizer-clip-venv/bin/pip install -r tools/clip/requirements.txt
HF_HOME=/tmp/memeizer-clip-hf /tmp/memeizer-clip-venv/bin/python tools/clip/export.py tinyclip \
  --revision 95ec8197b3f2fe7f747865c61ca556cf0768b2f7 --output /tmp/memeizer-clip-models/tinyclip
HF_HOME=/tmp/memeizer-clip-hf /tmp/memeizer-clip-venv/bin/python tools/clip/export.py clip \
  --revision 3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268 --output /tmp/memeizer-clip-models/clip
python3 tools/clip/install_assets.py /tmp/memeizer-clip-models/tinyclip --precision fp32
cp /tmp/memeizer-clip-models/tinyclip/reference/* app/src/androidTest/assets/clip-reference/
```

Each encoder includes its projection into the shared 512-dimensional space. Inputs
are fixed batch 1, RGB NCHW 224×224 and int64 token IDs/mask of length 77. The output
is FP32 and is normalized in Kotlin. INT8 dynamically quantizes supported constant
MatMul weights, leaving embedding lookups and unsupported operations in FP32.

Tokenizer behavior follows the checkpoint's **CLIPTokenizerFast**: NFC, Unicode
whitespace collapse and lowercase, byte BPE, BOS/EOS, EOS padding and truncation to
77 tokens. It does not unescape HTML or normalize typographic quotation marks.
Image decoding applies EXIF orientation, converts to sRGB, resizes the shorter side
using antialiased bicubic sampling, center crops, rescales and normalizes. Android's
8-bit bicubic intermediates are allowed at most one RGB level of difference from
Pillow in reference fixtures (0.016 after normalization); normalized image embeddings
must differ by at most 0.003 per component. Text embedding tolerance is 0.0002.

```bash
/tmp/memeizer-clip-venv/bin/python tools/clip/compare_exports.py \
  /tmp/memeizer-clip-models/tinyclip /tmp/memeizer-clip-models/clip \
  --output /tmp/clip-host-comparison.json
python3 -B -m unittest discover -s tools/clip -p 'test_*.py'
```

## Quality evaluation

The procedure below is retained for a future model comparison. It is deferred and
does not block continued development with the accepted TinyCLIP FP32 model. Revisit
it if real usage exposes quality limitations or model size becomes a problem.

Keep the dataset outside the repository. Its JSON format is:

```json
{
  "images": [{"id": "meme-001", "path": "/private/memes/001.png"}],
  "queries": [{"text": "a surprised cat", "relevant": ["meme-001"]}]
}
```

Use at least 300 distinct memes and 100 English queries, with human-reviewed relevance
labels. Query descriptions should cover visual scenes, unlabeled images and captions
in different languages. Synthetic fixtures and vector benchmarks are not a relevance
dataset and cannot establish hit@10.

```bash
HF_HOME=/tmp/memeizer-clip-hf /tmp/memeizer-clip-venv/bin/python tools/clip/evaluate.py \
  --dataset /private/memes/labels.json \
  --models /tmp/memeizer-clip-models/tinyclip /tmp/memeizer-clip-models/clip \
  --output /tmp/clip-quality.json
```

The selector rejects INT8 if its hit@10 loss against its own FP32 model exceeds
2 percentage points. It then selects the smallest remaining encoder pair within
5 percentage points of the best quality; equal sizes use latency. The report labels
host measurements explicitly. Confirm latency, memory, offline operation and ABI
support on the target Android flagship before qualifying the selected variant.
Install that single variant with `install_assets.py`, update its licenses and reference
fixtures, and rebuild. Changing `model_version` rebuilds only the CLIP index.

## Measurements from 2026-10-07

See [host comparison](reports/host-comparison.json) and
[Android measurements](reports/device-measurements.json). Host CPU:
AMD Ryzen 7 9800X3D, Linux x86_64, two intra-op threads per session.

| Candidate | Variant | Encoder bytes | Host text p95 | Host image p95 |
| --- | --- | ---: | ---: | ---: |
| TinyCLIP | FP32 | 337,032,651 | 10.1 ms | 14.1 ms |
| TinyCLIP | INT8 | 165,799,339 | 3.9 ms | 5.7 ms |
| OpenAI CLIP B/32 | FP32 | 605,360,442 | 17.9 ms | 33.6 ms |
| OpenAI CLIP B/32 | INT8 | 235,613,173 | 7.2 ms | 10.2 ms |

These short CPU smoke measurements use 10 query fixtures and a synthetic portrait,
not a meme quality benchmark. INT8 text embeddings differ noticeably from FP32;
smaller files alone do not authorize selecting INT8.

Device: **Medium Phone emulator, sdk_gphone64_x86_64, Android 12 / API 31,
x86_64, 4096-byte memory pages**, running on the host above. TinyCLIP FP32:

- Cold encoder initialization including first asset copy: 2,823 ms.
- Full warm query embedding plus exact Room retrieval on 10,000 synthetic
  normalized 512-dimensional vectors: p95 **211 ms** (20 runs; excludes debounce).
- Sampled process peak PSS during model and retrieval tests: **494,680 KiB**
  (483 MiB). Sampling every 25 ms can miss a shorter peak; includes test process overhead.
- Four reference image embeddings, including EXIF rotation: 32–82 ms per image.
- Debug APK: **418,533,921 bytes** (399.1 MiB), including existing OCR and two ABIs.

Flagship latency, real 10,000-image indexing time, hit@10 and quality-qualified model
selection remain unmeasured. The new ONNX Runtime 1.25.0 `.so` libraries have LOAD
alignment `0x4000` for arm64-v8a and x86_64; the complete APK passes
`zipalign -c -P 16 4`. Actual inference was tested only on x86_64 with 4 KiB pages;
arm64 and a 16 KiB-page device still require runtime verification.

## Android checks

JVM tests cover vector normalization/encoding, corruption, ties, top-30, debounce,
request cancellation and model-error snapshots. Instrumentation tests cover migration
with folder/OCR/FTS preservation, folder selection races, changed content, isolated
file failures, process-cancellation recovery, model changes, bounded runs, 256-row
search pages and the Python reference fixtures.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:testDebugUnitTest \
  :ncnnAndroidPPOCR:testDebugUnitTest assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
# Install and run without the Gradle test task's uninstall/cleanup step:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.darkesttrololo.memeizer.test/androidx.test.runner.AndroidJUnitRunner
```

Do not run Mobile MCP or another UiAutomation client during instrumentation UI tests.
A stale UiAutomation registration was cleared by restarting the emulator. All 49 app
instrumentation cases passed across the model/data run and isolated viewer retries;
the vendored module's instrumentation test passed. Final JVM results: 14 app tests,
1 vendored-module test; Lint: 0 errors, 19 existing warnings. Backend CLI tests: 6;
model selection tests: 4.

Use the existing debug harness to inspect progress and compare both modes:

```bash
tools/backend status
tools/backend search --mode semantic "a surprised cat"
tools/backend search "кот"
tools/backend search "hello"
tools/backend search ""
```

CLIP work runs after startup and successful folder scans (including periodic scans).
Each run processes at most 64 images, stopping before starting another image after
8 minutes. Results are persisted individually. Current failures are retained until
the image's content key or model changes; model failures are retried on restart or
another successful folder scan. Searching does not require successful OCR.

## Manual offline smoke check

With Wi-Fi and mobile data disabled, the selected demo folder gained three
caption-free shape fixtures. Incremental scanning automatically brought the CLIP
index from 4/4 to 7/7 without reprocessing unchanged OCR. `a red circle` ranked
`red-circle.png` first; its OCR text was empty. Text search for `дедлайн` returned
two known demo cards, `coffee` returned one, and empty queries returned all seven.
This verifies basic behavior and independence from OCR, not meme relevance quality.
Network settings were restored after testing.

[Semantic mode screenshot](reports/semantic-search-en.png) ·
[Text mode screenshot](reports/text-search-en.png)

Rapid IME edits are kept in synchronous Compose input state; a manual fast-entry
check confirmed the complete `coffee` query and its text result after this fix.
The first Gradle instrumentation run uninstalled the app and removed emulator app
data during cleanup. The demo folder was selected again through SAF; source images
remained on disk. Subsequent tests used direct instrumentation to keep the installation.
