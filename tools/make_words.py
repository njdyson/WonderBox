#!/usr/bin/env python3
"""Builds the word game's word lists (app/src/main/assets/words/).

    curl -L -o /tmp/scowl.tgz https://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz
    tar -xzf /tmp/scowl.tgz -C /tmp
    python3 tools/make_words.py /tmp/scowl-2020.12.07

known.txt is tools/words/kid_words.txt: words a young child knows. The game makes its puzzles
from these and shows the ones a child missed. more.txt is the rest of SCOWL's commonest
English and British words (sizes 10 and 20, no American spellings), so a child who finds a
real word that isn't on the kid list still gets it. Both leave out tools/words/blocked.txt.

SCOWL (Spell Checker Oriented Word Lists) is copyright Kevin Atkinson and others, free to use
and share with its notice: https://wordlist.aspell.net/
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT.parent / "app/src/main/assets/words"
WORD = re.compile(r"^[a-z]{3,7}$")


def words_in(path):
    text = path.read_text(encoding="utf-8")
    return {w for line in text.splitlines() if not line.startswith("#") for w in line.split()}


def main(scowl):
    blocked = words_in(ROOT / "words/blocked.txt")
    known = {w for w in words_in(ROOT / "words/kid_words.txt") if WORD.match(w)} - blocked
    more = set()
    for name in ["english-words.10", "british-words.10", "english-words.20", "british-words.20"]:
        for line in (pathlib.Path(scowl) / "final" / name).read_text(encoding="latin-1").splitlines():
            if WORD.match(line):
                more.add(line)
    more = more - blocked - known
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "known.txt").write_text("\n".join(sorted(known)) + "\n")
    (OUT / "more.txt").write_text("\n".join(sorted(more)) + "\n")
    print(f"{len(known)} known words, {len(more)} more")


if __name__ == "__main__":
    main(sys.argv[1])
