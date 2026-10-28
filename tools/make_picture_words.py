#!/usr/bin/env python3
"""Builds the spelling games' built-in picture words (games/PictureWords.kt) and their pictures.

    python3 tools/make_picture_words.py

Each word is a Fluent Emoji picture with a simple one-word name (server/pb_public/clipart/
fluent.json), plus the hand-picked words the games started with. Lists: SHORT is three letters
(cat), LONG four and five (frog), BIG six to eight (rabbit). Every word's picture is bundled in
app/src/main/assets/avatars as a 192px WebP (older Fire TVs draw newer emoji as blanks), fetched
from the Fluent Emoji CDN at fluent.json's pinned commit. Needs Pillow.
"""
import io
import json
import pathlib
import re
import urllib.parse
import urllib.request

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "server/pb_public/clipart/fluent.json"
AVATARS = ROOT / "app/src/main/assets/avatars"
OUT = ROOT / "app/src/main/java/com/njdyson/wonderbox/games/PictureWords.kt"

# The words the games started with, kept first and with their own pictures.
FIRST = [
    ("cat", "🐱"), ("dog", "🐶"), ("pig", "🐷"), ("hen", "🐔"), ("bus", "🚌"), ("box", "📦"),
    ("bat", "🦇"), ("bed", "🛏"), ("hat", "🎩"), ("sun", "🌞"), ("van", "🚐"), ("web", "🕸"),
    ("pen", "🖊"), ("map", "🗺"), ("fox", "🦊"), ("bag", "👜"), ("jam", "🍯"), ("mug", "☕"),
    ("fish", "🐟"), ("frog", "🐸"), ("duck", "🦆"), ("star", "⭐"), ("moon", "🌙"), ("cake", "🎂"),
    ("tree", "🌳"), ("ship", "🚢"), ("bell", "🔔"), ("ball", "⚽"), ("bird", "🐦"), ("boat", "⛵"),
    ("book", "📖"), ("lion", "🦁"), ("bear", "🐻"), ("crab", "🦀"), ("snail", "🐌"), ("snake", "🐍"),
    ("horse", "🐴"), ("sheep", "🐑"), ("mouse", "🐭"), ("apple", "🍎"), ("house", "🏠"), ("train", "🚂"),
    ("clock", "🕐"), ("bread", "🍞"), ("shell", "🐚"), ("whale", "🐳"), ("tiger", "🐯"), ("pizza", "🍕"),
]

# Fluent names that are odd, grown-up, American or not a clear picture for a 4 to 6 year old.
SKIP = set("""
dna dvd leo ewe oden mate sake memo moai link pick pill sari plus herb gear knot bank seat
kaaba hamsa joker pager razor aries libra minus virgo label clamp foggy dango candy metro
houses briefs bikini coffin chains dagger crutch ledger locked khanda cancer gemini pisces
taurus divide tamale fondue wrench amphora alembic customs menorah scorpio sparkle warning
placard plunger receipt syringe microbe rosette cooking camping droplet minibus pushpin
sauropod hibiscus hyacinth shamrock multiply aquarius infinity restroom wireless unlocked
sparkles sparkler lacrosse bullseye softball monorail eggplant airplane elevator seedling
blowfish scorpion motorway books shrimp
""".split())

# Groups worth spelling: animals, food, places, activities, things.
GROUPS = {0, 1, 2, 3, 4}


def strip(glyph):
    return glyph.replace("️", "")


def file_name(glyph):
    return "-".join(f"{ord(c):x}" for c in strip(glyph)) + ".webp"


def main():
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    by_glyph = {strip(row[1]): row for row in catalogue["emoji"] if row[1]}
    words = dict(FIRST)
    for name, glyph, _, _, group in catalogue["emoji"]:
        word = name.lower()
        if group in GROUPS and glyph and re.fullmatch(r"[a-z]{3,8}", word) and word not in SKIP:
            words.setdefault(word, glyph)

    # Bundle each picture; a word with no Fluent picture for its emoji is left out.
    kept = []
    for word, glyph in words.items():
        row = by_glyph.get(strip(glyph))
        if not row:
            print(f"no picture for {word} {glyph}, left out")
            continue
        target = AVATARS / file_name(glyph)
        if not target.exists():
            url = catalogue["base"] + urllib.parse.quote(row[2])
            with urllib.request.urlopen(url) as res:
                image = Image.open(io.BytesIO(res.read())).convert("RGBA")
            image.thumbnail((192, 192), Image.LANCZOS)
            image.save(target, "WEBP", quality=82, method=6)
        kept.append((word, glyph))

    lists = {
        "SHORT": ("Three-letter words, like cat.", [w for w in kept if len(w[0]) == 3]),
        "LONG": ("Four- and five-letter words, like frog.", [w for w in kept if len(w[0]) in (4, 5)]),
        "BIG": ("Six- to eight-letter words, like rabbit.", [w for w in kept if 6 <= len(w[0]) <= 8]),
    }
    lines = [
        "package com.njdyson.wonderbox.games",
        "",
        "// Made by tools/make_picture_words.py: change that, not this.",
        "",
        "/** The spelling games' built-in words, each with a picture bundled in assets/avatars. */",
        "internal object PictureWords {",
    ]
    for name, (doc, items) in lists.items():
        lines += [f"    /** {doc} */", f"    val {name} = listOf("]
        for i in range(0, len(items), 6):
            lines.append("        " + " ".join(f'"{w}" to "{g}",' for w, g in items[i:i + 6]))
        lines += ["    )", ""]
    lines[-1] = "}"
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(", ".join(f"{name} {len(items)}" for name, (_, items) in lists.items()))


main()
