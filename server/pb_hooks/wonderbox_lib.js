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
    })),
    videos: videoRecords(userId).map((v) => ({
      id: v.id,
      youtubeId: v.getString("youtube_id"),
      title: v.getString("title"),
      channel: v.getString("channel"),
      thumbnail: v.getString("thumbnail"),
      children: v.getStringSlice("children"),
    })),
  }
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

/** Removes a child. Videos that were only theirs go too; shared ones stay for the others. */
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
  $app.delete(child)
}

/** Moves a video within one child's list (what the phone shows), or the whole list without [childId]. */
function moveVideo(userId, id, delta, childId) {
  const video = own("videos", id, userId)
  const all = videoRecords(userId)
  all.forEach((r, i) => r.set("sort", i))
  const view = childId ? all.filter((r) => r.getStringSlice("children").includes(childId)) : all
  const index = view.findIndex((r) => r.id === video.id)
  const other = view[index + delta]
  if (index >= 0 && other) {
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
function addVideo(userId, text, childIds) {
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
  $app.save(record)
  return record
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
  caller, requireUser, library, own, ownChildIds, childRecords, move, saveChild, setPicture, removeChild,
  moveVideo, parseYouTubeId, checkVideo, addVideo, newPairingCode, findPairing, cleanupPairings,
  PAIRING_TTL_MS,
}
