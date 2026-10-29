/// <reference path="../pb_data/types.d.ts" />

// The server's own voice: Piper (https://github.com/rhasspy/piper) reading text in a British
// voice, for TVs whose own text-to-speech is missing or poor (some Fire TV sticks). install.sh
// puts Piper and a voice in /opt/wonderbox/piper and sets PIPER_BIN and PIPER_VOICE. Each
// clip is made once and kept in <data>/speech, named by a hash of the voice and the text.
// Stories (slow) are read by Gemini's storyteller voice instead when the server has a Gemini
// key (gemini_lib.js), with Piper as the fallback.

const MAX_TEXT = 700

// Piper's pace: length_scale (above 1 is slower) and seconds of silence between sentences.
// Everyday reading is a little slower than Piper's own pace, for young children.
const PACE = { normal: ["1.2", "0.35"], slow: ["1.4", "0.7"] }

// How the storyteller reads, and how many new story clips a family can have made per day. Kept
// short: Google says long style prompts make the voice drift, and asking for playful character
// voices brought odd noises (Nick heard fart-like sounds and hiss).
const STORY_STYLE = "warm, gentle and calm"
const STORY_CLIPS_PER_DAY = 300

function config() {
  const bin = $os.getenv("PIPER_BIN")
  const voice = $os.getenv("PIPER_VOICE")
  if (!bin || !voice) return null
  try {
    $os.stat(bin)
    $os.stat(voice)
  } catch (_) {
    return null
  }
  return { bin, voice }
}

/** Why this server can't speak, or "" when it can: for GET /api/wonder/voice. */
function problem() {
  const bin = $os.getenv("PIPER_BIN")
  const voice = $os.getenv("PIPER_VOICE")
  if (!bin || !voice) return "Piper isn't set up: run the install task again (server/README.md, step 4) and read what it says about the voice."
  try { $os.stat(bin) } catch (_) { return `Piper isn't at ${bin}: run the install task again.` }
  try { $os.stat(voice) } catch (_) { return `The voice isn't at ${voice}: run the install task again.` }
  return ""
}

/** True when this server can speak (Piper; the storyteller alone isn't enough for everything else). */
function available() {
  return config() !== null
}

/** The text to say: one line, at most MAX_TEXT characters. */
function clean(text) {
  return String(text || "").replace(/\s+/g, " ").trim().slice(0, MAX_TEXT)
}

/**
 * The path of a WAV file of [text] being read, made now if it isn't already. [slow] is for
 * stories: Gemini's storyteller when it can (counted against [userId]'s day), otherwise Piper
 * reading more slowly, with longer pauses between sentences.
 */
function clip(text, slow, userId) {
  if (slow) {
    const told = story(text, userId)
    if (told) return told
  }
  const c = config()
  if (!c) throw new NotFoundError("This server has no voice.")
  const dir = `${$app.dataDir()}/speech`
  $os.mkdirAll(dir, 0o755)
  const pace = slow ? PACE.slow : PACE.normal
  const name = $security.sha256(`${c.voice}\n${pace.join(" ")}\n${text}`)
  const wav = `${dir}/${name}.wav`
  try {
    $os.stat(wav)
    return wav
  } catch (_) {}
  const input = `${dir}/${name}.txt`
  const part = `${dir}/${name}.part.wav`
  $os.writeFile(input, text, 0o644)
  try {
    // The paths go in as arguments, never into the shell command, so no text can run as code.
    $os.cmd("sh", "-c", '"$0" --model "$1" --output_file "$2" --length_scale "$4" --sentence_silence "$5" < "$3"', c.bin, c.voice, part, input, pace[0], pace[1]).combinedOutput()
    $os.stat(part)
    $os.rename(part, wav)
  } catch (err) {
    try { $os.remove(part) } catch (_) {}
    throw new BadRequestError("The voice couldn't read that.")
  } finally {
    try { $os.remove(input) } catch (_) {}
  }
  return wav
}

/** The path of Gemini's storyteller reading [text], made now if need be; null if it can't. */
function story(text, userId) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  if (!gemini.available()) return null
  const dir = `${$app.dataDir()}/speech`
  $os.mkdirAll(dir, 0o755)
  const name = $security.sha256(`gemini\n${gemini.ttsModel()}\n${gemini.voice()}\n${STORY_STYLE}\n${text}`)
  const wav = `${dir}/${name}.wav`
  try {
    $os.stat(wav)
    return wav
  } catch (_) {}
  // The TV asks for each part ahead of time and again when it's read, often while the first ask
  // is still being recorded: the second waits for the first rather than paying for it twice.
  const making = `wonderbox_speech_making:${name}`
  const since = $app.store().get(making)
  if (since && Date.now() - since < 100 * 1000) {
    while (Date.now() - since < 100 * 1000) {
      sleep(500)
      try {
        $os.stat(wav)
        return wav
      } catch (_) {}
      if (!$app.store().get(making)) break
    }
    try {
      $os.stat(wav)
      return wav
    } catch (_) {
      return null
    }
  }
  $app.store().set(making, Date.now())
  try {
    gemini.allow(userId || "", "voice", STORY_CLIPS_PER_DAY)
    const part = `${dir}/${name}.part.wav`
    if (!gemini.speak(text, STORY_STYLE, part)) return null
    $os.rename(part, wav)
    return wav
  } catch (err) {
    console.log(`storyteller voice: ${err}`)
    return null
  } finally {
    $app.store().remove(making)
  }
}

/** Whether stories are read by Gemini's storyteller (the server has a Gemini key). */
function storyteller() {
  return require(`${__hooks}/gemini_lib.js`).available()
}

module.exports = { available, problem, clean, clip, storyteller, MAX_TEXT }
