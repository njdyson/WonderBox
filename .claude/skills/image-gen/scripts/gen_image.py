#!/usr/bin/env python3
"""Generate or edit images with OpenAI or Google (Gemini) image models.

Standard library only, so it runs anywhere Python 3.8+ does.

Keys:
  In a Claude Code cloud session the network proxy injects saved credentials,
  so no key is needed in the environment. Elsewhere, set OPENAI_API_KEY or
  GEMINI_API_KEY (GOOGLE_API_KEY also works).

Examples:
  gen_image.py "a jar of moonshine, pixel art" --out Art/Unused/jar.png
  gen_image.py "same scene at night" --ref Art/Speakeasy.png --out night.png
  gen_image.py "a 1920s ledger icon" --provider google --out ledger.png
  gen_image.py --list-models
"""

import argparse
import base64
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
import uuid

OPENAI_BASE = "https://api.openai.com/v1"
GOOGLE_BASE = "https://generativelanguage.googleapis.com/v1beta"
DEFAULT_MODELS = {"openai": "gpt-image-2", "google": "gemini-3.1-flash-image"}
PLACEHOLDER = "proxy-injected"  # the cloud proxy overwrites this header


def openai_key():
    return os.environ.get("OPENAI_API_KEY") or PLACEHOLDER


def google_key():
    return os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or PLACEHOLDER


def request(url, headers, body=None, timeout=300):
    req = urllib.request.Request(url, data=body, headers=headers, method="POST" if body else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(detail).get("error", detail)
        except ValueError:
            pass
        sys.exit(f"HTTP {e.code} from {url}: {detail}")


def multipart(fields, files):
    """Encode form fields and (name, path) files as multipart/form-data."""
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode()
    for name, path in files:
        mime = mimetypes.guess_type(path)[0] or "application/octet-stream"
        fname = os.path.basename(path)
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; "
                f"filename=\"{fname}\"\r\nContent-Type: {mime}\r\n\r\n").encode()
        with open(path, "rb") as f:
            out += f.read()
        out += b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def gen_openai(args):
    auth = {"Authorization": f"Bearer {openai_key()}"}
    opts = {"model": args.model, "prompt": args.prompt, "n": args.n}
    for k in ("size", "quality", "background", "output_format"):
        v = getattr(args, k)
        if v:
            opts[k] = v
    if args.ref:
        files = [("image[]", p) for p in args.ref]
        if args.mask:
            files.append(("mask", args.mask))
        body, ctype = multipart({k: str(v) for k, v in opts.items()}, files)
        data = request(f"{OPENAI_BASE}/images/edits", {**auth, "Content-Type": ctype}, body)
    else:
        body = json.dumps(opts).encode()
        data = request(f"{OPENAI_BASE}/images/generations",
                       {**auth, "Content-Type": "application/json"}, body)
    images = [base64.b64decode(d["b64_json"]) for d in data.get("data", []) if d.get("b64_json")]
    return images, data.get("usage")


def gen_google(args):
    parts = [{"text": args.prompt}]
    for p in args.ref or []:
        mime = mimetypes.guess_type(p)[0] or "image/png"
        with open(p, "rb") as f:
            parts.append({"inline_data": {"mime_type": mime, "data": base64.b64encode(f.read()).decode()}})
    payload = {"contents": [{"parts": parts}],
               "generationConfig": {"responseModalities": ["TEXT", "IMAGE"]}}
    if args.aspect:
        payload["generationConfig"]["imageConfig"] = {"aspectRatio": args.aspect}
    headers = {"x-goog-api-key": google_key(), "Content-Type": "application/json"}
    images = []
    for _ in range(args.n):
        data = request(f"{GOOGLE_BASE}/models/{args.model}:generateContent",
                       headers, json.dumps(payload).encode())
        for cand in data.get("candidates", []):
            for part in cand.get("content", {}).get("parts", []):
                inline = part.get("inlineData") or part.get("inline_data")
                if inline and inline.get("data"):
                    images.append(base64.b64decode(inline["data"]))
                elif part.get("text"):
                    print(f"[model text] {part['text']}", file=sys.stderr)
    return images, None


def list_models(provider):
    if provider == "openai":
        data = request(f"{OPENAI_BASE}/models", {"Authorization": f"Bearer {openai_key()}"})
        ids = sorted(m["id"] for m in data.get("data", []))
        print("\n".join(i for i in ids if "image" in i or "dall" in i))
    else:
        data = request(f"{GOOGLE_BASE}/models?pageSize=1000", {"x-goog-api-key": google_key()})
        for m in data.get("models", []):
            name = m["name"].split("/", 1)[-1]
            if "image" in name or "imagen" in name:
                print(name)


def sniff_ext(data):
    """The real format of image bytes, by their magic number."""
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return ".png"
    if data[:3] == b"\xff\xd8\xff":
        return ".jpg"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return ".webp"
    return None


def fix_ext(path, data):
    """Rename to match the bytes, so a JPEG never lands in a .png file."""
    real = sniff_ext(data)
    stem, ext = os.path.splitext(path)
    if real and ext.lower() not in ((real, ".jpeg") if real == ".jpg" else (real,)):
        print(f"[note] model returned {real[1:].upper()}, saving as {stem + real}", file=sys.stderr)
        return stem + real
    return path


def out_paths(out, count):
    if count == 1:
        return [out]
    stem, ext = os.path.splitext(out)
    return [f"{stem}-{i + 1}{ext}" for i in range(count)]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("prompt", nargs="?", help="what to draw")
    ap.add_argument("--out", help="output file (several images get -1, -2 ... suffixes)")
    ap.add_argument("--provider", choices=["openai", "google"], default="openai")
    ap.add_argument("--model", help="model id (default: gpt-image-2 / gemini-3.1-flash-image)")
    ap.add_argument("--n", type=int, default=1, help="number of images")
    ap.add_argument("--ref", action="append", help="reference image to edit or match (repeatable)")
    ap.add_argument("--mask", help="OpenAI only: PNG mask, transparent where to repaint")
    ap.add_argument("--size", help="OpenAI: 1024x1024, 1536x1024, 1024x1536 or auto")
    ap.add_argument("--quality", help="OpenAI: low, medium, high or auto")
    ap.add_argument("--background", help="OpenAI: transparent, opaque or auto")
    ap.add_argument("--output-format", dest="output_format", help="OpenAI: png, jpeg or webp")
    ap.add_argument("--aspect", help="Google: aspect ratio such as 1:1, 3:2, 16:9")
    ap.add_argument("--list-models", action="store_true", help="list image models the key can use")
    args = ap.parse_args()

    if args.list_models:
        list_models(args.provider)
        return
    if not args.prompt or not args.out:
        ap.error("a prompt and --out are required")
    args.model = args.model or DEFAULT_MODELS[args.provider]
    if args.output_format is None and args.provider == "openai":
        ext = os.path.splitext(args.out)[1].lower().lstrip(".")
        if ext in ("png", "jpeg", "webp"):
            args.output_format = ext
        elif ext == "jpg":
            args.output_format = "jpeg"

    images, usage = (gen_openai if args.provider == "openai" else gen_google)(args)
    if not images:
        sys.exit("No image came back. Try rewording the prompt.")
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    for path, img in zip(out_paths(args.out, len(images)), images):
        path = fix_ext(path, img)
        with open(path, "wb") as f:
            f.write(img)
        print(path)
    if usage:
        print(f"[usage] {json.dumps(usage)}", file=sys.stderr)


if __name__ == "__main__":
    main()
