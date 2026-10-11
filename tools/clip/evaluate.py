#!/usr/bin/env python3
"""Evaluate a private JSON dataset without copying images or labels into the repository.

Dataset: {"images": [{"id": "cat", "path": "/private/cat.png"}],
          "queries": [{"text": "a surprised cat", "relevant": ["cat"]}]}
"""
import argparse
import json
from pathlib import Path
import time

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps
from transformers import CLIPProcessor


def normalized(values):
    return values / np.linalg.norm(values, axis=-1, keepdims=True)


def evaluate(directory, dataset):
    manifest = json.loads((directory / "manifest.json").read_text())
    processor = CLIPProcessor.from_pretrained(manifest["checkpoint"], revision=manifest["revision"])
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    variants = []
    for precision, suffix in [("fp32", ".onnx"), ("int8", ".int8.onnx")]:
        started = time.perf_counter()
        vision = ort.InferenceSession(str(directory / ("vision" + suffix)), options, providers=["CPUExecutionProvider"])
        text = ort.InferenceSession(str(directory / ("text" + suffix)), options, providers=["CPUExecutionProvider"])
        cold_seconds = time.perf_counter() - started
        vectors = []
        started = time.perf_counter()
        for entry in dataset["images"]:
            with Image.open(entry["path"]) as image:
                pixels = processor(images=ImageOps.exif_transpose(image).convert("RGB"), return_tensors="np")["pixel_values"]
            vectors.append(normalized(vision.run(None, {"pixel_values": pixels})[0])[0])
        index_seconds = time.perf_counter() - started
        vectors = np.stack(vectors)
        hits, times = [], []
        ids = [entry["id"] for entry in dataset["images"]]
        for query in dataset["queries"]:
            started = time.perf_counter()
            inputs = processor.tokenizer(query["text"], padding="max_length", truncation=True, max_length=77, return_tensors="np")
            embedding = normalized(text.run(None, {k: inputs[k] for k in ["input_ids", "attention_mask"]})[0])[0]
            scores = vectors @ embedding
            top = sorted(range(len(ids)), key=lambda i: (-float(scores[i]), ids[i]))[:10]
            times.append(time.perf_counter() - started)
            hits.append(bool(set(query["relevant"]) & {ids[i] for i in top}))
        variants.append({"model": manifest["checkpoint"], "revision": manifest["revision"], "precision": precision,
                         "hit_at_10": sum(hits) / len(hits), "encoder_bytes": sum((directory / (name + suffix)).stat().st_size for name in ["vision", "text"]),
                         "host_cold_seconds": cold_seconds, "host_index_seconds": index_seconds,
                         "host_search_p95_seconds": float(np.percentile(times, 95))})
    return variants


def select(variants):
    allowed = []
    for item in variants:
        fp32 = next(v for v in variants if v["model"] == item["model"] and v["precision"] == "fp32")
        if item["precision"] == "fp32" or fp32["hit_at_10"] - item["hit_at_10"] <= .02000001:
            allowed.append(item)
    best = max(v["hit_at_10"] for v in allowed)
    eligible = [v for v in allowed if best - v["hit_at_10"] <= .05000001]
    return min(eligible, key=lambda v: (v["encoder_bytes"], v["host_search_p95_seconds"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--models", type=Path, nargs=2, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    dataset = json.loads(args.dataset.read_text())
    assert len(dataset["images"]) >= 300 and len(dataset["queries"]) >= 100, "Need >=300 memes and >=100 labeled queries"
    ids = {entry["id"] for entry in dataset["images"]}
    assert len(ids) == len(dataset["images"]), "Duplicate image IDs"
    for query in dataset["queries"]:
        assert query["text"].strip() and query["relevant"] and set(query["relevant"]) <= ids, "Invalid labels"
    variants = [variant for model in args.models for variant in evaluate(model, dataset)]
    report = {"image_count": len(ids), "query_count": len(dataset["queries"]), "variants": variants,
              "selected": select(variants), "measurement_target": "host CPU; Android acceptance measurements remain required"}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
