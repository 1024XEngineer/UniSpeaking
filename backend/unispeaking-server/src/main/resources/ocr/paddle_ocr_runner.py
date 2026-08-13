#!/usr/bin/env python3

import argparse
import json
import os
import sys
import tempfile
import traceback
from pathlib import Path

from PIL import Image
from paddleocr import PaddleOCR


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(add_help=True)
    parser.add_argument("--download-models", action="store_true")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--text-detection-model-name", default="PP-OCRv5_mobile_det")
    parser.add_argument("--text-recognition-model-name", default="PP-OCRv5_mobile_rec")
    parser.add_argument("--disable-doc-orientation", action="store_true")
    parser.add_argument("--disable-doc-unwarping", action="store_true")
    parser.add_argument("--disable-textline-orientation", action="store_true")
    parser.add_argument("--worker", action="store_true")
    parser.add_argument("--images", nargs="*")
    return parser


def create_pipeline(
    device: str,
    detection_model_name: str,
    recognition_model_name: str,
    model_directory: Path | None = None,
) -> PaddleOCR:
    model_parameters = {
        "text_detection_model_name": detection_model_name,
        "text_recognition_model_name": recognition_model_name,
        "use_doc_orientation_classify": False,
        "use_doc_unwarping": False,
        "use_textline_orientation": False,
        "device": device,
    }
    if model_directory is not None:
        model_parameters["text_detection_model_dir"] = str(
            local_model_path(model_directory, detection_model_name)
        )
        model_parameters["text_recognition_model_dir"] = str(
            local_model_path(model_directory, recognition_model_name)
        )
    return PaddleOCR(**model_parameters)


def local_model_path(model_directory: Path, model_name: str) -> Path:
    model_path = model_directory / "official_models" / model_name
    if not model_path.is_dir():
        raise RuntimeError("ocr-model-not-found")
    return model_path


def model_cache_directory() -> Path:
    return Path(os.environ.get("PADDLE_PDX_CACHE_HOME") or (Path.home() / ".paddlex"))


def extract_text(result) -> str:
    payload = getattr(result, "json", None)
    if callable(payload):
        payload = payload()
    if isinstance(payload, str):
        payload = json.loads(payload)
    if not isinstance(payload, dict):
        raise ValueError("invalid result")
    root = payload.get("res")
    if not isinstance(root, dict):
        raise ValueError("invalid result")
    texts = root.get("rec_texts")
    if isinstance(texts, list):
        cleaned = [str(text).strip() for text in texts if str(text).strip()]
        return "\n".join(cleaned)
    text = root.get("rec_text")
    if isinstance(text, str):
        return text.strip()
    raise ValueError("invalid result")


def ensure_models_loaded(ocr: PaddleOCR) -> None:
    with tempfile.TemporaryDirectory(prefix="paddle-ocr-smoke-") as temp_dir:
        image_path = Path(temp_dir) / "smoke.png"
        Image.new("RGB", (16, 16), "white").save(image_path)
        list(ocr.predict([str(image_path)]))


def recognize_batch(ocr: PaddleOCR, image_paths: list[str]) -> dict:
    output = []
    for result in ocr.predict(image_paths):
        output.append({"text": extract_text(result)})
    return {"results": output}


def run_worker(ocr: PaddleOCR) -> int:
    # stdout is a JSON-lines protocol. Paddle/PaddleX diagnostics belong on stderr.
    print(json.dumps({"ready": True}), flush=True)
    for line in sys.stdin:
        request_id = None
        try:
            request = json.loads(line)
            request_id = request.get("id")
            image_paths = request.get("images")
            if not isinstance(request_id, str) or not request_id:
                raise ValueError("invalid request id")
            if not isinstance(image_paths, list) or not image_paths:
                raise ValueError("missing images")
            payload = recognize_batch(ocr, [str(path) for path in image_paths])
            payload["id"] = request_id
            print(json.dumps(payload, ensure_ascii=False), flush=True)
        except Exception:
            # Do not terminate the resident process for one bad image/request. Java
            # treats the error response as a failed OCR operation and can restart
            # the worker if the process itself has become unhealthy.
            print(json.dumps({
                "id": request_id,
                "error": "ocr-request-failed",
            }), flush=True)
    return 0


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    model_directory = None if args.download_models else model_cache_directory()
    ocr = create_pipeline(
        args.device,
        args.text_detection_model_name,
        args.text_recognition_model_name,
        model_directory,
    )
    if args.worker:
        return run_worker(ocr)
    if args.download_models:
        ensure_models_loaded(ocr)
        return 0
    if not args.images:
        print(json.dumps({"error": "missing images"}), file=sys.stderr)
        return 2
    payload = recognize_batch(ocr, args.images)
    print(json.dumps(payload, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        if os.environ.get("OCR_RUNNER_DEBUG") == "1":
            traceback.print_exc()
        print(json.dumps({"error": "ocr-runner-failed"}), file=sys.stderr)
        raise SystemExit(1)
