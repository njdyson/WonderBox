/// <reference path="../pb_data/types.d.ts" />

// GET /api/wonder/speech?text=…: the server's voice reading the text, as a WAV file, for a
// linked TV (X-Device-Key) or the signed-in parent. 404 if this server has no voice.
routerAdd("GET", "/api/wonder/speech", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const speech = require(`${__hooks}/speech_lib.js`)
  lib.caller(e)
  const text = speech.clean(e.request.url.query().get("text"))
  if (!text) throw new BadRequestError("Nothing to say.")
  if (!speech.available()) throw new NotFoundError("This server has no voice.")
  const file = speech.clip(text)
  // The same text always sounds the same, so the TV can keep it.
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  return e.blob(200, "audio/wav", $os.readFile(file))
})
