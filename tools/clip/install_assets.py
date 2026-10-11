#!/usr/bin/env python3
"""Package exactly one exported variant. Use --precision int8 only after labeled evaluation."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path)
    parser.add_argument("--destination", type=Path, default=Path("app/src/main/assets/clip"))
    parser.add_argument("--precision", choices=["fp32", "int8"], default="fp32")
    args = parser.parse_args()
    manifest = json.loads((args.model / "manifest.json").read_text())
    args.destination.mkdir(parents=True, exist_ok=True)
    suffix = ".onnx" if args.precision == "fp32" else ".int8.onnx"
    for name in ["vision", "text"]:
        shutil.copyfile(args.model / (name + suffix), args.destination / (name + ".onnx"))
    for name in ["vocab.json", "merges.txt"]:
        shutil.copyfile(args.model / name, args.destination / name)
    manifest["precision"] = args.precision
    manifest["model_version"] = manifest["model_version"].replace("-fp32-", f"-{args.precision}-")
    manifest["files"] = {name: hashlib.sha256((args.destination / name).read_bytes()).hexdigest()
                         for name in ["vision.onnx", "text.onnx", "vocab.json", "merges.txt"]}
    (args.destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
