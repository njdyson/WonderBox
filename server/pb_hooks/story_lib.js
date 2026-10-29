/// <reference path="../pb_data/types.d.ts" />

// The TV's story maker (app/.../ui/StoryScreen.kt), loaded with require(). The child picks one
// of three pictures for each part of a story; for each part the TV sends the story so far and
// the three pictures on offer, and Gemini writes all three possible next parts in one go, so
// whichever the child picks is usually ready by the time they pick it.
//
// Gemini is reached through gemini_lib.js (one key for everything). Without a key the TV
// tells a simple story of its own.

// A child can't run up a bill: at most this many parts written per family per day.
const DAILY_LIMIT = 300
const MAX_TURNS = 10
const MAX_PART = 700

// A last check on what comes back, on top of the prompt and Google's own filters. A part with
// any of these is swapped for the TV's simple story.
const BLOCKED = /\b(kill\w*|dead|death|die[sd]?|dying|blood\w*|gun\w*|knife|knives|weapon\w*|stab\w*|shoot\w*|murder\w*|stupid|idiot|shut up|sexy?|drunk|beer|wine|cigarette\w*|devil|hell|damn|god|ghost\w*|zombie\w*|skeleton\w*|witch\w*|nightmare\w*|scream\w*|terrif\w*|horrible|scary|frighten\w*)\b/i

function available() {
  return require(`${__hooks}/gemini_lib.js`).available()
}

/** How long each part is for a child of [age]: sentences and a word limit. */
function lengthFor(age) {
  if (age <= 4) return { sentences: "two short sentences", words: 30 }
  if (age === 5) return { sentences: "two or three short sentences", words: 40 }
  if (age === 6) return { sentences: "three sentences", words: 50 }
  return { sentences: "three or four sentences", words: 65 }
}

/** A picture's word as sent by the TV: plain lower-case words only, so nothing else gets into the prompt. */
function cleanWord(word) {
  const w = String(word || "").toLowerCase().replace(/[^a-z ]/g, "").replace(/\s+/g, " ").trim()
  return w.length >= 2 && w.length <= 20 ? w : ""
}

/** One part of the story so far, as the TV sends it back: plain text, one line. */
function cleanPart(text) {
  return String(text || "").replace(/\s+/g, " ").trim().slice(0, MAX_PART)
}

/** What this part of the story is for: who it's about, where they go, what they find, how it ends. */
function roleOf(turn, turns, word) {
  if (turn === 1) return `Begin the story with "Once upon a time". The hero is a ${word}: give them a friendly name.`
  if (turn === 2) return `The hero sets off and goes to (or arrives at) the ${word}.`
  if (turn === turns) return `This is the last part. Bring the story to a warm, happy ending that involves the ${word}. The final sentence should feel like an ending, but don't write "The end".`
  return `Something fun or surprising happens with a ${word}. Leave a little curiosity about what happens next.`
}

/**
 * The three possible next parts of a story: [{word, text}], in the order of [options].
 * [story] is the parts so far, [{word, text}]; [turn] is 1 for the first part, up to [turns].
 * A part that's missing or fails the check has text "".
 */
function write(userId, child, setup, turn, turns, story, options) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  if (!gemini.available()) throw new ApiError(503, "The story maker needs a Gemini key on the server.")
  gemini.allow(userId, "story", DAILY_LIMIT)

  const name = String(child.getString("name") || "").replace(/[^\p{L} '-]/gu, "").trim().slice(0, 30) || "the child"
  const age = setup.age
  const length = lengthFor(age)
  const system = [
    `You write short, gentle, happy stories for a young child in British English. The child listening is called ${name} and is ${age} years old.`,
    "The child chooses what happens next by picking a picture, so the story is built one part at a time.",
    "Rules you must always follow:",
    "- Kind, cheerful and cosy, like a picture book. Nothing scary, violent, sad, mean, rude, romantic or grown-up. No danger, villains, injuries, illness, death, monsters or getting lost. A small puzzle is fine if it's solved happily.",
    "- No real people, brands, films, TV shows, games, screens or phones.",
    `- Simple everyday words a ${age}-year-old knows, and short sentences that read aloud well. British spelling.`,
    `- Each part is ${length.sentences}, at most ${length.words} words. No title, no lists, no emoji.`,
    `- Now and then, ${name} can be a friend who helps in the story, but the hero is the character from the first picture.`,
    "- Keep the same hero, names and details as the story so far.",
  ].join("\n")
  const sofar = story.length
    ? "The story so far:\n" + story.map((p) => p.text).join("\n")
    : "The story hasn't started yet."
  const ask = [
    sofar,
    "",
    `Now write part ${turn} of ${turns}.`,
    `The child will pick one of these pictures: ${options.map((w) => `"${w}"`).join(", ")}.`,
    "Write one possible next part for each picture, in that order, each following on from the story so far. Each must use its picture's word naturally.",
    ...options.map((w, i) => `${i + 1}. "${w}": ${roleOf(turn, turns, w)}`),
  ].join("\n")
  const schema = {
    type: "OBJECT",
    properties: {
      parts: {
        type: "ARRAY",
        items: { type: "OBJECT", properties: { word: { type: "STRING" }, text: { type: "STRING" } }, required: ["word", "text"] },
      },
    },
    required: ["parts"],
  }
  const answer = gemini.writeJson(system, ask, schema)
  if (!answer) throw new ApiError(502, "Couldn't reach Gemini to write the story.")
  return parse(answer, options)
}

/** Gemini's {parts: [{word, text}]} as [{word, text}] in the order of [options], each checked. */
function parse(answer, options) {
  const parts = Array.isArray(answer && answer.parts) ? answer.parts : []
  return options.map((word, i) => {
    const found = parts.find((p) => p && cleanWord(p.word) === word) || parts[i] || {}
    return { word, text: check(found.text) }
  })
}

/** A part fit to read to a child, or "" (the TV then tells that part itself). */
function check(text) {
  let t = String(text || "").replace(/[*_#`<>{}\[\]]/g, "").replace(/\s+/g, " ").trim()
  if (!t || BLOCKED.test(t)) return ""
  if (t.length > MAX_PART) {
    // Cut at the last full sentence that fits.
    const cut = t.slice(0, MAX_PART)
    const end = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
    t = end > 40 ? cut.slice(0, end + 1) : ""
  }
  return t
}

// --- The story library --------------------------------------------------------

// A picture for each finished story is drawn by a cron job (story.pb.js), so nobody waits.
// Stories kept per child; the oldest go first.
const KEEP_PER_CHILD = 60
// Pictures drawn per family per day; more wait until the next day.
const PICTURES_PER_DAY = 30
// An emoji: a few characters, none of them plain ASCII.
const PICTURE = /^[^\x00-\x7F]{1,16}$/

const cap = (w) => w.replace(/(^|\s)\S/g, (c) => c.toUpperCase())

/** A title from the pictures picked: "The Fox and the Castle", "The Owl in Space". */
function titleOf(parts) {
  const hero = parts[0] && parts[0].word
  const place = parts[1] && parts[1].word
  if (!hero) return "My story"
  if (!place) return `The ${cap(hero)}`
  if (["space", "snow", "sky", "woods"].includes(place)) return `The ${cap(hero)} in ${place === "space" || place === "snow" ? "" : "the "}${cap(place)}`
  return `The ${cap(hero)} and the ${cap(place)}`
}

/** Saves a finished story for [child]: parts [{word, picture, text}] as the TV sends them. */
function save(userId, child, parts) {
  const clean = (Array.isArray(parts) ? parts : []).slice(0, MAX_TURNS).map((p) => ({
    word: cleanWord(p && p.word),
    picture: PICTURE.test(String((p && p.picture) || "")) ? String(p.picture) : "",
    text: cleanPart(p && p.text),
  })).filter((p) => p.text)
  if (!clean.length) throw new BadRequestError("There's no story to save.")
  const record = new Record($app.findCollectionByNameOrId("stories"))
  record.set("user", userId)
  record.set("child", child.id)
  record.set("title", titleOf(clean))
  record.set("parts", clean)
  record.set("picture_state", "")
  $app.save(record)
  const all = $app.findRecordsByFilter("stories", "child = {:c}", "-created", 1000, 0, { c: child.id })
  for (let i = KEEP_PER_CHILD; i < all.length; i++) $app.delete(all[i])
  return record
}

/** The user's stories, newest first, as the TV and the phone app list them. */
function storyList(userId) {
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const records = $app.findRecordsByFilter("stories", "user = {:u}", "-created", 500, 0, { u: userId })
  const list = []
  for (let i = 0; i < records.length; i++) {
    const r = records[i]
    list.push({
      id: r.id,
      child: r.getString("child"),
      title: r.getString("title"),
      parts: quiz.jsonField(r, "parts") || [],
      // The file name changes when it's redrawn, so a cached copy is never shown instead.
      picture: r.getString("picture") ? `/api/wonder/stories/${r.id}/picture?v=${encodeURIComponent(r.getString("picture"))}` : "",
      // Still waiting for its picture (drawn by the cron job within a minute or two).
      drawing: !r.getString("picture") && r.getString("picture_state") !== "failed" && available(),
      created: r.getString("created"),
    })
  }
  return list
}

/**
 * Asks Gemini to draw a picture for [record] (a story) and saves it. Returns true when it
 * worked. The picture shows the story's hero, never a person.
 */
function draw(record) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const parts = quiz.jsonField(record, "parts") || []
  const hero = (parts[0] && parts[0].word) || "animal"
  const story = parts.map((p) => p.text).join(" ").slice(0, 2500)
  const prompt = [
    "Draw one warm, gentle picture-book illustration for this children's story, in landscape (4:3).",
    "The illustration must fill the whole picture edge to edge: no borders, frames, margins or black bars.",
    `The hero is a friendly cartoon ${hero}. Show the happiest moment of the story.`,
    "Style: soft colours, rounded shapes, cosy and cheerful, like a picture book for 4 to 6 year olds.",
    "Do not draw any humans or children, only the animals, creatures and things from the story. No words, letters or numbers in the picture. Nothing scary.",
    "",
    "The story:",
    story,
  ].join("\n")
  const image = gemini.drawImage(prompt, "4:3")
  if (!image) return false
  const ext = image.type.includes("jpeg") ? "jpg" : image.type.includes("webp") ? "webp" : "png"
  record.set("picture", $filesystem.fileFromBytes(image.bytes, `story.${ext}`))
  return true
}

/** Draws pictures for up to [limit] stories that haven't got one yet (the cron job). */
function drawPending(limit) {
  if (!available()) return
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const pending = $app.findRecordsByFilter("stories", "picture_state = ''", "created", limit, 0)
  for (let i = 0; i < pending.length; i++) {
    const record = pending[i]
    try {
      gemini.allow(record.getString("user"), "picture", PICTURES_PER_DAY)
    } catch (_) {
      continue // drawn tomorrow
    }
    record.set("picture_state", "drawing")
    $app.save(record)
    let ok = false
    try {
      ok = draw(record)
    } catch (err) {
      console.log(`story picture: ${err}`)
    }
    record.set("picture_state", ok ? "done" : "failed")
    try {
      $app.save(record)
    } catch (err) {
      console.log(`story picture: couldn't save it: ${err}`)
      record.set("picture", null)
      record.set("picture_state", "failed")
      $app.save(record)
    }
  }
}

module.exports = {
  available, write, parse, check, cleanWord, cleanPart, lengthFor, MAX_TURNS, DAILY_LIMIT,
  save, storyList, titleOf, draw, drawPending,
}
