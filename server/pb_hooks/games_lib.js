/// <reference path="../pb_data/types.d.ts" />

// Settings for the TV's hangman and memory pairs (app/.../games/Hangman.kt and Memory.kt),
// loaded with require(). Each child's are kept in children.game_setup next to maths and
// spelling; anything missing or odd gets the default.

const WORD_LISTS = ["short", "long", "mine"]
const HANGMAN_LETTERS = [8, 12, 26]
const HANGMAN_BALLOONS = [4, 6, 8]
const MEMORY_PAIRS = [3, 4, 6, 8, 10]
const MEMORY_PICTURES = ["clipart", "photos", "both"]

const HANGMAN_DEFAULT = { on: true, lists: ["short"], letters: 8, balloons: 6, words: 3, say: true }
const MEMORY_DEFAULT = { on: true, pairs: 4, pictures: "clipart" }

/** The nearest of [allowed] to [value], or [fallback] when it isn't a number. */
function nearest(value, allowed, fallback) {
  const n = Number(value)
  if (value === undefined || value === null || value === "" || !Number.isFinite(n)) return fallback
  return allowed.reduce((best, x) => (Math.abs(x - n) < Math.abs(best - n) ? x : best), allowed[0])
}

/** A child's hangman: {on, lists, letters, balloons, words, say}. */
function hangmanSetup(value) {
  const v = value && typeof value === "object" ? value : {}
  const lists = Array.isArray(v.lists) ? WORD_LISTS.filter((l) => v.lists.includes(l)) : []
  const words = Math.round(Number(v.words))
  return {
    on: v.on === undefined ? HANGMAN_DEFAULT.on : !!v.on,
    lists: lists.length ? lists : HANGMAN_DEFAULT.lists,
    letters: nearest(v.letters, HANGMAN_LETTERS, HANGMAN_DEFAULT.letters),
    balloons: nearest(v.balloons, HANGMAN_BALLOONS, HANGMAN_DEFAULT.balloons),
    words: Number.isFinite(words) ? Math.min(10, Math.max(1, words)) : HANGMAN_DEFAULT.words,
    say: v.say === undefined ? HANGMAN_DEFAULT.say : !!v.say,
  }
}

/** A child's memory pairs: {on, pairs, pictures}. */
function memorySetup(value) {
  const v = value && typeof value === "object" ? value : {}
  return {
    on: v.on === undefined ? MEMORY_DEFAULT.on : !!v.on,
    pairs: nearest(v.pairs, MEMORY_PAIRS, MEMORY_DEFAULT.pairs),
    pictures: MEMORY_PICTURES.includes(v.pictures) ? v.pictures : MEMORY_DEFAULT.pictures,
  }
}

// Stars (app/.../data/Api.kt PointsSetup): per right answer, per finished jigsaw, hangman or
// pairs game, minutes of a learning video per star (with a daily cap, 0 for none), and
// minutes of a treat or music video one star buys.
const POINTS_DEFAULT = { answer: 1, game: 3, videoMinutes: 3, videoCap: 10, treatMinutes: 1 }
const POINTS_LIMITS = { answer: [0, 10], game: [0, 20], videoMinutes: [1, 30], videoCap: [0, 100], treatMinutes: [1, 10] }

/** A child's star rates, {answer, game, videoMinutes, videoCap, treatMinutes}, each kept in range. */
function pointsSetup(value) {
  const v = value && typeof value === "object" ? value : {}
  const out = {}
  for (const key of Object.keys(POINTS_DEFAULT)) {
    const n = Math.round(Number(v[key]))
    const [min, max] = POINTS_LIMITS[key]
    out[key] = v[key] !== undefined && v[key] !== null && Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : POINTS_DEFAULT[key]
  }
  return out
}

module.exports = { hangmanSetup, memorySetup, pointsSetup }
