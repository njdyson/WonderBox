/// <reference path="../pb_data/types.d.ts" />

// Google Gemini, for anything on the server that writes or draws (the story maker in
// story_lib.js so far), loaded with require(). One key for everything: GEMINI_API_KEY in
// <data>/wonderbox.env (deploy/install.sh). GEMINI_MODEL and GEMINI_IMAGE_MODEL change the
// default models. Every call has Google's safety filters at their strictest, since it's all
// for children.

const TEXT_MODEL = "gemini-3.6-flash"
const IMAGE_MODEL = "gemini-2.5-flash-image"

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
    url: `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
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
 * [prompt]. Quick: little thinking. Returns the parsed object, or null.
 */
function writeJson(system, prompt, schema, options) {
  const o = options || {}
  const body = {
    systemInstruction: { parts: [{ text: system }] },
    contents: [{ role: "user", parts: [{ text: prompt }] }],
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

module.exports = { available, allow, generate, answerParts, writeJson, drawImage, base64Bytes, textModel, imageModel }
