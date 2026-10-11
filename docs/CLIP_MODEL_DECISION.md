# CLIP model decision and research results

Decision date: **2026-10-11**. Status: **accepted for current use**.

## Decision

Keep the existing bundled **TinyCLIP FP32** model and continue development. The
project owner is satisfied with the current behavior and does not have resources
for the proposed larger comparative benchmark. That benchmark is deferred; it is
not a prerequisite for retaining the current model.

Checkpoint: `wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M`.
Revision: `95ec8197b3f2fe7f747865c61ca556cf0768b2f7`.
Application model version: `tinyclip-95ec8197b3f2-fp32-v1`.
Both encoders produce 512-dimensional vectors and are bundled in the APK.

Keep offline inference, automatic indexing, the separate English semantic-search
mode with up to 30 results, and the existing OCR/text-search mode. No model picker
or model replacement is planned now. Revisit either option only if actual use
shows that the current model is insufficient; there is no scheduled follow-up.

This is a product decision based on available evidence, not a claim that TinyCLIP
has won a comparative quality benchmark. The asset manifest's provisional
selection status refers to the latter qualification.

## Candidate measurements

The existing exported candidates were measured on an AMD Ryzen 7 9800X3D Linux
host, using ONNX Runtime CPU with two intra-op threads. These are short smoke
measurements, not Android phone timings or meme-retrieval quality scores.

| Candidate | Precision | Combined encoder bytes | Host text p95 | Host image p95 |
| --- | --- | ---: | ---: | ---: |
| TinyCLIP | FP32 | 337,032,651 | 10.1 ms | 14.1 ms |
| TinyCLIP | INT8 | 165,799,339 | 3.9 ms | 5.7 ms |
| OpenAI CLIP ViT-B/32 | FP32 | 605,360,442 | 17.9 ms | 33.6 ms |
| OpenAI CLIP ViT-B/32 | INT8 | 235,613,173 | 7.2 ms | 10.2 ms |

Source: [host comparison report](../tools/clip/reports/host-comparison.json).
Encoder sizes exclude OCR, native libraries, and other APK contents.

TinyCLIP FP32 already has a working Android integration and uses smaller encoder
files than CLIP B/32 FP32. INT8 offers smaller files and faster host inference,
but changes embeddings: the minimum text cosine similarity to the corresponding
FP32 reference was about 0.897 for TinyCLIP and 0.915 for CLIP B/32 on the smoke
fixtures. Those values do not measure retrieval accuracy; they motivate checking
search quality before adopting INT8. No comparative meme-quality advantage for
CLIP B/32 has been established in this project.

## Local Android retrieval check: 2026-10-11

The project owner supplied 20 JPEG images in the private local `clip_test/`
directory. The assistant inspected them and proposed 40 English scene-description
queries, two per image, with one expected relevant image per query. Labels were
written before running the queries and have not been independently human-reviewed.

The check used the existing debug backend harness and the application's real
`SearchRepository` on the local Medium Phone emulator, Android 12 / API 31,
x86_64. The test images were copied into a subfolder of the already selected demo
folder, leaving the original seven demo images in the gallery.

| Check | Observed result |
| --- | --- |
| New CLIP image embeddings | 20/20 ready, no failures |
| Complete gallery CLIP index | 27/27 ready, no model error |
| Complete gallery OCR indexing | 27 images `INDEXED`; scan task `SUCCEEDED` |
| Expected image ranked first | 40/40 queries, including the seven demo distractors |
| Hit@1 / Hit@3 / Hit@5 / Hit@10 | 100% for this query set |
| New images with empty OCR output | 11; their scene-description queries still retrieved them first |
| Offline semantic checks | Three queries retrieved the expected image first with Wi-Fi and mobile data disabled |
| Existing Cyrillic/Latin text search | `дедлайн`: two demo results; `coffee`: one demo result |
| Text search on a new image | `айтишница`: one expected result |
| Empty-query browsing | All 27 active images returned |
| Console regression checks | Four model-selection tests and six backend-harness tests passed |

Network settings were restored after the offline checks. Initial emulator runs
terminated with exit code 139. A cold start without snapshots, using SwiftShader
and disabled Vulkan, allowed the check to finish. This workaround does not
establish the root cause of the emulator crash.

Private images, per-query relevance labels, full query results, device URIs, and
database details remain in the ignored local `clip_test/` directory. This document
records aggregate results; reproducing retrieval scores requires that private set.
The full private report is `clip_test/android-results.json`.

## Evidence limits and future options

The 20-image check supports the current scene-description search behavior. It
does not establish accuracy on a large collection, abstract intent, meme humor,
or a separately sampled set of queries. Hit@10 is especially weak evidence with
only 20 test images. The 40 queries are paired descriptions of 20 scenes, not
40 independent scenes. No CLIP B/32 or INT8 variant was evaluated on this set.

Previously recorded device measurements are also limited to an x86_64 emulator:
about 2.8 seconds for cold initialization including initial asset copying, about
211 ms p95 for embedding plus retrieval over 10,000 synthetic vectors, and about
483 MiB sampled peak process PSS including test overhead. The recorded debug APK
was 418,533,921 bytes. These are historical measurements, not newly measured
flagship performance. See [the model guide](../tools/clip/README.md) and
[device report](../tools/clip/reports/device-measurements.json).

If a concrete limitation emerges later, consider replacing the bundled model or
adding model selection. Model-size pressure makes TinyCLIP INT8 a candidate;
search-quality failures justify comparing other models on the failing examples.
Neither change is authorized by this decision.

The earlier proposal of at least 300 images and 100 human-reviewed English
queries remains documented as a possible broader comparison, including the
2-percentage-point INT8 tolerance and 5-percentage-point size/quality selection
rule. It is deferred rather than a current acceptance blocker. Runtime checks on
arm64 hardware and a 16 KiB-page device remain outside the evidence reported here.

Model references: [TinyCLIP model card](https://huggingface.co/wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M)
and [OpenAI CLIP B/32 model card](https://huggingface.co/openai/clip-vit-base-patch32).
