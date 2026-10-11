#!/usr/bin/env python3
"""Export both projected CLIP encoders, quantize MatMul weights, and save reference fixtures."""
import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import shutil

import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image, ImageOps
import torch
from transformers import CLIPModel, CLIPProcessor
from huggingface_hub import HfApi

CANDIDATES = {
    "tinyclip": "wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M",
    "clip": "openai/clip-vit-base-patch32",
}


def normalize(value):
    array = np.asarray(value, dtype=np.float32)
    return array / np.linalg.norm(array, axis=-1, keepdims=True)


class Vision(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.encoder = model.vision_model
        self.projection = model.visual_projection

    def forward(self, pixel_values):
        return self.projection(self.encoder(pixel_values=pixel_values).pooler_output)


class Text(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.encoder = model.text_model
        self.projection = model.text_projection

    def forward(self, input_ids, attention_mask):
        return self.projection(self.encoder(input_ids=input_ids, attention_mask=attention_mask).pooler_output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", choices=CANDIDATES)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--revision", help="Immutable checkpoint revision; defaults to resolved current SHA")
    args = parser.parse_args()
    checkpoint = CANDIDATES[args.candidate]
    revision = args.revision or HfApi().model_info(checkpoint).sha
    torch.set_num_threads(2)
    model = CLIPModel.from_pretrained(checkpoint, revision=revision, attn_implementation="eager").eval()
    processor = CLIPProcessor.from_pretrained(checkpoint, revision=revision, use_fast=False)
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    tokenizer = processor.tokenizer
    tokenizer.save_pretrained(output)
    # Only these assets are needed by Android; processor config is recorded in the manifest.
    vision, text = Vision(model).eval(), Text(model).eval()
    ids = tokenizer("a surprised cat", padding="max_length", truncation=True, max_length=77, return_tensors="pt")
    image = Image.fromarray(np.random.default_rng(42).integers(0, 256, (313, 197, 3), dtype=np.uint8))
    pixels = processor(images=image, return_tensors="pt")["pixel_values"]
    with torch.no_grad():
        for name, encoder, inputs, input_names in [
            ("vision", vision, (pixels,), ["pixel_values"]),
            ("text", text, (ids["input_ids"], ids["attention_mask"]), ["input_ids", "attention_mask"]),
        ]:
            path = output / f"{name}.onnx"
            torch.onnx.export(encoder, inputs, str(path), input_names=input_names,
                              output_names=["embedding"], opset_version=17, dynamo=False)
            onnx.checker.check_model(str(path))
            quantize_dynamic(str(path), str(output / f"{name}.int8.onnx"),
                             op_types_to_quantize=["MatMul"], weight_type=QuantType.QInt8,
                             extra_options={"MatMulConstBOnly": True})
            session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
            actual = session.run(None, dict(zip(input_names, [v.numpy() for v in inputs])))[0]
            expected = encoder(*inputs).numpy()
            np.testing.assert_allclose(actual, expected, atol=2e-4, rtol=2e-4)
    fixtures = output / "reference"
    fixtures.mkdir(exist_ok=True)
    texts = ["a surprised cat", "a dog wearing sunglasses", "I can’t believe it!", "hello &amp; goodbye",
             "<tag> funny cat </tag>", "a " * 120, "123 cats", "", "café", "a\ncat"]
    cases = []
    with torch.no_grad():
        for query in texts:
            tokens = tokenizer(query, padding="max_length", truncation=True, max_length=77, return_tensors="pt")
            cases.append({"text": query, "ids": tokens["input_ids"][0].tolist(),
                          "mask": tokens["attention_mask"][0].tolist(),
                          "embedding": normalize(text(tokens["input_ids"], tokens["attention_mask"]).numpy())[0].tolist()})
        images = []
        for name, dimensions in [("portrait", (197, 313)), ("landscape", (313, 197)), ("square", (224, 224))]:
            w, h = dimensions
            y, x = np.mgrid[:h, :w]
            rgb = np.stack([(x * 3 + y) % 256, (y * 5 + x) % 256, (x + y * 7) % 256], axis=-1).astype(np.uint8)
            sample = Image.fromarray(rgb)
            sample.save(fixtures / f"{name}.png")
            values = processor(images=sample, return_tensors="pt")["pixel_values"]
            values.numpy().astype("<f4").tofile(fixtures / f"{name}.pixels.bin")
            images.append({"file": f"{name}.png", "embedding": normalize(vision(values).numpy())[0].tolist()})
        sample = Image.open(fixtures / "landscape.png")
        exif = sample.getexif()
        exif[274] = 6
        sample.save(fixtures / "rotated.jpg", quality=95, exif=exif)
        with Image.open(fixtures / "rotated.jpg") as rotated:
            values = processor(images=ImageOps.exif_transpose(rotated).convert("RGB"), return_tensors="pt")["pixel_values"]
        images.append({"file": "rotated.jpg", "embedding": normalize(vision(values).numpy())[0].tolist()})
    (fixtures / "tokens.json").write_text(json.dumps(cases))
    (fixtures / "images.json").write_text(json.dumps(images))
    versions = {name: importlib.metadata.version(name) for name in ["torch", "transformers", "onnx", "onnxruntime", "Pillow", "ftfy"]}
    manifest = {
        "checkpoint": checkpoint, "revision": revision, "precision": "fp32",
        "model_version": f"{args.candidate}-{revision[:12]}-fp32-v1",
        "dimension": model.config.projection_dim, "opset": 17, "tools": versions,
        "tokenizer": {"implementation": "CLIPTokenizerFast", "normalization": "NFC, Unicode whitespace collapse, lowercase; no HTML unescape"},
        "tokens": {"bos": tokenizer.bos_token_id, "eos": tokenizer.eos_token_id,
                   "pad": tokenizer.pad_token_id, "length": 77},
        "preprocessing": {"size": processor.image_processor.crop_size["height"],
                          "mean": processor.image_processor.image_mean, "std": processor.image_processor.image_std,
                          "resize": "shorter-side-bicubic-antialias", "crop": "center", "color": "RGB-sRGB",
                          "orientation": "EXIF-transpose", "scale": 1 / 255},
        "licenses": {"model": "MIT", "onnxruntime": "MIT",
                     "source": "https://github.com/microsoft/Cream/blob/main/LICENSE" if args.candidate == "tinyclip"
                     else "https://github.com/openai/CLIP/blob/main/LICENSE"},
        "selection_status": "provisional; labeled meme evaluation and flagship measurements pending",
        "files": {},
    }
    for name in ["vision.onnx", "text.onnx", "vocab.json", "merges.txt"]:
        manifest["files"][name] = hashlib.sha256((output / name).read_bytes()).hexdigest()
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"checkpoint": checkpoint, "revision": revision, "bytes": {
        name: (output / name).stat().st_size for name in ["vision.onnx", "text.onnx", "vision.int8.onnx", "text.int8.onnx"]
    }}), flush=True)


if __name__ == "__main__":
    main()
