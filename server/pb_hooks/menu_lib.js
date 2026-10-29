/// <reference path="../pb_data/types.d.ts" />

// Pictures for the TV's menu tiles, drawn by Gemini (gemini_lib.js), loaded with require().
// Each family gets one picture for each of SUBJECTS, all in the same style, drawn once by a
// cron job (menu.pb.js) and kept in the menu_pictures collection. The parent can have any
// of them drawn again from the phone app. The TV shows its own emoji until a picture is there.

// What each tile's picture shows, and the colours of the tile it sits on (ui/HomeScreen.kt),
// so the picture's background matches. "home.*" are the home page's categories, drawn wide
// (4:3) to fill the tile; "game.*" are the Games and Quizzes tiles, drawn square. [letters]
// lets that picture have a few letters or numbers, where the subject needs them.
const SUBJECTS = {
  "home.quizzes": { title: "Quizzes", colors: "bright blue (#4D96FF to #2B5FD9)", subject: "a big, friendly, smiling question mark character next to a glowing light bulb, with little sparkles" },
  "home.games": { title: "Games", colors: "purple (#A259FF to #6A2BD9)", subject: "a chunky, colourful toy game controller with a jigsaw piece, a die and a balloon bouncing around it" },
  "home.learning": { title: "Learning", colors: "coral pink (#FF5C7A to #D12E55)", subject: "a small stack of colourful picture books with a cute owl wearing round glasses sitting on top" },
  "home.music": { title: "Music", colors: "teal (#2EC4B6 to #13867C)", subject: "a cheerful toy drum and a little ukulele with colourful musical notes bouncing out of them" },
  "home.exercise": { title: "Exercise", colors: "orange (#FF9F43 to #E0612B)", subject: "a happy cartoon monkey doing a star jump, with a skipping rope and a bouncy ball" },
  "home.stories": { title: "Stories", colors: "indigo (#7C6CF2 to #4B3BC4)", subject: "an open storybook with a little castle, a crescent moon and stars popping up out of its pages" },
  "home.photos": { title: "Photos", colors: "leaf green (#8BC34A to #4F8A1B)", subject: "a cute chunky toy camera with a few blank instant photos fanned out beside it" },
  "home.treats": { title: "Treats", colors: "golden yellow to orange (#FFC93C to #FF8A3D)", subject: "a big shining golden star on top of an open treasure chest full of wrapped presents and sweets" },
  "game.maths": { title: "Maths", colors: "bright blue (#4D96FF to #2B5FD9)", subject: "chunky colourful toy number blocks showing 1, 2 and 3 with a plus sign", letters: true },
  "game.spelling": { title: "Spelling", colors: "purple (#A259FF to #6A2BD9)", subject: "chunky wooden toy alphabet blocks showing A, B and C, with a fat crayon", letters: true },
  "game.clock": { title: "Clock", colors: "teal (#2EC4B6 to #13867C)", subject: "a smiling cartoon alarm clock with two bells on top" },
  "game.flags": { title: "Flags", colors: "green (#3FB950 to #1E7A33)", subject: "a smiling cartoon globe with a string of plain, brightly coloured bunting flags around it (no real countries' flags)" },
  "game.hangman": { title: "Hangman", colors: "coral pink (#FF5C7A to #D12E55)", subject: "a bunch of shiny, brightly coloured balloons tied together with curly ribbons" },
  "game.scramble": { title: "Word jumble", colors: "green (#3FB950 to #1E7A33)", subject: "a few chunky letter tiles tumbling out of a little cloth bag, showing the letters C, A and T", letters: true },
  "game.jigsaw": { title: "Jigsaw", colors: "orange (#FF8A3D to #D9541B)", subject: "four chunky, colourful jigsaw puzzle pieces snapping together" },
  "game.memory": { title: "Pairs", colors: "bright blue (#4D96FF to #2B5FD9)", subject: "two matching playing cards turned face up, both showing the same cute ladybird, with a few face-down cards behind" },
  "game.story": { title: "Stories", colors: "indigo (#7C6CF2 to #4B3BC4)", subject: "an open storybook with a friendly little dragon and swirling stars rising out of it" },
  "game.photoquiz": { title: "Photo quiz", colors: "teal (#2EC4B6 to #13867C)", subject: "a big friendly magnifying glass looking at a little instant photo of a sunny garden, with little sparkles" },
}

// Pictures drawn per family per day, counting redraws; more wait until the next day.
const PER_DAY = 40

function available() {
  return require(`${__hooks}/gemini_lib.js`).available()
}

/** What to ask Gemini for, for the tile [key]. */
function promptFor(key) {
  const s = SUBJECTS[key]
  const wide = key.startsWith("home.")
  return [
    `Draw a single, bold picture for a "${s.title}" button in a children's TV app for 4 to 6 year olds.`,
    `It shows ${s.subject}.`,
    "Style: a cute, glossy 3D cartoon like a chunky clay or plastic toy, rounded shapes, soft studio lighting, bright cheerful colours, friendly smiling faces where there are faces. The same style as the app's other buttons, so they look like a set.",
    `Background: a smooth, plain ${s.colors} gradient that fills the whole picture edge to edge. No borders, frames, rounded corners, white edges or shadows around the picture.`,
    wide
      ? "Landscape (4:3). Keep the subject large and in the upper two thirds; leave the bottom quarter as plain background, since the button's name is written over it."
      : "Square. Keep the subject large and centred, filling most of the picture.",
    s.letters ? "Only the letters or numbers named above, nothing else written." : "No words, letters or numbers anywhere in the picture.",
    "No people or children. Nothing scary.",
  ].join("\n")
}

/** Makes sure [userId] has a menu_pictures record for every subject (new ones wait to be drawn). */
function ensure(userId) {
  const have = new Set()
  const records = $app.findRecordsByFilter("menu_pictures", "user = {:u}", "", 200, 0, { u: userId })
  for (let i = 0; i < records.length; i++) have.add(records[i].getString("key"))
  const collection = $app.findCollectionByNameOrId("menu_pictures")
  for (const key of Object.keys(SUBJECTS)) {
    if (have.has(key)) continue
    const record = new Record(collection)
    record.set("user", userId)
    record.set("key", key)
    record.set("state", "")
    $app.save(record)
  }
}

/** The path the TV and the phone app load [record]'s picture from; it changes when it's redrawn. */
function pictureUrl(record) {
  const name = record.getString("picture")
  return name ? `/api/wonder/menu-pictures/${record.id}?v=${encodeURIComponent(name)}` : ""
}

/** {key: path} for the user's pictures that are drawn: the library's menuPictures. */
function pictureMap(userId) {
  const map = {}
  const records = $app.findRecordsByFilter("menu_pictures", "user = {:u} && picture != ''", "", 200, 0, { u: userId })
  for (let i = 0; i < records.length; i++) {
    if (SUBJECTS[records[i].getString("key")]) map[records[i].getString("key")] = pictureUrl(records[i])
  }
  return map
}

/** Every subject for the phone app, in SUBJECTS order: [{key, title, picture, state}]. */
function pictureList(userId) {
  const byKey = {}
  const records = $app.findRecordsByFilter("menu_pictures", "user = {:u}", "", 200, 0, { u: userId })
  for (let i = 0; i < records.length; i++) byKey[records[i].getString("key")] = records[i]
  return Object.keys(SUBJECTS).map((key) => {
    const r = byKey[key]
    return { key, title: SUBJECTS[key].title, home: key.startsWith("home."), picture: r ? pictureUrl(r) : "", state: r ? r.getString("state") : "" }
  })
}

/** Asks for [key]'s picture to be drawn again (the cron job does it); the old one stays until then. */
function redraw(userId, key) {
  if (!SUBJECTS[key]) throw new BadRequestError("There's no menu picture called that.")
  ensure(userId)
  const record = $app.findFirstRecordByFilter("menu_pictures", "user = {:u} && key = {:k}", { u: userId, k: key })
  record.set("state", "")
  $app.save(record)
}

/** Draws [record]'s picture with Gemini. Returns true when it worked. */
function draw(record) {
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const key = record.getString("key")
  const image = gemini.drawImage(promptFor(key), key.startsWith("home.") ? "4:3" : "1:1")
  if (!image) return false
  const ext = image.type.includes("jpeg") ? "jpg" : image.type.includes("webp") ? "webp" : "png"
  record.set("picture", $filesystem.fileFromBytes(image.bytes, `${key.replace(/\W/g, "-")}.${ext}`))
  return true
}

/** PocketBase's date format, [ms] ago. */
const ago = (ms) => new Date(Date.now() - ms).toISOString().replace("T", " ")

/**
 * Puts pictures back in the queue that failed over 6 hours ago (the key may have been fixed
 * since) or got stuck drawing (the server restarted part way).
 */
function retryStale() {
  const stale = $app.findRecordsByFilter("menu_pictures",
    "(state = 'failed' && updated < {:failed}) || (state = 'drawing' && updated < {:drawing})", "", 100, 0,
    { failed: ago(6 * 3600 * 1000), drawing: ago(15 * 60 * 1000) })
  for (let i = 0; i < stale.length; i++) {
    stale[i].set("state", "")
    $app.save(stale[i])
  }
}

/** The cron job: gives every family a record per subject, then draws up to [limit] waiting pictures. */
function drawPending(limit) {
  if (!available()) return
  const gemini = require(`${__hooks}/gemini_lib.js`)
  const users = $app.findAllRecords("users")
  for (let i = 0; i < users.length; i++) ensure(users[i].id)
  retryStale()
  const pending = $app.findRecordsByFilter("menu_pictures", "state = ''", "updated", limit, 0)
  for (let i = 0; i < pending.length; i++) {
    const record = pending[i]
    if (!SUBJECTS[record.getString("key")]) continue
    try {
      gemini.allow(record.getString("user"), "menu", PER_DAY)
    } catch (_) {
      continue // drawn tomorrow
    }
    record.set("state", "drawing")
    $app.save(record)
    let ok = false
    try {
      ok = draw(record)
    } catch (err) {
      console.log(`menu picture ${record.getString("key")}: ${err}`)
    }
    record.set("state", ok ? "done" : "failed")
    try {
      $app.save(record)
    } catch (err) {
      console.log(`menu picture: couldn't save it: ${err}`)
      record.set("picture", null)
      record.set("state", "failed")
      $app.save(record)
    }
  }
}

module.exports = { SUBJECTS, available, promptFor, ensure, pictureMap, pictureList, redraw, drawPending }
