/// <reference path="../pb_data/types.d.ts" />

// Shared helpers for wonderbox.pb.js. PocketBase runs each route handler in its own
// context, so handlers load this with require() rather than sharing globals.

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const PAIRING_TTL_MS = 10 * 60 * 1000

/** The signed-in parent (phone app token) or the parent a linked TV belongs to (X-Device-Key). */
function caller(e) {
  if (e.auth && e.auth.collection().name === "users") return e.auth
  const key = e.request.header.get("X-Device-Key")
  if (key) {
    let device = null
    try {
      device = $app.findFirstRecordByFilter("devices", "key_hash = {:h}", { h: $security.sha256(key) })
    } catch (_) {}
    if (device) {
      // Record when a TV was last seen, at most once an hour.
      const last = device.getDateTime("last_seen")
      if (last.isZero() || Date.now() - last.unix() * 1000 > 3600 * 1000) {
        device.set("last_seen", new Date().toISOString())
        $app.save(device)
      }
      return $app.findRecordById("users", device.getString("user"))
    }
  }
  throw new UnauthorizedError("Sign in, or link this TV again.")
}

function requireUser(e) {
  if (e.auth && e.auth.collection().name === "users") return e.auth
  throw new UnauthorizedError("Sign in first.")
}

// --- Library: children and videos ------------------------------------------

// Copied into plain arrays: the Go slices PocketBase returns don't support splice().
function childRecords(userId) {
  return toArray($app.findRecordsByFilter("children", "user = {:u}", "sort,created", 500, 0, { u: userId }))
}

function videoRecords(userId) {
  return toArray($app.findRecordsByFilter("videos", "user = {:u}", "sort,created", 5000, 0, { u: userId }))
}

function channelRecords(userId) {
  return toArray($app.findRecordsByFilter("channels", "user = {:u}", "sort,created", 500, 0, { u: userId }))
}

function toArray(records) {
  const list = []
  for (let i = 0; i < records.length; i++) list.push(records[i])
  return list
}

/** Everything the TV and the phone app show, in display order. */
function library(userId) {
  return {
    children: childRecords(userId).map((c) => ({
      id: c.id,
      name: c.getString("name"),
      avatar: c.getString("avatar"),
      color: c.getString("color"),
      picture: pictureUrl(c),
      games: games(c),
    })),
    quizzes: require(`${__hooks}/quizzes_lib.js`).quizList(userId),
    // The server can read things aloud for the TV (speech.pb.js).
    voice: require(`${__hooks}/speech_lib.js`).available(),
    videos: videoRecords(userId).map((v) => ({
      id: v.id,
      youtubeId: v.getString("youtube_id"),
      title: v.getString("title"),
      channel: v.getString("channel"),
      thumbnail: v.getString("thumbnail"),
      children: v.getStringSlice("children"),
      tier: tier(v.getString("tier")),
      section: section(v.getString("section")),
    })),
    channels: channelRecords(userId).map((c) => ({
      id: c.id,
      channelId: c.getString("channel_id"),
      kind: c.getString("kind") === "playlist" ? "playlist" : "channel",
      title: c.getString("title"),
      thumbnail: c.getString("thumbnail"),
      children: c.getStringSlice("children"),
      tier: tier(c.getString("tier")),
      section: section(c.getString("section")),
      videos: channelVideos(c),
      refreshed: c.getString("refreshed"),
    })),
  }
}

/** "treat" or "free": anything else (including empty, for videos added before tiers) is free. */
function tier(value) {
  return value === "treat" ? "treat" : "free"
}

/** Where it goes on the TV: "music", "exercise" (kids' yoga, workouts) or "videos". */
const SECTIONS = ["videos", "music", "exercise"]
function section(value) {
  return SECTIONS.includes(value) ? value : "videos"
}

const GAME_DEFAULTS = { maths: 1, spelling: 1, roundSize: 10, jigsaw: 3 }
// Photo jigsaws are 2 x 2 up to 5 x 5 pieces; 0 turns them off (stored as 1, since 0 is "not set").
const JIGSAW_MIN = 2
const JIGSAW_MAX = 5

/** A child's game settings, with defaults for anything not set yet. */
function games(child) {
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const extra = require(`${__hooks}/games_lib.js`)
  const maths = child.getInt("maths_level") || GAME_DEFAULTS.maths
  const spelling = child.getInt("spelling_level") || GAME_DEFAULTS.spelling
  const stored = quiz.jsonField(child, "game_setup") || {}
  const clockLevel = Math.min(quiz.CLOCK_PRESETS.length, Math.max(1, Math.round(Number(stored.clockLevel)) || 1))
  const flagLevel = Math.min(quiz.FLAG_PRESETS.length, Math.max(1, Math.round(Number(stored.flagLevel)) || 1))
  return {
    maths,
    spelling,
    roundSize: child.getInt("round_size") || GAME_DEFAULTS.roundSize,
    // What each game asks; a child set up before these existed gets their level's.
    mathsSetup: quiz.mathsSetup(stored.maths, maths),
    spellingSetup: quiz.spellingSetup(stored.spelling, spelling),
    // Telling the time: its level and setup live in game_setup, like the games below.
    clock: clockLevel,
    clockSetup: quiz.clockSetup(stored.clock, clockLevel),
    flags: flagLevel,
    flagSetup: quiz.flagSetup(stored.flags, flagLevel),
    // Which games win a treat: maths, spelling, clock, flags, quizzes, hangman, scramble, memory, jigsaw, videos (learning and exercise).
    treatGames: quiz.treatGames(stored.treats, stored.treatsOffered),
    jigsaw: jigsawSize(child.getInt("jigsaw_size")),
    sounds: soundsMode(child.getString("sounds")),
    hangman: extra.hangmanSetup(stored.hangman),
    scramble: extra.scrambleSetup(stored.scramble),
    memory: extra.memorySetup(stored.memory),
  }
}

// The games' sound effects on the TV (app/.../ui/Sounds.kt).
const SOUNDS = ["off", "calm", "silly"]

function soundsMode(value) {
  return SOUNDS.includes(value) ? value : "silly"
}

function jigsawSize(stored) {
  if (!stored) return GAME_DEFAULTS.jigsaw
  return stored === 1 ? 0 : Math.min(JIGSAW_MAX, Math.max(JIGSAW_MIN, stored))
}

/**
 * {id, maths, spelling, clock, flags, roundSize, mathsSetup, spellingSetup, clockSetup, flagSetup, treatGames,
 * jigsaw, sounds, hangman, scramble, memory}: how hard a child's games are (jigsaw 0 is off),
 * which of them win a treat, and their sound effects. hangman, scramble (the word game) and
 * memory are whole setups (pb_hooks/games_lib.js).
 * A level on its own sets that level's setup; a setup on its own keeps the level, or moves
 * it to the level whose setup it matches. Anything left out stays as it was.
 */
function saveGames(userId, body) {
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const extra = require(`${__hooks}/games_lib.js`)
  const child = own("children", body.id, userId)
  const clamp = (value, min, max, fallback) => {
    const n = Math.round(Number(value))
    return value !== undefined && value !== null && Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
  }
  const current = games(child)
  // Keep whatever else is stored with the setups.
  const setup = Object.assign({}, quiz.jsonField(child, "game_setup") || {}, {
    maths: current.mathsSetup,
    spelling: current.spellingSetup,
    clock: current.clockSetup,
    clockLevel: current.clock,
    flags: current.flagSetup,
    flagLevel: current.flags,
    treats: current.treatGames,
    // The games there were to choose from, so ones added later can earn by default.
    treatsOffered: quiz.TREAT_GAMES,
  })
  setup.hangman = body.hangman ? extra.hangmanSetup(body.hangman) : current.hangman
  setup.scramble = body.scramble ? extra.scrambleSetup(body.scramble) : current.scramble
  setup.memory = body.memory ? extra.memorySetup(body.memory) : current.memory
  let maths = clamp(body.maths, 1, MATHS_LEVELS, current.maths)
  let spelling = clamp(body.spelling, 1, SPELLING_LEVELS, current.spelling)
  if (body.mathsSetup) {
    setup.maths = quiz.mathsSetup(body.mathsSetup, maths)
    maths = quiz.mathsLevelOf(setup.maths) || maths
  } else if (maths !== current.maths) {
    setup.maths = quiz.mathsPreset(maths)
  }
  if (body.spellingSetup) {
    setup.spelling = quiz.spellingSetup(body.spellingSetup, spelling)
    spelling = quiz.spellingLevelOf(setup.spelling) || spelling
  } else if (spelling !== current.spelling) {
    setup.spelling = quiz.spellingPreset(spelling, current.spellingSetup)
  }
  let clock = clamp(body.clock, 1, quiz.CLOCK_PRESETS.length, current.clock)
  if (body.clockSetup) {
    setup.clock = quiz.clockSetup(body.clockSetup, clock)
    clock = quiz.clockLevelOf(setup.clock) || clock
  } else if (clock !== current.clock) {
    setup.clock = quiz.clockPreset(clock)
  }
  setup.clockLevel = clock
  let flags = clamp(body.flags, 1, quiz.FLAG_PRESETS.length, current.flags)
  if (body.flagSetup) {
    setup.flags = quiz.flagSetup(body.flagSetup, flags)
    flags = quiz.flagLevelOf(setup.flags) || flags
  } else if (flags !== current.flags) {
    setup.flags = quiz.flagPreset(flags)
  }
  setup.flagLevel = flags
  if (Array.isArray(body.treatGames)) setup.treats = quiz.treatGames(body.treatGames, quiz.TREAT_GAMES)
  child.set("maths_level", maths)
  child.set("spelling_level", spelling)
  child.set("round_size", clamp(body.roundSize, 3, 30, current.roundSize))
  child.set("game_setup", setup)
  // Left out (an older phone app): left as it is.
  if (body.jigsaw !== undefined) {
    const jigsaw = Math.round(Number(body.jigsaw))
    if (jigsaw === 0) child.set("jigsaw_size", 1)
    else if (Number.isFinite(jigsaw)) child.set("jigsaw_size", Math.min(JIGSAW_MAX, Math.max(JIGSAW_MIN, jigsaw)))
  }
  if (SOUNDS.includes(body.sounds)) child.set("sounds", body.sounds)
  $app.save(child)
}

/**
 * A TV's `level.change` event: [game] ("maths", "spelling", "clock", "flags" or "scramble") moved
 * to [level] because the child found it easy or hard (app/.../games/AutoLevel.kt). Only saved
 * while the parent still has that game on auto. True if it changed anything.
 */
function autoLevel(userId, childId, game, level) {
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const extra = require(`${__hooks}/games_lib.js`)
  let child = null
  try { child = own("children", childId, userId) } catch (_) { return false }
  const current = games(child)
  const n = Math.round(Number(level))
  if (!Number.isFinite(n)) return false
  const setup = Object.assign({}, quiz.jsonField(child, "game_setup") || {})
  const fit = (max) => Math.min(max, Math.max(1, n))
  if (game === "maths" && current.mathsSetup.auto) {
    child.set("maths_level", fit(MATHS_LEVELS))
    setup.maths = quiz.mathsSetup(current.mathsSetup, fit(MATHS_LEVELS))
  } else if (game === "spelling" && current.spellingSetup.auto) {
    child.set("spelling_level", fit(SPELLING_LEVELS))
    setup.spelling = quiz.spellingSetup(current.spellingSetup, fit(SPELLING_LEVELS))
  } else if (game === "clock" && current.clockSetup.auto) {
    setup.clockLevel = fit(quiz.CLOCK_PRESETS.length)
    setup.clock = quiz.clockSetup(current.clockSetup, setup.clockLevel)
  } else if (game === "flags" && current.flagSetup.auto) {
    setup.flagLevel = fit(quiz.FLAG_PRESETS.length)
    setup.flags = quiz.flagSetup(current.flagSetup, setup.flagLevel)
  } else if (game === "scramble" && current.scramble.auto) {
    setup.scramble = extra.scrambleSetup(Object.assign({}, current.scramble, { level: fit(extra.SCRAMBLE_LEVELS) }))
  } else {
    return false
  }
  child.set("game_setup", setup)
  $app.save(child)
  return true
}

// The TV app has this many levels of each game (app/.../games).
const MATHS_LEVELS = 6
const SPELLING_LEVELS = 5

/** The stored video list of a channel, as plain objects. */
function channelVideos(channel) {
  // A JSON field comes back as raw bytes.
  let list = []
  try {
    list = JSON.parse(toString(channel.get("videos")) || "[]")
  } catch (_) {}
  return Array.isArray(list) ? list : []
}

/** A record of the user's, or a 404. */
function own(collection, id, userId) {
  let record = null
  try {
    record = $app.findRecordById(collection, String(id || ""))
  } catch (_) {}
  if (!record || record.getString("user") !== userId) throw new NotFoundError("That's not there any more.")
  return record
}

/** Only the user's own children, so a video can't be shown to someone else's child. */
function ownChildIds(ids, userId) {
  const mine = new Set(childRecords(userId).map((c) => c.id))
  return [...new Set((ids || []).map(String))].filter((id) => mine.has(id))
}

/**
 * Moves [record] one place up (delta -1) or down (+1) among [list], which is in display
 * order. Renumbers the list first so every item has its own sort value.
 */
function move(list, record, delta) {
  const index = list.findIndex((r) => r.id === record.id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= list.length) return
  const moved = list.splice(index, 1)[0]
  list.splice(target, 0, moved)
  list.forEach((r, i) => {
    if (r.getInt("sort") !== i) {
      r.set("sort", i)
      $app.save(r)
    }
  })
}

function saveChild(userId, body) {
  const name = String(body.name || "").trim().slice(0, 40)
  if (!name) throw new BadRequestError("Give them a name.")
  let record
  if (body.id) {
    record = own("children", body.id, userId)
  } else {
    record = new Record($app.findCollectionByNameOrId("children"))
    record.set("user", userId)
    record.set("sort", childRecords(userId).length)
  }
  record.set("name", name)
  record.set("avatar", String(body.avatar || "").slice(0, 16))
  record.set("color", /^#[0-9a-fA-F]{6}$/.test(body.color || "") ? body.color : "#FFD23F")
  $app.save(record)
  return record
}

/** Where a child's picture is served from (relative to the server), or "" if they have none. */
function pictureUrl(child) {
  const file = child.getString("picture")
  return file ? `/api/files/children/${child.id}/${file}` : ""
}

/** Sets or removes a child's picture (see the /children/picture route). */
function setPicture(userId, e) {
  const body = e.requestInfo().body
  const record = own("children", body.id, userId)
  if (body.remove) {
    record.set("picture", null)
  } else {
    let files = []
    try {
      files = e.findUploadedFiles("picture")
    } catch (_) {}
    if (!files.length) throw new BadRequestError("Choose a picture.")
    record.set("picture", files[0])
  }
  $app.save(record)
}

/** Removes a child. Videos, channels and photos that were only theirs go too; shared ones stay for the others. */
function removeChild(userId, id) {
  const child = own("children", id, userId)
  for (const video of videoRecords(userId)) {
    const kids = video.getStringSlice("children")
    if (!kids.includes(child.id)) continue
    const rest = kids.filter((k) => k !== child.id)
    if (rest.length) {
      video.set("children", rest)
      $app.save(video)
    } else {
      $app.delete(video)
    }
  }
  const photos = toArray($app.findRecordsByFilter("photos", "user = {:u}", "", 0, 0, { u: userId }))
  for (const record of [...channelRecords(userId), ...photos]) {
    const kids = record.getStringSlice("children")
    if (!kids.includes(child.id)) continue
    const rest = kids.filter((k) => k !== child.id)
    if (rest.length) {
      record.set("children", rest)
      $app.save(record)
    } else {
      $app.delete(record)
    }
  }
  $app.delete(child)
}

/** Moves a video within one child's list (what the phone shows), or the whole list without [childId]. */
/**
 * Swaps a video with the one next to it in a child's list ([childId]) or the whole list. The phone
 * shows one kind at a time (videos, music, treats), so it can name the neighbour it sees as [otherId].
 */
function moveVideo(userId, id, delta, childId, otherId) {
  const video = own("videos", id, userId)
  const all = videoRecords(userId)
  all.forEach((r, i) => r.set("sort", i))
  const view = childId ? all.filter((r) => r.getStringSlice("children").includes(childId)) : all
  const index = view.findIndex((r) => r.id === video.id)
  const other = otherId ? view.find((r) => r.id === otherId) : view[index + delta]
  if (index >= 0 && other && other.id !== video.id) {
    const a = all.find((r) => r.id === video.id)
    const b = all.find((r) => r.id === other.id)
    const sa = a.getInt("sort")
    a.set("sort", b.getInt("sort"))
    b.set("sort", sa)
  }
  for (const r of all) $app.save(r)
}

// --- Checking YouTube videos ----------------------------------------------

/** The 11-character video ID from any usual YouTube link, or a bare ID. Null if there isn't one. */
function parseYouTubeId(text) {
  const s = String(text || "").trim()
  if (/^[A-Za-z0-9_-]{11}$/.test(s)) return s
  const patterns = [
    /(?:youtube\.com|youtube-nocookie\.com)\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/|v\/)([A-Za-z0-9_-]{11})/,
    /youtu\.be\/([A-Za-z0-9_-]{11})/,
  ]
  for (const p of patterns) {
    const m = s.match(p)
    if (m) return m[1]
  }
  return null
}

/**
 * Checks a video can play embedded on the TV, before it's added. Returns its details, or
 * throws an error saying why not. With YOUTUBE_API_KEY set (see deploy/install.sh), the Data
 * API also catches age-restricted and region-blocked videos; without it, oEmbed catches
 * private, deleted and not-embeddable ones.
 */
function checkVideo(text) {
  const id = parseYouTubeId(text)
  if (!id) throw new BadRequestError("That doesn't look like a YouTube link.")
  const thumbnail = `https://i.ytimg.com/vi/${id}/hqdefault.jpg`

  const key = $os.getenv("YOUTUBE_API_KEY")
  if (key) {
    const res = $http.send({
      url: `https://www.googleapis.com/youtube/v3/videos?part=snippet,status,contentDetails&id=${id}&key=${encodeURIComponent(key)}`,
      timeout: 15,
    })
    if (res.statusCode === 200 && res.json) {
      const item = (res.json.items || [])[0]
      if (!item) throw new BadRequestError("That video is private or has been deleted.")
      const status = item.status || {}
      const details = item.contentDetails || {}
      const snippet = item.snippet || {}
      if (status.privacyStatus === "private") throw new BadRequestError("That video is private.")
      if (status.embeddable === false) throw new BadRequestError("The owner doesn't let this video play outside YouTube, so it won't work on the TV.")
      if ((details.contentRating || {}).ytRating === "ytAgeRestricted") throw new BadRequestError("That video is age-restricted, so it can't play on the TV.")
      const region = details.regionRestriction || {}
      if ((region.blocked || []).includes("GB") || (region.allowed && !region.allowed.includes("GB"))) {
        throw new BadRequestError("That video isn't available in the UK.")
      }
      if (snippet.liveBroadcastContent === "upcoming") throw new BadRequestError("That's a live stream that hasn't started yet.")
      return {
        youtubeId: id,
        title: snippet.title || "",
        channel: snippet.channelTitle || "",
        thumbnail,
        madeForKids: !!status.madeForKids,
        checked: "full",
      }
    }
    // The API failing (quota, bad key) shouldn't stop videos being added: fall back to oEmbed.
  }

  const watch = encodeURIComponent(`https://www.youtube.com/watch?v=${id}`)
  const res = $http.send({ url: `https://www.youtube.com/oembed?format=json&url=${watch}`, timeout: 15 })
  if (res.statusCode === 401 || res.statusCode === 403) {
    throw new BadRequestError("The owner doesn't let this video play outside YouTube, so it won't work on the TV.")
  }
  if (res.statusCode === 404 || res.statusCode === 400) throw new BadRequestError("That video is private or has been deleted.")
  if (res.statusCode !== 200 || !res.json) throw new ApiError(502, "Couldn't reach YouTube to check that video. Try again in a minute.")
  return {
    youtubeId: id,
    title: res.json.title || "",
    channel: res.json.author_name || "",
    thumbnail,
    checked: "basic",
  }
}

/** Checks and adds a video for [childIds], or updates who it's for if it's already there. New ones go first. */
function addVideo(userId, text, childIds, tierValue, sectionValue) {
  const kids = ownChildIds(childIds, userId)
  if (!kids.length) throw new BadRequestError("Choose who it's for.")
  const info = checkVideo(text)
  let record = null
  try {
    record = $app.findFirstRecordByFilter("videos", "user = {:u} && youtube_id = {:y}", { u: userId, y: info.youtubeId })
  } catch (_) {}
  if (record) {
    record.set("children", [...new Set([...record.getStringSlice("children"), ...kids])])
  } else {
    record = new Record($app.findCollectionByNameOrId("videos"))
    record.set("user", userId)
    record.set("youtube_id", info.youtubeId)
    const first = videoRecords(userId)[0]
    record.set("sort", first ? first.getInt("sort") - 1 : 0)
    record.set("children", kids)
  }
  record.set("title", info.title)
  record.set("channel", info.channel)
  record.set("thumbnail", info.thumbnail)
  if (tierValue !== undefined) record.set("tier", tier(tierValue))
  if (sectionValue !== undefined) record.set("section", section(sectionValue))
  $app.save(record)
  return record
}

// --- Channels ----------------------------------------------------------------

const CHANNEL_VIDEO_LIMIT = 50
// YouTube's cookie for "rejected all" on the consent page, so a server in the UK or EU
// gets the channel page rather than the consent form.
const YOUTUBE_HEADERS = { "Cookie": "SOCS=CAI", "Accept-Language": "en-GB,en;q=0.8", "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36" }

/**
 * What a channel link points at: {id} for a /channel/UC… link or bare ID, otherwise {path}
 * (the part after youtube.com, such as "/@numberblocks" or "/c/name"). Null if it isn't one.
 */
function parseChannel(text) {
  const s = String(text || "").trim()
  if (/^UC[A-Za-z0-9_-]{22}$/.test(s)) return { id: s }
  if (/^@[\w.-]{2,}$/.test(s)) return { path: "/" + s }
  const m = s.match(/(?:^|\/\/|\.)youtube\.com(\/(?:channel\/(UC[A-Za-z0-9_-]{22})|@[^/?#]+|c\/[^/?#]+|user\/[^/?#]+))/)
  if (!m) return null
  return m[2] ? { id: m[2] } : { path: m[1] }
}

/** The playlist ID from a youtube.com/playlist link or a bare ID; null if it isn't one. Mixes (RD…) can't be listed. */
function parsePlaylist(text) {
  const s = String(text || "").trim()
  if (/^(PL|OL|UU|FL)[A-Za-z0-9_-]{10,62}$/.test(s)) return s
  const m = s.match(/(?:^|\/\/|\.)youtube\.com\/playlist\?(?:[^#]*&)?list=([A-Za-z0-9_-]{10,64})/)
  if (!m) return null
  if (m[1].startsWith("RD")) throw new BadRequestError("That's a YouTube mix, which changes for each viewer, so it can't be added. Add the songs, or a playlist, instead.")
  return m[1]
}

/** A playlist's name and picture: from the API with a key, from its feed without. */
function resolvePlaylist(id) {
  const key = $os.getenv("YOUTUBE_API_KEY")
  if (key) {
    const res = $http.send({ url: `https://www.googleapis.com/youtube/v3/playlists?part=snippet&id=${id}&key=${encodeURIComponent(key)}`, timeout: 15 })
    if (res.statusCode === 200 && res.json) {
      const item = (res.json.items || [])[0]
      if (!item) throw new BadRequestError("That playlist is private or has been deleted.")
      const snippet = item.snippet || {}
      const thumbs = snippet.thumbnails || {}
      return {
        channelId: id,
        kind: "playlist",
        title: snippet.title || "",
        owner: snippet.channelTitle || "",
        thumbnail: ((thumbs.high || thumbs.medium || thumbs.default) || {}).url || "",
      }
    }
  }
  const res = $http.send({ url: `https://www.youtube.com/feeds/videos.xml?playlist_id=${id}`, headers: YOUTUBE_HEADERS, timeout: 20 })
  if (res.statusCode === 404 || res.statusCode === 400) throw new BadRequestError("That playlist is private or has been deleted.")
  if (res.statusCode !== 200) throw new ApiError(502, "Couldn't reach YouTube to look up that playlist. Try again in a minute.")
  const xml = bodyText(res)
  const first = (xml.match(/<yt:videoId>([A-Za-z0-9_-]{11})<\/yt:videoId>/) || [])[1]
  return {
    channelId: id,
    kind: "playlist",
    title: decodeEntities((xml.match(/<title>([^<]*)<\/title>/) || [])[1]),
    owner: decodeEntities((xml.match(/<author>\s*<name>([^<]*)<\/name>/) || [])[1]),
    thumbnail: first ? `https://i.ytimg.com/vi/${first}/hqdefault.jpg` : "",
  }
}

/** The response body as text. */
function bodyText(res) {
  if (typeof res.raw === "string" && res.raw) return res.raw
  return res.body ? toString(res.body) : ""
}

function decodeEntities(s) {
  return String(s || "")
    .replace(/&quot;/g, "\"").replace(/&#39;|&#x27;|&apos;/g, "'")
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&amp;/g, "&")
}

/** Finds a channel's ID, name and picture from its page, so @handles and /c/ links work without an API key. */
function channelFromPage(path) {
  const res = $http.send({ url: `https://www.youtube.com${path}`, headers: YOUTUBE_HEADERS, timeout: 20 })
  if (res.statusCode === 404) throw new BadRequestError("Couldn't find that channel. Check the link.")
  if (res.statusCode !== 200) throw new ApiError(502, "Couldn't reach YouTube to look up that channel. Try again in a minute.")
  const html = bodyText(res)
  const id = (html.match(/<link rel="canonical" href="https:\/\/www\.youtube\.com\/channel\/(UC[A-Za-z0-9_-]{22})"/) ||
    html.match(/"externalId":"(UC[A-Za-z0-9_-]{22})"/) || [])[1]
  if (!id) throw new ApiError(502, "YouTube didn't say which channel that is. Try the link from the channel's About page.")
  const meta = (name) => decodeEntities((html.match(new RegExp(`<meta property="og:${name}" content="([^"]*)"`)) || [])[1] || "")
  return { channelId: id, title: meta("title"), thumbnail: meta("image") }
}

/** The channel's details from the YouTube Data API, or null if the API can't help. */
function channelFromApi(ref, key) {
  let query
  if (ref.id) query = `id=${ref.id}`
  else if (ref.path.startsWith("/@")) query = `forHandle=${encodeURIComponent(decodeURIComponent(ref.path.slice(1)))}`
  else if (ref.path.startsWith("/user/")) query = `forUsername=${encodeURIComponent(ref.path.slice(6))}`
  else return null
  const res = $http.send({ url: `https://www.googleapis.com/youtube/v3/channels?part=snippet&${query}&key=${encodeURIComponent(key)}`, timeout: 15 })
  if (res.statusCode !== 200 || !res.json) return null
  const item = (res.json.items || [])[0]
  if (!item) throw new BadRequestError("Couldn't find that channel. Check the link.")
  const thumbs = (item.snippet || {}).thumbnails || {}
  return {
    channelId: item.id,
    title: (item.snippet || {}).title || "",
    thumbnail: ((thumbs.high || thumbs.medium || thumbs.default) || {}).url || "",
  }
}

/** A channel's ID, name and picture from any channel link. */
function resolveChannel(text) {
  const ref = parseChannel(text)
  if (!ref) return null
  const key = $os.getenv("YOUTUBE_API_KEY")
  if (key) {
    const found = channelFromApi(ref, key)
    if (found) return found
  }
  return channelFromPage(ref.id ? `/channel/${ref.id}` : ref.path)
}

/**
 * A channel's latest videos (newest first, without Shorts) or a playlist's videos (in its
 * order). With YOUTUBE_API_KEY: up to 50, each checked to play embedded on the TV. Without:
 * the 15 in YouTube's feed, unchecked.
 */
function fetchSourceVideos(id, kind) {
  const playlist = kind === "playlist"
  const key = $os.getenv("YOUTUBE_API_KEY")
  if (key) {
    // UULF… is a channel's uploads without Shorts or live streams; UU… is every upload.
    const lists = playlist ? [id] : ["UULF" + id.slice(2), "UU" + id.slice(2)]
    const viaApi = playlistVideosFromApi(lists, key)
    if (viaApi) return { videos: viaApi, complete: true }
  }
  const res = $http.send({ url: `https://www.youtube.com/feeds/videos.xml?${playlist ? "playlist_id" : "channel_id"}=${id}`, headers: YOUTUBE_HEADERS, timeout: 20 })
  if (res.statusCode === 404) throw new BadRequestError(playlist ? "That playlist is private or empty." : "That channel has no public videos.")
  if (res.statusCode !== 200) throw new ApiError(502, "Couldn't reach YouTube to get those videos. Try again in a minute.")
  const videos = []
  for (const entry of bodyText(res).split("<entry>").slice(1)) {
    const videoId = (entry.match(/<yt:videoId>([A-Za-z0-9_-]{11})<\/yt:videoId>/) || [])[1]
    // A playlist is someone's choice, Shorts and all; a whole channel's Shorts are left out.
    if (!videoId || (!playlist && /href="https:\/\/www\.youtube\.com\/shorts\//.test(entry))) continue
    videos.push({ youtubeId: videoId, title: decodeEntities((entry.match(/<title>([^<]*)<\/title>/) || [])[1]), thumbnail: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg` })
  }
  return { videos, complete: false }
}

/** The videos of the first of [playlistIds] that exists. Null if the API fails (quota, bad key), so the feed can be used instead. */
function playlistVideosFromApi(playlistIds, key) {
  let items = null
  for (const playlistId of playlistIds) {
    const res = $http.send({
      url: `https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&maxResults=${CHANNEL_VIDEO_LIMIT}&playlistId=${playlistId}&key=${encodeURIComponent(key)}`,
      timeout: 15,
    })
    if (res.statusCode === 200 && res.json) {
      items = res.json.items || []
      break
    }
    if (res.statusCode !== 404) return null
  }
  if (!items) return []
  const ids = items.map((i) => (i.contentDetails || {}).videoId).filter(Boolean)
  if (!ids.length) return []
  const res = $http.send({
    url: `https://www.googleapis.com/youtube/v3/videos?part=snippet,status,contentDetails&id=${ids.join(",")}&key=${encodeURIComponent(key)}`,
    timeout: 15,
  })
  if (res.statusCode !== 200 || !res.json) return null
  const byId = {}
  for (const item of res.json.items || []) byId[item.id] = item
  const videos = []
  for (const id of ids) {
    const item = byId[id]
    if (!item) continue
    const status = item.status || {}
    const details = item.contentDetails || {}
    const region = details.regionRestriction || {}
    if (status.privacyStatus === "private" || status.embeddable === false) continue
    if ((details.contentRating || {}).ytRating === "ytAgeRestricted") continue
    if ((region.blocked || []).includes("GB") || (region.allowed && !region.allowed.includes("GB"))) continue
    if ((item.snippet || {}).liveBroadcastContent !== "none") continue
    videos.push({ youtubeId: id, title: (item.snippet || {}).title || "", thumbnail: `https://i.ytimg.com/vi/${id}/hqdefault.jpg` })
  }
  return videos
}

/**
 * Fetches a channel's latest videos into its record. Without an API key the feed only has the
 * latest 15, so videos seen before are kept (up to 50) and the list grows over time.
 */
function refreshChannel(record) {
  const fetched = fetchSourceVideos(record.getString("channel_id"), record.getString("kind"))
  let list = fetched.videos
  if (!fetched.complete) {
    const seen = new Set(list.map((v) => v.youtubeId))
    list = list.concat(channelVideos(record).filter((v) => !seen.has(v.youtubeId)))
  }
  record.set("videos", list.slice(0, CHANNEL_VIDEO_LIMIT))
  record.set("refreshed", new Date().toISOString())
  $app.save(record)
  return record
}

/** Every channel, every few hours (see the cron job in wonderbox.pb.js). One failing doesn't stop the rest. */
function refreshAllChannels() {
  const all = $app.findRecordsByFilter("channels", "id != ''", "refreshed", 2000, 0)
  for (const record of all) {
    try {
      refreshChannel(record)
    } catch (e) {
      console.log(`Wonder Box: couldn't refresh channel ${record.getString("channel_id")}: ${e}`)
    }
  }
}

// --- Searching YouTube ---------------------------------------------------------

const SEARCH_TYPES = ["video", "channel", "playlist"]
const SEARCH_CACHE_MS = 60 * 60 * 1000

/**
 * Searches YouTube for [type] ("video", "channel" or "playlist") with SafeSearch on strict.
 * Needs YOUTUBE_API_KEY; each search costs 100 of the key's 10,000 free daily units, so
 * answers are kept for an hour and repeating a search is free.
 */
function search(text, type) {
  const q = String(text || "").trim().slice(0, 100)
  if (!q) throw new BadRequestError("Type something to search for.")
  if (!SEARCH_TYPES.includes(type)) type = "video"
  const key = $os.getenv("YOUTUBE_API_KEY")
  if (!key) throw new BadRequestError("Searching needs a YouTube API key on the server. Paste a link instead.")

  const cacheKey = `wonderbox_search:${type}:${q.toLowerCase()}`
  const cached = $app.store().get(cacheKey)
  if (cached && Date.now() - cached.at < SEARCH_CACHE_MS) return cached.results

  const extra = type === "video" ? "&videoEmbeddable=true&videoSyndicated=true" : ""
  const res = $http.send({
    url: `https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=15&safeSearch=strict&regionCode=GB&relevanceLanguage=en&type=${type}${extra}&q=${encodeURIComponent(q)}&key=${encodeURIComponent(key)}`,
    timeout: 15,
  })
  if (res.statusCode === 403) throw new ApiError(503, "YouTube's search limit for today is used up. Paste a link instead, or try again tomorrow.")
  if (res.statusCode !== 200 || !res.json) throw new ApiError(502, "Couldn't reach YouTube to search. Try again in a minute.")
  const results = []
  for (const item of res.json.items || []) {
    const id = item.id || {}
    const snippet = item.snippet || {}
    const thumbs = snippet.thumbnails || {}
    const result = {
      kind: type,
      title: decodeEntities(snippet.title),
      channel: decodeEntities(snippet.channelTitle),
      thumbnail: ((thumbs.high || thumbs.medium || thumbs.default) || {}).url || "",
    }
    if (type === "video" && id.videoId) result.url = `https://www.youtube.com/watch?v=${id.videoId}`
    else if (type === "channel" && id.channelId) result.url = `https://www.youtube.com/channel/${id.channelId}`
    else if (type === "playlist" && id.playlistId) result.url = `https://www.youtube.com/playlist?list=${id.playlistId}`
    else continue
    if (type === "video" && snippet.liveBroadcastContent && snippet.liveBroadcastContent !== "none") continue
    results.push(result)
  }
  $app.store().set(cacheKey, { at: Date.now(), results })
  return results
}

/** A channel or playlist link's details, or null if it's neither. */
function resolveSource(text) {
  const playlist = parsePlaylist(text)
  if (playlist) return resolvePlaylist(playlist)
  const channel = resolveChannel(text)
  return channel && { ...channel, kind: "channel" }
}

/**
 * A video, channel or playlist link: its details, with kind "video", "channel" or "playlist".
 * A video link with a playlist attached (from a playlist's page) is just the video.
 * Errors say why it can't be added.
 */
function checkLink(text) {
  if (parseYouTubeId(text) || !(parsePlaylist(text) || parseChannel(text))) return { kind: "video", ...checkVideo(text) }
  const source = resolveSource(text)
  const { videos } = fetchSourceVideos(source.channelId, source.kind)
  if (!videos.length) throw new BadRequestError(`That ${source.kind} has no videos that will play on the TV.`)
  return { ...source, videos: videos.slice(0, 8), videoCount: videos.length }
}

/** Adds a channel or playlist for [childIds], or updates who it's for if it's already there. New ones go first. */
function addChannel(userId, text, childIds, tierValue, sectionValue) {
  const kids = ownChildIds(childIds, userId)
  if (!kids.length) throw new BadRequestError("Choose who it's for.")
  const info = resolveSource(text)
  if (!info) throw new BadRequestError("That doesn't look like a YouTube channel or playlist link.")
  let record = null
  try {
    record = $app.findFirstRecordByFilter("channels", "user = {:u} && channel_id = {:c}", { u: userId, c: info.channelId })
  } catch (_) {}
  if (record) {
    record.set("children", [...new Set([...record.getStringSlice("children"), ...kids])])
  } else {
    record = new Record($app.findCollectionByNameOrId("channels"))
    record.set("user", userId)
    record.set("channel_id", info.channelId)
    record.set("kind", info.kind === "playlist" ? "playlist" : "")
    const first = channelRecords(userId)[0]
    record.set("sort", first ? first.getInt("sort") - 1 : 0)
    record.set("children", kids)
  }
  record.set("title", info.title)
  record.set("thumbnail", info.thumbnail)
  if (tierValue !== undefined) record.set("tier", tier(tierValue))
  if (sectionValue !== undefined) record.set("section", section(sectionValue))
  refreshChannel(record)
  return record
}

/**
 * {id, children, tier, section}: who a video or channel is for, whether it's a treat, and
 * whether it's music. Any of them can be left out. No children left removes it.
 */
function updateItem(collection, userId, body) {
  const record = own(collection, body.id, userId)
  if (body.children !== undefined) {
    const kids = ownChildIds(body.children, userId)
    if (!kids.length) {
      $app.delete(record)
      return
    }
    record.set("children", kids)
  }
  if (body.tier !== undefined) record.set("tier", tier(body.tier))
  if (body.section !== undefined) record.set("section", section(body.section))
  $app.save(record)
}

// --- Linking a TV ----------------------------------------------------------

function newPairingCode() {
  return $security.randomStringWithAlphabet(6, CODE_ALPHABET)
}

/** A pending pairing by code, or null if missing or expired (expired ones are deleted). */
function findPairing(code) {
  let pairing = null
  try {
    pairing = $app.findFirstRecordByFilter("pairings", "code = {:c}", { c: String(code || "").toUpperCase().replace(/\s/g, "") })
  } catch (_) {
    return null
  }
  if (Date.now() - pairing.getDateTime("created").unix() * 1000 > PAIRING_TTL_MS) {
    $app.delete(pairing)
    return null
  }
  return pairing
}

function cleanupPairings() {
  const cutoff = new Date(Date.now() - PAIRING_TTL_MS).toISOString().replace("T", " ")
  const old = $app.findRecordsByFilter("pairings", "created < {:t}", "", 100, 0, { t: cutoff })
  for (const r of old) $app.delete(r)
}

module.exports = {
  caller, requireUser, library, own, ownChildIds, childRecords, channelRecords, move, saveChild, setPicture, removeChild,
  saveGames, autoLevel, moveVideo, parseYouTubeId, checkVideo, addVideo, parseChannel, parsePlaylist, checkLink, addChannel,
  refreshChannel, refreshAllChannels, search, updateItem, newPairingCode, findPairing, cleanupPairings,
  PAIRING_TTL_MS,
}
