#!/usr/bin/env python3
"""Builds server/pb_public/clipart/fluent.json, the phone app's clipart catalogue for quizzes.

    git clone --filter=blob:none --no-checkout https://github.com/microsoft/fluentui-emoji.git /tmp/fluent
    git -C /tmp/fluent sparse-checkout set --no-cone '/assets/*/metadata.json' && git -C /tmp/fluent checkout
    python3 tools/make_clipart_index.py /tmp/fluent server/pb_public/clipart/fluent.json

Each entry is [name, emoji, path of its 3D picture, extra keywords, group]. The phone app
loads pictures from jsDelivr at the commit below and copies the chosen ones to the server,
so the TV never needs the CDN. Fluent Emoji is MIT licensed (clipart/LICENSE.txt).
"""
import json
import os
import subprocess
import sys

repo, out = sys.argv[1], sys.argv[2]
commit = subprocess.check_output(["git", "-C", repo, "rev-parse", "HEAD"], text=True).strip()
files = subprocess.check_output(["git", "-C", repo, "ls-tree", "-r", "--name-only", "HEAD", "assets"], text=True).splitlines()

# One 3D picture per emoji; for skin-tone emoji, the "Default" (yellow) one.
pngs = {}
for f in files:
    if "/3D/" not in f or not f.endswith(".png"):
        continue
    folder = f.split("/")[1]
    if "/Default/" in f or folder not in pngs:
        pngs[folder] = f

GROUPS = ["Animals & Nature", "Food & Drink", "Travel & Places", "Activities", "Objects",
          "Smileys & Emotion", "People & Body", "Symbols", "Flags"]
rows = []
for folder, path in pngs.items():
    with open(os.path.join(repo, "assets", folder, "metadata.json"), encoding="utf-8") as f:
        meta = json.load(f)
    group = GROUPS.index(meta["group"]) if meta.get("group") in GROUPS else len(GROUPS)
    keywords = [k for k in meta.get("keywords", []) if k.lower() not in folder.lower()]
    rows.append([folder, meta.get("glyph", ""), path[len("assets/"):], " ".join(keywords), group])
rows.sort(key=lambda r: (r[4], r[0].lower()))

with open(out, "w", encoding="utf-8") as f:
    json.dump({
        "groups": GROUPS,
        "base": f"https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@{commit}/assets/",
        "emoji": rows,
    }, f, ensure_ascii=False, separators=(",", ":"))
print(f"{len(rows)} pictures, {os.path.getsize(out)} bytes")
