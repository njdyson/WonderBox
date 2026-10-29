/// <reference path="../pb_data/types.d.ts" />

// GET /api/wonder/speech?text=…[&slow=1]: the server's voice reading the text, as a WAV file, for
// a linked TV (X-Device-Key) or the signed-in parent; slow=1 is for stories: Gemini's storyteller
// voice when the server has a Gemini key, otherwise Piper reading more slowly. 404 if this server
// has no voice.
routerAdd("GET", "/api/wonder/speech", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const speech = require(`${__hooks}/speech_lib.js`)
  const user = lib.caller(e)
  const text = speech.clean(e.request.url.query().get("text"))
  if (!text) throw new BadRequestError("Nothing to say.")
  const slow = e.request.url.query().get("slow") === "1"
  if (!speech.available() && !(slow && speech.storyteller())) throw new NotFoundError("This server has no voice.")
  const file = speech.clip(text, slow, user.id)
  // The same text always sounds the same, so the TV can keep it.
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  return e.blob(200, "audio/wav", $os.readFile(file))
})

// GET /api/wonder/voice: whether the server's voice is set up, and if not, why. Open to
// anyone (it says nothing private), so it can be checked in a browser after installing.
routerAdd("GET", "/api/wonder/voice", (e) => {
  const speech = require(`${__hooks}/speech_lib.js`)
  const problem = speech.problem()
  return e.json(200, { voice: !problem, problem, storyteller: speech.storyteller() })
})
