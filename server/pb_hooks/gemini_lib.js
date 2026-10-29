/// <reference path="../pb_data/types.d.ts" />

// Google Gemini, for anything on the server that writes, draws or reads aloud (the story maker
// in story_lib.js, menu pictures, the photo quiz's questions, the storyteller voice in
// speech_lib.js), loaded with require(). One key for everything: GEMINI_API_KEY in
// <data>/wonderbox.env (deploy/install.sh). GEMINI_MODEL, GEMINI_IMAGE_MODEL and
// GEMINI_TTS_MODEL change the default models, GEMINI_VOICE the storyteller's voice;
// GEMINI_URL points somewhere else, for tests.
// Every call has Google's safety filters at their strictest, since it's all for children.

const TEXT_MODEL = "gemini-3.6-flash"
const IMAGE_MODEL = "gemini-2.5-flash-image"
const TTS_MODEL = "gemini-3.8-flash-tts"
// The storyteller: Nick's pick from Google AI Studio's voice library (2026-10-29).
const VOICE = "Riko"

const SAFETY = ["HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"]
  .map((category) => ({ category, threshold: "BLOCK_LOW_AND_ABOVE" }))

function available() {
  return !!$os.getenv("GEMINI_API_KEY")
}

function textModel() {
  return ($os.getenv("GEMINI_MODEL") || TEXT_MODEL).replace(/[^a-zA-Z0-9.\-_]/g, "")
}

function imageModel() {
  return ($os.getenv("GEMINI_IMAGE_MODEL") || IMAGE_MODEL).replace(/[^a-zA-Z0-9.\-_]/g, "")
}

function ttsModel() {
  return ($os.getenv("GEMINI_TTS_MODEL") || TTS_MODEL).replace(/[^a-zA-Z0-9.\-_]/g, "")
}

/**
 * The storyteller's voice for the TTS models: a voice_… ID as it is, otherwise the name as AI
 * Studio shows it (like "Riko"), looked up once in the voice library for its ID. A prebuilt
 * name, or one the library doesn't know, is used as it is.
 */
function voice() {
  const name = ($os.getenv("GEMINI_VOICE") || VOICE).replace(/[^a-zA-Z0-9.\-_]/g, "")
  if (/^voice(key)?_/.test(name)) return name
  const cacheKey = `wonderbox_gemini:voice:${name}`
  const cached = $app.store().get(cacheKey)
  if (cached) return cached
  let id = name
  try {
    const res = $http.send({
      url: apiUrl(`voices?search=${encodeURIComponent(name)}&page_size=50`),
      method: "GET",
      headers: { "x-goog-api-key": $os.getenv("GEMINI_API_KEY") || "" },
      timeout: 15,
    })
    const found = ((res.statusCode === 200 && res.json && res.json.voices) || [])
      .find((v) => String(v.displayName || v.display_name || "").toLowerCase() === name.toLowerCase())
    if (found) id = String(found.id || String(found.name || "").replace(/^voices\//, "") || name)
    else console.log(`gemini voices: no voice called ${name} (answered ${res.statusCode}), trying the name as it is`)
  } catch (err) {
    console.log(`gemini voices: ${err}`)
  }
  $app.store().set(cacheKey, id)
  return id
}

function apiUrl(path) {
  return `${($os.getenv("GEMINI_URL") || "https://generativelanguage.googleapis.com").replace(/\/+$/, "")}/v1beta/${path}`
}

/**
 * Counts one more use of [what] by [userId] today, so a child can't run up a bill. Throws 429
 * once there have been [limit] today.
 */
function allow(userId, what, limit) {
  const key = `wonderbox_gemini:${what}:${userId}:${new Date().toISOString().slice(0, 10)}`
  const count = $app.store().get(key) || 0
  if (count >= limit) throw new ApiError(429, "That's lots for today. Try again tomorrow.")
  $app.store().set(key, count + 1)
}

/**
 * POSTs [body] (a generateContent request, safety settings added) to [model]. If the model
 * says the request is bad, tries once more with [fallback] applied to the body (to drop a
 * setting an older model doesn't know). Returns the response JSON, or null when it failed.
 */
function generate(model, body, timeout, fallback) {
  const key = $os.getenv("GEMINI_API_KEY")
  if (!key) throw new ApiError(503, "This needs a Gemini key on the server.")
  body.safetySettings = SAFETY
  const send = () => $http.send({
    url: apiUrl(`models/${model}:generateContent`),
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify(body),
    timeout,
  })
  let res = send()
  if (res.statusCode === 400 && fallback) {
    fallback(body)
    res = send()
  }
  if (res.statusCode !== 200 || !res.json) {
    console.log(`gemini ${model}: answered ${res.statusCode}: ${String(toString(res.body)).slice(0, 300)}`)
    return null
  }
  return res.json
}

/** The parts of the first answer (thoughts left out). */
function answerParts(json) {
  const candidate = ((json && json.candidates) || [])[0]
  return ((candidate && candidate.content && candidate.content.parts) || []).filter((p) => !p.thought)
}

/**
 * JSON written to [schema] (Gemini's OpenAPI-style schema), from [system] instructions and a
 * [prompt]. Quick: little thinking. options.images is pictures for it to look at:
 * [{type: "image/jpeg", bytes: [...]}]. Returns the parsed object, or null.
 */
function writeJson(system, prompt, schema, options) {
  const o = options || {}
  const images = (o.images || []).map((i) => ({ inlineData: { mimeType: i.type, data: base64Text(i.bytes) } }))
  const body = {
    systemInstruction: { parts: [{ text: system }] },
    contents: [{ role: "user", parts: [...images, { text: prompt }] }],
    generationConfig: {
      temperature: o.temperature === undefined ? 0.9 : o.temperature,
      maxOutputTokens: o.maxTokens || 2048,
      responseMimeType: "application/json",
      responseSchema: schema,
      thinkingConfig: { thinkingLevel: "low" },
    },
  }
  const json = generate(textModel(), body, o.timeout || 25, (b) => delete b.generationConfig.thinkingConfig)
  if (!json) return null
  try {
    return JSON.parse(answerParts(json).map((p) => p.text || "").join(""))
  } catch (_) {
    return null
  }
}

/**
 * A picture of [prompt], wide ([aspect] like "16:9") where the model can, as
 * {type: "image/png", bytes: [...]}; null if it couldn't.
 */
function drawImage(prompt, aspect) {
  const body = {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { responseModalities: ["IMAGE"], imageConfig: { aspectRatio: aspect || "16:9" } },
  }
  const json = generate(imageModel(), body, 90, (b) => delete b.generationConfig.imageConfig)
  const image = answerParts(json).map((p) => p.inlineData || p.inline_data).find((d) => d && d.data)
  if (!image) return null
  return { type: String(image.mimeType || image.mime_type || "image/png"), bytes: base64Bytes(image.data) }
}

/**
 * [text] read aloud by [voice()], delivered as [style] says (like "gentle and slow"), as the
 * bytes of a WAV file; null if it couldn't. Uses the Interactions API, which the TTS models
 * answer on.
 */
function speak(text, style) {
  const key = $os.getenv("GEMINI_API_KEY")
  if (!key) return null
  const body = {
    model: ttsModel(),
    input: [{ type: "user_input", content: [{ type: "text", text, annotations: style ? [{ type: "speech_metadata", style }] : [] }] }],
    response_format: { type: "audio" },
    generation_config: { speech_config: [{ voice: voice() }] },
  }
  const res = $http.send({
    url: apiUrl("interactions"),
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify(body),
    timeout: 90,
  })
  if (res.statusCode !== 200 || !res.json) {
    console.log(`gemini ${body.model}: answered ${res.statusCode}: ${String(toString(res.body)).slice(0, 300)}`)
    return null
  }
  // The last audio block of the model's output.
  let data = ""
  for (const step of res.json.steps || []) {
    for (const c of step.content || []) if (c && c.type === "audio" && c.data) data = c.data
  }
  if (!data && res.json.output_audio) data = res.json.output_audio.data || ""
  if (!data) return null
  const bytes = base64Bytes(data)
  // Older TTS models send bare PCM (16-bit, 24 kHz, mono): give it a WAV header.
  return isWav(bytes) ? bytes : wav(bytes, 24000)
}

function isWav(bytes) {
  return bytes.length > 12 && bytes[0] === 82 && bytes[1] === 73 && bytes[2] === 70 && bytes[3] === 70
}

/** 16-bit mono [pcm] at [rate] as a WAV file's bytes. */
function wav(pcm, rate) {
  const head = []
  const text = (t) => { for (let i = 0; i < t.length; i++) head.push(t.charCodeAt(i)) }
  const int = (n, size) => { for (let i = 0; i < size; i++) head.push((n >> (8 * i)) & 255) }
  text("RIFF"); int(36 + pcm.length, 4); text("WAVE")
  text("fmt "); int(16, 4); int(1, 2); int(1, 2); int(rate, 4); int(rate * 2, 4); int(2, 2); int(16, 2)
  text("data"); int(pcm.length, 4)
  return head.concat(pcm)
}

/** Standard base64 to bytes (the JS hooks have no decoder of their own). */
function base64Bytes(text) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  const lookup = {}
  for (let i = 0; i < alphabet.length; i++) lookup[alphabet[i]] = i
  const clean = String(text).replace(/[^A-Za-z0-9+/]/g, "")
  const out = new Array(Math.floor(clean.length * 3 / 4))
  let n = 0
  for (let i = 0; i + 1 < clean.length; i += 4) {
    const a = lookup[clean[i]], b = lookup[clean[i + 1]]
    const c = i + 2 < clean.length ? lookup[clean[i + 2]] : -1
    const d = i + 3 < clean.length ? lookup[clean[i + 3]] : -1
    out[n++] = ((a << 2) | (b >> 4)) & 255
    if (c >= 0) out[n++] = (((b & 15) << 4) | (c >> 2)) & 255
    if (d >= 0) out[n++] = (((c & 3) << 6) | d) & 255
  }
  out.length = n
  return out
}

/** Bytes to standard base64, for sending a picture. */
function base64Text(bytes) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  const out = []
  let i = 0
  for (; i + 2 < bytes.length; i += 3) {
    const n = ((bytes[i] & 255) << 16) | ((bytes[i + 1] & 255) << 8) | (bytes[i + 2] & 255)
    out.push(alphabet[n >> 18] + alphabet[(n >> 12) & 63] + alphabet[(n >> 6) & 63] + alphabet[n & 63])
  }
  if (i < bytes.length) {
    const n = ((bytes[i] & 255) << 16) | (i + 1 < bytes.length ? (bytes[i + 1] & 255) << 8 : 0)
    out.push(alphabet[n >> 18] + alphabet[(n >> 12) & 63] + (i + 1 < bytes.length ? alphabet[(n >> 6) & 63] : "=") + "=")
  }
  return out.join("")
}

module.exports = { available, allow, generate, answerParts, writeJson, drawImage, speak, base64Bytes, base64Text, textModel, imageModel, ttsModel, voice }
