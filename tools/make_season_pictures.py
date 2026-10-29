#!/usr/bin/env python3
"""Bundles the seasonal decorations' pictures (ui/Seasons.kt) in app/src/main/assets/season.

    python3 tools/make_season_pictures.py

Each is a Fluent Emoji 3D picture (MIT, like the avatars), fetched from the CDN at
server/pb_public/clipart/fluent.json's pinned commit and saved as a 256px WebP named by its
code points ("🎃" is 1f383.webp). Fluent's fireworks (🎆 🎇) are dark squares, so
Bonfire Night's fireworks are drawn instead. Add a season's emoji to PICTURES and run it again. Needs Pillow.
"""
import io
import json
import pathlib
import urllib.parse
import urllib.request

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "server/pb_public/clipart/fluent.json"
OUT = ROOT / "app/src/main/assets/season"

PICTURES = {
    "halloween": "🎃 👻 🦇 🕷 🕸 🍬 🍭 🌝",
    "bonfire": "✨ 🔥",
    "christmas": "🎄 ⛄ ❄ 🎁 🔔 ⭐ 🦌",
    "valentine": "💖 💕 💗 🌹",
    "easter": "🐣 🥚 🐰 🌷 🐥 🌼",
}


def strip(glyph):
    return glyph.replace("️", "")


def main():
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    by_glyph = {strip(row[1]): row for row in catalogue["emoji"] if row[1]}
    OUT.mkdir(parents=True, exist_ok=True)
    for season, glyphs in PICTURES.items():
        for glyph in glyphs.split():
            row = by_glyph[strip(glyph)]
            target = OUT / ("-".join(f"{ord(c):x}" for c in strip(glyph)) + ".webp")
            if target.exists():
                continue
            url = catalogue["base"] + urllib.parse.quote(row[2])
            with urllib.request.urlopen(url) as res:
                image = Image.open(io.BytesIO(res.read())).convert("RGBA")
            image.thumbnail((256, 256), Image.LANCZOS)
            image.save(target, "WEBP", quality=82, method=6)
            print(season, glyph, target.name)


main()
