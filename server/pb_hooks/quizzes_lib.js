/// <reference path="../pb_data/types.d.ts" />

// Helpers for quizzes.pb.js and the game settings in wonderbox_lib.js, loaded with require().
//
// Two kinds of game:
// - Maths and spelling, made up on the TV from each child's "setup" (which kinds of
//   question, how big the numbers, which words). Each numbered level is a ready-made setup;
//   the phone app can change any part of it.
// - Quizzes the parent writes: questions with 2 to 4 answers, each words, a picture or both.
//
// Pictures are an emoji (as text) or a quiz image the phone uploaded, given as its path
// /api/wonder/quiz-images/<id>. The phone copies Fluent Emoji clipart up as quiz images too,
// so the TV only ever talks to this server.

const MATHS_TYPES = ["count", "add", "sub", "add3", "missing", "bonds", "double", "half", "more", "bigger", "times"]
const SPELLING_TYPES = ["first", "missing", "pick", "build", "hear", "hearbuild"]
const CLOCK_TYPES = ["oclock", "half", "quarter", "five", "minute"]
// The games that can win a treat on the TV ("quizzes" is all the parent's quizzes), and the
// ones that do until the parent changes it: the learning ones, not the jigsaw or memory pairs.
const TREAT_GAMES = ["maths", "spelling", "clock", "quizzes", "hangman", "memory", "jigsaw"]
const TREAT_DEFAULT = ["maths", "spelling", "clock", "quizzes", "hangman"]
const WORD_LISTS = ["short", "long", "phase2", "mine"]
const MATHS_MAX = [5, 10, 20, 50, 100]

// The TV's levels (app/.../games/Questions.kt and the phone app keep the same list).
const MATHS_PRESETS = [
  { types: ["count"], max: 5, choices: 3, pictures: true },
  { types: ["add"], max: 5, choices: 3, pictures: true },
  { types: ["add"], max: 10, choices: 4, pictures: false },
  { types: ["add", "sub"], max: 10, choices: 4, pictures: false },
  { types: ["add", "sub"], max: 20, choices: 4, pictures: false },
  { types: ["times"], max: 10, tables: [2, 5, 10], choices: 4, pictures: false },
]
const SPELLING_PRESETS = [
  { types: ["first"], lists: ["short"], choices: 3 },
  { types: ["missing"], lists: ["short"], choices: 3 },
  { types: ["missing"], lists: ["long"], choices: 4 },
  { types: ["pick"], lists: ["short", "long"], choices: 3 },
  { types: ["build"], lists: ["long"], choices: 4 },
]
const CLOCK_PRESETS = [
  { types: ["oclock"], choices: 3 },
  { types: ["oclock", "half"], choices: 3 },
  { types: ["oclock", "half", "quarter"], choices: 4 },
  { types: ["five"], choices: 4 },
  { types: ["minute"], choices: 4 },
]

const IMAGE_PATH = /^\/api\/wonder\/quiz-images\/[a-z0-9]{15}$/

/** A JSON field as a plain value (they come back as raw bytes), or null. */
function jsonField(record, name) {
  try {
    const text = toString(record.get(name))
    return text ? JSON.parse(text) : null
  } catch (_) {
    return null
  }
}

/** The values of [list] that are in [allowed], in [allowed]'s order. */
function pick(list, allowed) {
  const set = new Set(Array.isArray(list) ? list.map(String) : [])
  return allowed.filter((v) => set.has(v))
}

function int(value, min, max, fallback) {
  const n = Math.round(Number(value))
  return value !== undefined && value !== null && value !== "" && Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
}

/** An emoji, a quiz image's path, or "" for anything else. */
function picture(value) {
  const s = String(value || "").trim()
  if (IMAGE_PATH.test(s)) return s
  if (/^[a-z0-9]{15}$/.test(s)) return `/api/wonder/quiz-images/${s}`
  return s.length <= 16 && !/[\/<>]/.test(s) ? s : ""
}

function mathsPreset(level) {
  const p = MATHS_PRESETS[int(level, 1, MATHS_PRESETS.length, 1) - 1]
  return { on: true, types: [...p.types], max: p.max, tables: [...(p.tables || [2, 5, 10])], choices: p.choices, pictures: p.pictures }
}

function spellingPreset(level, keep) {
  const p = SPELLING_PRESETS[int(level, 1, SPELLING_PRESETS.length, 1) - 1]
  return { on: true, types: [...p.types], lists: [...p.lists], choices: p.choices, myWords: (keep && keep.myWords) || [] }
}

/** A maths setup from the phone (or stored), checked; falls back to [level]'s for anything missing. */
function mathsSetup(input, level) {
  const base = mathsPreset(level)
  if (!input || typeof input !== "object") return base
  const types = pick(input.types, MATHS_TYPES)
  const tables = [...new Set((Array.isArray(input.tables) ? input.tables : []).map(Number).filter((t) => Number.isInteger(t) && t >= 1 && t <= 12))].sort((a, b) => a - b)
  return {
    on: input.on !== false,
    types: types.length ? types : base.types,
    max: MATHS_MAX.includes(Number(input.max)) ? Number(input.max) : base.max,
    tables: tables.length ? tables : base.tables,
    choices: int(input.choices, 2, 4, base.choices),
    pictures: input.pictures === undefined ? base.pictures : !!input.pictures,
  }
}

/** A spelling setup, checked, including the parent's own words: [{word, picture}]. */
function spellingSetup(input, level) {
  const base = spellingPreset(level)
  if (!input || typeof input !== "object") return base
  const seen = new Set()
  const myWords = []
  for (const w of Array.isArray(input.myWords) ? input.myWords : []) {
    const word = String((w && w.word) || w || "").trim().toLowerCase()
    if (!/^[a-z]{2,12}$/.test(word) || seen.has(word)) continue
    seen.add(word)
    myWords.push({ word, picture: picture(w && w.picture) })
    if (myWords.length >= 300) break
  }
  const types = pick(input.types, SPELLING_TYPES)
  let lists = pick(input.lists, WORD_LISTS)
  if (!myWords.length) lists = lists.filter((l) => l !== "mine")
  return {
    on: input.on !== false,
    types: types.length ? types : base.types,
    lists: lists.length ? lists : base.lists.filter((l) => l !== "mine"),
    choices: int(input.choices, 2, 4, base.choices),
    myWords,
  }
}

function clockPreset(level) {
  const p = CLOCK_PRESETS[int(level, 1, CLOCK_PRESETS.length, 1) - 1]
  return { on: true, types: [...p.types], choices: p.choices }
}

/** A clock setup: which times to read (o'clock, half past...) and how many answers. */
function clockSetup(input, level) {
  const base = clockPreset(level)
  if (!input || typeof input !== "object") return base
  const types = pick(input.types, CLOCK_TYPES)
  return {
    on: input.on !== false,
    types: types.length ? types : base.types,
    choices: int(input.choices, 2, 4, base.choices),
  }
}

/** The games that win a treat, checked; the default ones if it's never been set. */
function treatGames(input) {
  return Array.isArray(input) ? pick(input, TREAT_GAMES) : [...TREAT_DEFAULT]
}

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b)

/** The level whose setup this is (ignoring on/off), or 0 if it's been changed. */
function mathsLevelOf(setup) {
  const strip = (s) => ({ types: s.types, max: s.max, tables: s.types.includes("times") ? s.tables : [], choices: s.choices, pictures: s.pictures })
  return MATHS_PRESETS.findIndex((_, i) => same(strip(mathsPreset(i + 1)), strip(setup))) + 1
}

/** The level whose setup this is (ignoring on/off and the parent's own words), or 0. */
function spellingLevelOf(setup) {
  const strip = (s) => ({ types: s.types, lists: s.lists.filter((l) => l !== "mine"), choices: s.types.every((t) => t === "build") ? 0 : s.choices })
  if (setup.lists.includes("mine")) return 0
  return SPELLING_PRESETS.findIndex((_, i) => same(strip(spellingPreset(i + 1)), strip(setup))) + 1
}

/** The level whose setup this is (ignoring on/off), or 0. */
function clockLevelOf(setup) {
  const strip = (s) => ({ types: s.types, choices: s.choices })
  return CLOCK_PRESETS.findIndex((_, i) => same(strip(clockPreset(i + 1)), strip(setup))) + 1
}

// --- The parent's own quizzes ----------------------------------------------------

function quizRecords(userId) {
  const records = $app.findRecordsByFilter("quizzes", "user = {:u}", "sort,created", 500, 0, { u: userId })
  const list = []
  for (let i = 0; i < records.length; i++) list.push(records[i])
  return list
}

/** The quizzes, as the TV and the phone app show them. */
function quizList(userId) {
  let records = []
  try {
    records = quizRecords(userId)
  } catch (_) {
    return [] // the quizzes migration hasn't run yet
  }
  return records.map((q) => ({
    id: q.id,
    title: q.getString("title"),
    icon: q.getString("icon"),
    children: q.getStringSlice("children"),
    shuffle: q.getBool("shuffle"),
    questions: jsonField(q, "questions") || [],
  }))
}

/** {title, icon, children, shuffle, questions}, checked. Throws a BadRequestError saying what's wrong. */
function cleanQuiz(body, userId, ownChildIds) {
  const title = String(body.title || "").trim().slice(0, 60)
  if (!title) throw new BadRequestError("Give the quiz a name.")
  const raw = Array.isArray(body.questions) ? body.questions : []
  if (raw.length > 100) throw new BadRequestError("A quiz can have up to 100 questions.")
  const questions = raw.map((q, n) => {
    q = q || {}
    const text = String(q.text || "").trim().slice(0, 200)
    const answers = (Array.isArray(q.answers) ? q.answers : []).slice(0, 4).map((a) => ({
      text: String((a && a.text) || "").trim().slice(0, 60),
      picture: picture(a && a.picture),
    }))
    const label = `Question ${n + 1}`
    if (!text && !picture(q.picture)) throw new BadRequestError(`${label} needs some words or a picture.`)
    if (answers.length < 2) throw new BadRequestError(`${label} needs at least two answers.`)
    if (answers.some((a) => !a.text && !a.picture)) throw new BadRequestError(`${label} has an empty answer.`)
    const correct = int(q.correct, 0, answers.length - 1, 0)
    return { text, picture: picture(q.picture), answers, correct }
  })
  if (!questions.length) throw new BadRequestError("Add a question first.")
  return {
    title,
    icon: picture(body.icon) || "❓",
    children: ownChildIds(body.children, userId),
    shuffle: body.shuffle !== false,
    questions,
  }
}

/** Every quiz image a quiz or a child's own spelling words uses. */
function usedImages(userId) {
  const used = new Set()
  const add = (p) => { if (IMAGE_PATH.test(p || "")) used.add(p.split("/").pop()) }
  for (const q of quizList(userId)) {
    add(q.icon)
    for (const question of q.questions) {
      add(question.picture)
      for (const a of question.answers || []) add(a.picture)
    }
  }
  const children = $app.findRecordsByFilter("children", "user = {:u}", "", 500, 0, { u: userId })
  for (let i = 0; i < children.length; i++) {
    const setup = jsonField(children[i], "game_setup") || {}
    for (const w of (setup.spelling && setup.spelling.myWords) || []) add(w.picture)
  }
  return used
}

/**
 * Deletes quiz images nothing uses any more. Only ones over a day old, so a picture the
 * phone has just uploaded for a quiz that isn't saved yet stays.
 */
function cleanupImages(userId) {
  const used = usedImages(userId)
  const before = new Date(Date.now() - 24 * 3600 * 1000).toISOString().replace("T", " ")
  const old = $app.findRecordsByFilter("quiz_images", "user = {:u} && created < {:b}", "", 1000, 0, { u: userId, b: before })
  for (let i = 0; i < old.length; i++) {
    if (!used.has(old[i].id)) $app.delete(old[i])
  }
}

module.exports = {
  jsonField, mathsSetup, spellingSetup, mathsPreset, spellingPreset, mathsLevelOf, spellingLevelOf,
  clockSetup, clockPreset, clockLevelOf, treatGames, CLOCK_PRESETS,
  quizRecords, quizList, cleanQuiz, cleanupImages, IMAGE_PATH,
}
