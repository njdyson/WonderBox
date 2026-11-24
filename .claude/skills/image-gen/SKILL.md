---
name: image-gen
description: Generate or edit images (art, icons, card plates, concept art, mockup assets) with OpenAI's or Google's image models and save them into the project. Use when the user asks to make, draw, generate, paint or redo an image, or to restyle or edit an existing one.
---

# Image generation

One script does the work: `scripts/gen_image.py` (Python 3, standard library only). Run it from the
project root:

```bash
python3 .claude/skills/image-gen/scripts/gen_image.py "PROMPT" --out path/to/file.png [options]
```

It prints the saved path(s) on stdout and token usage on stderr. If a model returns a different format
than the extension asked for, the script fixes the extension and says so: use the printed path. Open
the result with the Read tool and look at it before you report back: check that it matches the prompt,
that any lettering is spelled correctly, and that period details are right.

## Keys

- **Cloud sessions:** the network proxy injects saved credentials. Nothing to set. If a call fails
  with 401 or 403, the key for that host isn't saved on this environment: say so and ask the user to
  add it, never ask for the key in chat.
- **Elsewhere:** the script reads `OPENAI_API_KEY`, or `GEMINI_API_KEY` / `GOOGLE_API_KEY`.

Every call costs money on the user's API account. Generate one image at a time unless asked for
variations, and use `--quality low` for drafts.

## Providers and models

| Provider | Default model | Notes |
| --- | --- | --- |
| `openai` (default) | `gpt-image-2` | Best general choice. No transparent backgrounds. |
| `openai` | `gpt-image-1.5` | Use for `--background transparent` (icons, tokens, cutouts). |
| `google` | `gemini-3.1-flash-image` | Strong on prompt-following and period detail. Use `--aspect` instead of `--size`. |
| `google` | `gemini-2.5-flash-image` | Older and flatter; a fallback. |

Model ids change. Run `--list-models` (with `--provider google` for Google) to see what the key can
use today, and pass `--model` to pick one.

## Options

| Option | Meaning |
| --- | --- |
| `--out FILE` | Required. Extension sets the format (`.png`, `.jpg`, `.webp`). `--n 3` writes `FILE-1`, `FILE-2`, ... |
| `--ref IMG` | Edit or restyle this image; repeat for several references. Switches OpenAI to its edits endpoint. |
| `--mask PNG` | OpenAI only: repaint where the mask is transparent. Same size as the first `--ref`. |
| `--size` | OpenAI: `1024x1024`, `1536x1024` (landscape), `1024x1536` (portrait), `auto`. |
| `--quality` | OpenAI: `low` (fast, cheap drafts), `medium`, `high`, `auto`. |
| `--background` | OpenAI: `transparent` (needs `gpt-image-1.5`), `opaque`, `auto`. |
| `--aspect` | Google: `1:1`, `3:2`, `2:3`, `16:9`, ... |
| `--n` | How many images. |

## Prompting tips

- **Name the period and setting.** Models drift to the nearest familiar era: "a police car" came back
  as a 1940s cruiser, and a 1929 street came back with a window air conditioner. Say "1929 New
  York", "a Model A Ford police car", and so on, and check the result for anachronisms.
- **Match existing art with `--ref`.** Passing an existing image and asking for "the same painted
  style" keeps a set consistent far better than describing the style in words. For a faithful edit
  of one image (same figures and layout, one change), Google held the composition closely in testing;
  keep the reference's orientation (`--aspect` or `--size`) unless the user wants a new framing.
- **Lettering:** low quality misspells words. For an image with text, use `--quality high`, keep the
  text short, and check every letter. Where the project can, put text in HTML or SVG over the image
  instead of baking it in.
- **Composition:** say what goes where and what to leave empty ("plain background, subject centred,
  room at the top for a title").

## Where to save

Save into the project's existing art folders, following their naming. Don't overwrite an existing
image unless the user asks; write a new file next to it and let them compare. Large PNGs bloat a git
repo, so prefer `.jpg` or `.webp` for paintings and keep `.png` for transparency.

If a project has its own rules for art (a CLAUDE.md section, an optimise script, a style guide),
those win over this file.
