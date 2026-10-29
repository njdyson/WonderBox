/// <reference path="../pb_data/types.d.ts" />

// The TV's photo quiz (app/.../ui/PhotoQuizScreen.kt), loaded with require(). The child looks at
// one of the family photos for a few seconds, then it's hidden and they answer questions about
// what they saw ("What colour was the girl's dress?").
//
// Gemini (gemini_lib.js) looks at each photo once and writes nine questions: three easy, three
// middling and three harder, so one set suits every child. A cron job (photo_quiz.pb.js) writes
// them ahead of time for the photos of children who have the quiz on, so the TV never waits;
// they're kept in photo_quizzes, one record per photo. Nothing is sent to Gemini for a family
// until the parent turns the quiz on for a child.

// Photos looked at per family per day, counting the parent's "new questions"; more wait until
// the next day.
const PER_DAY = 60
// Tries at a photo that failed (Gemini down, an odd answer) before giving up on it.
const MAX_TRIES = 3
const RETRY_AFTER_MS = 6 * 60 * 60 * 1000

const LEVELS = [1, 2, 3]
const MAX_TEXT = 140
const MAX_ANSWER = 30

// A last check on what comes back, on top of the prompt and Google's own filters. A question
// with any of these is dropped.
const BLOCKED = /\b(fat|thin|skinny|ugly|pretty|beautiful|sexy?|naked|nude|underwear|bra|knickers|pants|bikini|body|bodies|skin|weight|race|black people|white people|religion|kill\w*|dead|death|die[sd]?|blood\w*|gun\w*|knife|knives|weapon\w*|stupid|idiot|beer|wine|drunk|cigarette\w*|scary|frighten\w*|ghost\w*|sad|crying|cry|angry|hurt|injur\w*|ill|sick|poo|wee|toilet)\b/i

function available() {
  return require(`${__hooks}/gemini_lib.js`).available()
}

/** The questions for a child of [age]: 1 (3 and 4), 2 (5 and 6) or 3 (7 and up). */
function levelFor(age) {
  return age <= 4 ? 1 : age <= 6 ? 2 : 3
}

/** A still photo that can be quizzed on: ready (not a home video still uploading) and with a picture. */
function quizzable(photo) {
  return !photo.getString("status") && !!photo.getString("image")
}

/** The user's photos for children who have the photo quiz on, as records. */
function photosToQuiz(userId) {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const on = lib.childRecords(userId).filter((c) => lib.games(c).photoQuiz.on).map((c) => c.id)
  if (!on.length) return []
  const records = $app.findRecordsByFilter("photos", "user = {:u} && status = ''", "-created", 5000, 0, { u: userId })
  const list = []
  for (let i = 0; i < records.length; i++) {
    const p = records[i]
    if (quizzable(p) && p.getStringSlice("children").some((id) => on.includes(id))) list.push(p)
  }
  return list
}

/** {photoId: record} of the user's photo_quizzes. */
function recordsByPhoto(userId) {
  const records = $app.findRecordsByFilter("photo_quizzes", "user = {:u}", "", 5000, 0, { u: userId })
  const map = {}
  for (let i = 0; i < records.length; i++) map[records[i].getString("photo")] = records[i]
  return map
}

/** Makes sure each photo to quiz has a record (new ones wait for the cron job). Returns them by photo. */
function ensure(userId) {
  const have = recordsByPhoto(userId)
  const collection = $app.findCollectionByNameOrId("photo_quizzes")
  for (const photo of photosToQuiz(userId)) {
    if (have[photo.id]) continue
    const record = new Record(collection)
    record.set("user", userId)
    record.set("photo", photo.id)
    record.set("questions", [])
    record.set("state", "")
    record.set("tries", 0)
    try {
      $app.save(record)
      have[photo.id] = record
    } catch (err) {
      // Made by another request just now.
      console.log(`photo quiz: couldn't add ${photo.id}: ${err}`)
    }
  }
  return have
}

/** The photo's picture as {type, bytes}, or null. */
function photoBytes(photo) {
  const name = photo.getString("image")
  if (!name) return null
  const fsys = $app.newFilesystem()
  try {
    const reader = fsys.getReader(photo.baseFilesPath() + "/" + name)
    try {
      const bytes = toBytes(reader)
      const type = /\.png$/i.test(name) ? "image/png" : /\.webp$/i.test(name) ? "image/webp" : "image/jpeg"
      return { type, bytes }
    } finally {
      reader.close()
    }
  } catch (err) {
    console.log(`photo quiz: couldn't read ${photo.id}: ${err}`)
    return null
  } finally {
    fsys.close()
  }
}

const SYSTEM = [
  "You write observation quiz questions about a family photo, for young children in British English.",
  "A child looks at the photo for a few seconds, then it's hidden and they answer your questions from memory, picking from a few answers. The TV reads the questions and answers aloud.",
  "Rules you must always follow:",
  "- Only ask about things that are clearly visible and certain: the colours of clothes and things, how many of something there are, which animals, toys, food or objects are there, what someone is holding or doing, where something is, the weather, inside or outside.",
  "- Talk about the photo in the past tense, since it's hidden by then: \"What colour was the girl's dress?\"",
  "- Call people only by what can be seen: \"the girl\", \"the boy\", \"the baby\", \"the man\", \"the lady\", \"the person in the blue hat\". Never guess names, ages, feelings or how people are related.",
  "- Never ask about anyone's body, skin, weight or looks, nor anything about race, religion or health. Nothing scary, sad, rude or embarrassing. Keep it gentle and fun.",
  "- No brands, logos, film or TV characters by name, and nothing that needs reading writing in the photo.",
  "- Each question has exactly one right answer that is plainly true from the photo, and wrong answers of the same kind (all colours, all numbers, all animals) that are plainly not true. Answers are one to three simple words, lower case. Yes/no questions are fine for the easiest level.",
  "- Level 1 is for 3 and 4 year olds: big, obvious things, main colours, counting to 3, 2 or 3 answers.",
  "- Level 2 is for 5 and 6 year olds: smaller things and colours, counting to 5, what someone was holding or doing, 3 answers.",
  "- Level 3 is for 7 to 9 year olds: small details, things in the background, counting to 10, where things were (next to, behind, on the left), 3 or 4 answers.",
  "- Every question asks about something different.",
  "If the photo has nothing a child could be asked about, is too blurry or dark, is a screenshot or document, or isn't suitable for children, set usable to false and give no questions.",
].join("\n")

const ASK = "Write three questions at each level (1, 2 and 3) about this photo: nine in all."

const SCHEMA = {
  type: "OBJECT",
  properties: {
    usable: { type: "BOOLEAN" },
    questions: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          level: { type: "INTEGER" },
          text: { type: "STRING" },
          right: { type: "STRING" },
          wrong: { type: "ARRAY", items: { type: "STRING" } },
        },
        required: ["level", "text", "right", "wrong"],
      },
    },
  },
  required: ["usable", "questions"],
}

/** One answer as shown on the TV: plain words, or "" if it isn't one. */
function cleanAnswer(text) {
  const t = String(text || "").replace(/[*_#`<>{}\[\]"]/g, "").replace(/\s+/g, " ").trim().toLowerCase()
  return t && t.length <= MAX_ANSWER && !BLOCKED.test(t) ? t : ""
}

/** Gemini's answer as [{level, text, right, wrong}], each checked; questions that fail are dropped. */
function parse(answer) {
  if (!answer || answer.usable === false) return []
  const list = Array.isArray(answer.questions) ? answer.questions : []
  const out = []
  const seen = new Set()
  for (const q of list) {
    if (!q || typeof q !== "object") continue
    const level = Math.round(Number(q.level))
    let text = String(q.text || "").replace(/[*_#`<>{}\[\]]/g, "").replace(/\s+/g, " ").trim()
    if (!LEVELS.includes(level) || !text || text.length > MAX_TEXT || BLOCKED.test(text)) continue
    if (!/[?]$/.test(text)) text += "?"
    const right = cleanAnswer(q.right)
    const wrong = []
    for (const w of Array.isArray(q.wrong) ? q.wrong : []) {
      const a = cleanAnswer(w)
      if (a && a !== right && !wrong.includes(a)) wrong.push(a)
    }
    if (!right || !wrong.length || seen.has(text.toLowerCase())) continue
    seen.add(text.toLowerCase())
    out.push({ level, text, right, wrong: wrong.slice(0, 3) })
  }
  return out
}

/**
 * Has Gemini look at [record]'s photo and write its questions, then saves it as "done", "none"
 * (nothing to ask) or "failed". Counts towards the family's day.
 */
function write(record) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  let photo
  try {
    photo = $app.findRecordById("photos", record.getString("photo"))
  } catch (_) {
    return record // deleted just now; the record goes with it
  }
  record.set("state", "writing")
  record.set("tries", record.getInt("tries") + 1)
  $app.save(record)
  let questions = null
  try {
    const image = photoBytes(photo)
    const answer = image ? gemini.writeJson(SYSTEM, ASK, SCHEMA, { images: [image], temperature: 0.6, maxTokens: 4096, timeout: 45 }) : null
    if (answer) questions = parse(answer)
  } catch (err) {
    console.log(`photo quiz ${photo.id}: ${err}`)
  }
  record.set("questions", questions || [])
  record.set("state", questions === null ? "failed" : questions.length >= 3 ? "done" : "none")
  $app.save(record)
  return record
}

/** Writes the questions for up to [limit] photos that haven't got them yet, across families (the cron job). */
function writePending(limit) {
  if (!available()) return
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const users = new Set()
  const children = $app.findRecordsByFilter("children", "", "", 5000, 0)
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  for (let i = 0; i < children.length; i++) {
    if (lib.games(children[i]).photoQuiz.on) users.add(children[i].getString("user"))
  }
  let done = 0
  for (const userId of users) {
    if (done >= limit) return
    const records = Object.values(ensure(userId))
    const retryBefore = Date.now() - RETRY_AFTER_MS
    const waiting = records.filter((r) => !r.getBool("off") && (r.getString("state") === "" ||
      (r.getString("state") === "failed" && r.getInt("tries") < MAX_TRIES && new Date(r.getString("updated").replace(" ", "T")).getTime() < retryBefore)))
    for (const record of waiting) {
      if (done >= limit) return
      try {
        gemini.allow(userId, "photoquiz", PER_DAY)
      } catch (_) {
        break // the rest tomorrow
      }
      write(record)
      done++
    }
  }
}

/** One record as the phone app and the TV see it. */
function entry(record) {
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const photoId = record.getString("photo")
  return {
    photo: photoId,
    thumb: `/api/wonder/photos/${photoId}/thumb`,
    state: record.getString("state") === "writing" ? "" : record.getString("state"),
    off: record.getBool("off"),
    questions: quiz.jsonField(record, "questions") || [],
  }
}

/**
 * The photos [child] can be quizzed on, each with its questions: [{photo, questions}], newest
 * photo first. When none are ready yet but some are waiting, one is written now (the TV shows
 * "getting ready" meanwhile), so a child who's just had it turned on can play straight away.
 */
function forChild(userId, child) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  if (!available()) throw new ApiError(503, "The photo quiz needs a Gemini key on the server.")
  if (!lib.games(child).photoQuiz.on) return []
  const records = ensure(userId)
  const mine = photosToQuiz(userId).filter((p) => p.getStringSlice("children").includes(child.id))
  const ready = () => mine.map((p) => records[p.id]).filter((r) => r && !r.getBool("off") && r.getString("state") === "done")
  let list = ready()
  if (!list.length) {
    const waiting = mine.map((p) => records[p.id]).find((r) => r && !r.getBool("off") && r.getString("state") === "")
    if (waiting) {
      gemini.allow(userId, "photoquiz", PER_DAY)
      write(waiting)
      list = ready()
    }
  }
  return list.map(entry).map((e) => ({ photo: e.photo, questions: e.questions }))
}

/** Every photo's questions for the phone app: [{photo, thumb, children, state, off, questions}], newest first. */
function listFor(userId) {
  const records = ensure(userId)
  return photosToQuiz(userId).filter((p) => records[p.id]).map((p) => Object.assign(entry(records[p.id]), { children: p.getStringSlice("children") }))
}

/** The parent asks for new questions about [photoId] (written now). Returns the list. */
function redo(userId, photoId) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  if (!available()) throw new ApiError(503, "The photo quiz needs a Gemini key on the server.")
  const record = ensure(userId)[photoId]
  if (!record) throw new NotFoundError("That photo isn't in the quiz.")
  gemini.allow(userId, "photoquiz", PER_DAY)
  record.set("tries", 0)
  write(record)
  return listFor(userId)
}

/** Leaves [photoId] out of the quiz ([off]) or puts it back. Returns the list. */
function setOff(userId, photoId, off) {
  const record = ensure(userId)[photoId]
  if (!record) throw new NotFoundError("That photo isn't in the quiz.")
  record.set("off", !!off)
  $app.save(record)
  return listFor(userId)
}

module.exports = { available, levelFor, parse, write, writePending, forChild, listFor, redo, setOff, ensure, PER_DAY }
