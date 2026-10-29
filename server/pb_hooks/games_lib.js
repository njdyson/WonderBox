/// <reference path="../pb_data/types.d.ts" />

// Settings for the TV's hangman, word game, memory pairs and story maker (app/.../games/Hangman.kt,
// WordGame.kt, Memory.kt and Story.kt), loaded with require(). Each child's are kept in
// children.game_setup next to maths and spelling; anything missing or odd gets the default.

const WORD_LISTS = ["short", "long", "big", "mine"]
const HANGMAN_LETTERS = [8, 12, 26]
const HANGMAN_BALLOONS = [4, 6, 8]
const MEMORY_PAIRS = [3, 4, 6, 8, 10]
const MEMORY_PICTURES = ["clipart", "photos", "both"]

const HANGMAN_DEFAULT = { on: true, lists: ["short"], letters: 8, balloons: 6, words: 3, say: true }
const MEMORY_DEFAULT = { on: true, pairs: 4, pictures: "clipart" }
// The word game: levels 1 and 2 unjumble a word (from these lists), 3 to 5 make words from 5, 6 or 7 letters.
const SCRAMBLE_LISTS = ["phase2", "short", "long", "big", "mine"]
const SCRAMBLE_LEVELS = 5
const SCRAMBLE_DEFAULT = { on: true, level: 1, lists: ["phase2"], words: 3, say: true }
// The story maker (story_lib.js): how old the listener is (it sets how long and how simple each
// part is) and how many pictures they pick in a story.
const STORY_AGES = [3, 4, 5, 6, 7, 8, 9]
const STORY_TURNS = [3, 4, 5, 6, 7, 8, 9, 10]
const STORY_DEFAULT = { on: true, age: 5, turns: 5 }

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

/** A child's word game: {on, level, lists, words, say, auto} (auto: the level moves with how the child does). */
function scrambleSetup(value) {
  const v = value && typeof value === "object" ? value : {}
  const lists = Array.isArray(v.lists) ? SCRAMBLE_LISTS.filter((l) => v.lists.includes(l)) : []
  const level = Math.round(Number(v.level))
  const words = Math.round(Number(v.words))
  return {
    on: v.on === undefined ? SCRAMBLE_DEFAULT.on : !!v.on,
    level: Number.isFinite(level) ? Math.min(SCRAMBLE_LEVELS, Math.max(1, level)) : SCRAMBLE_DEFAULT.level,
    lists: lists.length ? lists : SCRAMBLE_DEFAULT.lists,
    words: Number.isFinite(words) ? Math.min(10, Math.max(1, words)) : SCRAMBLE_DEFAULT.words,
    say: v.say === undefined ? SCRAMBLE_DEFAULT.say : !!v.say,
    auto: !!v.auto,
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

/** A child's story maker: {on, age, turns}. */
function storySetup(value) {
  const v = value && typeof value === "object" ? value : {}
  return {
    on: v.on === undefined ? STORY_DEFAULT.on : !!v.on,
    age: nearest(v.age, STORY_AGES, STORY_DEFAULT.age),
    turns: nearest(v.turns, STORY_TURNS, STORY_DEFAULT.turns),
  }
}

module.exports = { hangmanSetup, scrambleSetup, memorySetup, storySetup, SCRAMBLE_LEVELS }
