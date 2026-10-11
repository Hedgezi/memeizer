#!/usr/bin/env python3
"""Compare exported FP32/INT8 runtime behavior. This smoke benchmark has no relevance labels."""
import argparse
import json
from pathlib import Path
import time

import numpy as np
import onnxruntime as ort


def normalized(array):
    assert np.isfinite(array).all() and np.linalg.norm(array) > 0
    return array / np.linalg.norm(array, axis=-1, keepdims=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("models", nargs=2, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    variants = []
    for directory in args.models:
        manifest = json.loads((directory / "manifest.json").read_text())
        refs = json.loads((directory / "reference/tokens.json").read_text())
        image_ref = json.loads((directory / "reference/images.json").read_text())[0]
        pixels = np.fromfile(directory / "reference/portrait.pixels.bin", dtype="<f4").reshape(1, 3, 224, 224)
        for precision, suffix in [("fp32", ".onnx"), ("int8", ".int8.onnx")]:
            start = time.perf_counter()
            text = ort.InferenceSession(str(directory / ("text" + suffix)), options, providers=["CPUExecutionProvider"])
            vision = ort.InferenceSession(str(directory / ("vision" + suffix)), options, providers=["CPUExecutionProvider"])
            cold_ms = (time.perf_counter() - start) * 1000
            text_cosines, max_errors, text_times = [], [], []
            for ref in refs:
                start = time.perf_counter()
                actual = normalized(text.run(None, {"input_ids": np.array([ref["ids"]], dtype=np.int64),
                                                   "attention_mask": np.array([ref["mask"]], dtype=np.int64)})[0])[0]
                text_times.append((time.perf_counter() - start) * 1000)
                expected = np.array(ref["embedding"])
                text_cosines.append(float(actual @ expected))
                max_errors.append(float(np.max(np.abs(actual - expected))))
                if precision == "fp32":
                    np.testing.assert_allclose(actual, expected, atol=.0002, rtol=.0002)
            image_times = []
            for _ in range(10):
                start = time.perf_counter()
                actual_image = normalized(vision.run(None, {"pixel_values": pixels})[0])[0]
                image_times.append((time.perf_counter() - start) * 1000)
            expected_image = np.array(image_ref["embedding"])
            if precision == "fp32":
                np.testing.assert_allclose(actual_image, expected_image, atol=.0002, rtol=.0002)
            variants.append({"model": manifest["checkpoint"], "revision": manifest["revision"], "precision": precision,
                             "encoder_bytes": sum((directory / (name + suffix)).stat().st_size for name in ["vision", "text"]),
                             "host_cold_ms": cold_ms, "host_text_p95_ms": float(np.percentile(text_times, 95)),
                             "host_image_p95_ms": float(np.percentile(image_times, 95)),
                             "text_cosine_min_to_python_fp32": min(text_cosines), "text_max_absolute_error": max(max_errors),
                             "image_cosine_to_python_fp32": float(actual_image @ expected_image)})
            del text, vision
    report = {"target": "host CPU, 2 intra-op threads", "variants": variants,
              "quality_status": "No labeled meme dataset; hit@10 and winner selection unavailable"}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
