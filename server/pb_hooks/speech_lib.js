/// <reference path="../pb_data/types.d.ts" />

// The server's own voice: Piper (https://github.com/rhasspy/piper) reading text in a British
// voice, for TVs whose own text-to-speech is missing or poor (some Fire TV sticks). install.sh
// puts Piper and a voice in /opt/wonderbox/piper and sets PIPER_BIN and PIPER_VOICE. Each
// clip is made once and kept in <data>/speech, named by a hash of the voice and the text.

const MAX_TEXT = 300

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

/** True when this server can speak. */
function available() {
  return config() !== null
}

/** The text to say: one line, at most MAX_TEXT characters. */
function clean(text) {
  return String(text || "").replace(/\s+/g, " ").trim().slice(0, MAX_TEXT)
}

/** The path of a WAV file of [text] being read, made now if it isn't already. */
function clip(text) {
  const c = config()
  if (!c) throw new NotFoundError("This server has no voice.")
  const dir = `${$app.dataDir()}/speech`
  $os.mkdirAll(dir, 0o755)
  const name = $security.sha256(`${c.voice}\n${text}`)
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
    $os.cmd("sh", "-c", '"$0" --model "$1" --output_file "$2" < "$3"', c.bin, c.voice, part, input).combinedOutput()
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

module.exports = { available, problem, clean, clip, MAX_TEXT }
