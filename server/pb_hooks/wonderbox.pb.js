/// <reference path="../pb_data/types.d.ts" />

// Wonder Box API. The phone app authenticates with a PocketBase user token
// (Authorization header); linked TVs send their device key (X-Device-Key).
// Every editing route returns the whole library, so the phone app just redraws.

// The children, videos and channels, for the TV or the phone app.
routerAdd("GET", "/api/wonder/library", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  return e.json(200, lib.library(lib.caller(e).id))
})

// --- Children ----------------------------------------------------------------

// Add a child ({name, avatar, color}) or change one ({id, ...}).
routerAdd("POST", "/api/wonder/children/save", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const child = lib.saveChild(user.id, e.requestInfo().body)
  return e.json(200, { ...lib.library(user.id), saved: child.id })
})

routerAdd("POST", "/api/wonder/children/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.removeChild(user.id, e.requestInfo().body.id)
  return e.json(200, lib.library(user.id))
})

// A child's picture: multipart with "id" and a "picture" file (the phone app shrinks it
// first), or {id, remove: true} to go back to their avatar.
routerAdd("POST", "/api/wonder/children/picture", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.setPicture(user.id, e)
  return e.json(200, lib.library(user.id))
})

// {id, delta: -1 or 1}: the order of the faces on the TV.
routerAdd("POST", "/api/wonder/children/move", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  lib.move(lib.childRecords(user.id), lib.own("children", body.id, user.id), Number(body.delta) < 0 ? -1 : 1)
  return e.json(200, lib.library(user.id))
})

// {id, maths, spelling, roundSize, jigsaw, sounds, hangman, memory, ...}: a child's games on the TV.
routerAdd("POST", "/api/wonder/children/games", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.saveGames(user.id, e.requestInfo().body)
  return e.json(200, lib.library(user.id))
})

// --- Videos ------------------------------------------------------------------

// ?q=words&type=video|channel|playlist: YouTube search results, each with a url to check
// and add as usual. Needs the YouTube API key.
routerAdd("GET", "/api/wonder/search", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  const query = e.request.url.query()
  return e.json(200, { results: lib.search(query.get("q"), query.get("type")) })
})

// {url}: a video or channel link. For a video, whether it will play on the TV, and its title
// and thumbnail; for a channel, its name, picture and latest videos. Errors say why not.
// The answer's kind is "video" or "channel".
routerAdd("POST", "/api/wonder/videos/check", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  return e.json(200, lib.checkLink(e.requestInfo().body.url))
})

// {url, children: [ids], tier, section}: checks the video again, then adds it at the top.
routerAdd("POST", "/api/wonder/videos/add", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const video = lib.addVideo(user.id, body.url, body.children, body.tier, body.section)
  return e.json(200, { ...lib.library(user.id), saved: video.id })
})

// {id, children: [ids], tier: "free" or "treat", section: "videos" or "music"}: any of them.
// No children left removes it.
routerAdd("POST", "/api/wonder/videos/update", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.updateItem("videos", user.id, e.requestInfo().body)
  return e.json(200, lib.library(user.id))
})

routerAdd("POST", "/api/wonder/videos/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("videos", e.requestInfo().body.id, user.id))
  return e.json(200, lib.library(user.id))
})

// {id, delta: -1 or 1, child}: moves a video within that child's list.
routerAdd("POST", "/api/wonder/videos/move", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  lib.moveVideo(user.id, body.id, Number(body.delta) < 0 ? -1 : 1, body.child ? String(body.child) : null, body.other ? String(body.other) : null)
  return e.json(200, lib.library(user.id))
})

// --- Channels ----------------------------------------------------------------

// {url, children: [ids], tier, section}: adds a whole channel or a playlist at the top; its videos come with it.
routerAdd("POST", "/api/wonder/channels/add", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const channel = lib.addChannel(user.id, body.url, body.children, body.tier, body.section)
  return e.json(200, { ...lib.library(user.id), saved: channel.id })
})

// {id, children: [ids], tier, section}: as for videos.
routerAdd("POST", "/api/wonder/channels/update", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.updateItem("channels", user.id, e.requestInfo().body)
  return e.json(200, lib.library(user.id))
})

routerAdd("POST", "/api/wonder/channels/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("channels", e.requestInfo().body.id, user.id))
  return e.json(200, lib.library(user.id))
})

// {id}: fetches the channel's latest videos now, rather than waiting for the next refresh.
routerAdd("POST", "/api/wonder/channels/refresh", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  lib.refreshChannel(lib.own("channels", e.requestInfo().body.id, user.id))
  return e.json(200, lib.library(user.id))
})

// {id, delta: -1 or 1}: the order of the channels.
routerAdd("POST", "/api/wonder/channels/move", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  lib.move(lib.channelRecords(user.id), lib.own("channels", body.id, user.id), Number(body.delta) < 0 ? -1 : 1)
  return e.json(200, lib.library(user.id))
})

// New videos on every channel, four times a day.
cronAdd("wonderbox_channels", "17 */6 * * *", () => {
  require(`${__hooks}/wonderbox_lib.js`).refreshAllChannels()
})

// --- Linking a TV ------------------------------------------------------------

// Called by the TV: returns a code to show, and a secret only the TV knows for polling.
routerAdd("POST", "/api/wonder/pair/start", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.cleanupPairings()
  const secret = $security.randomString(32)
  const record = new Record($app.findCollectionByNameOrId("pairings"))
  let code = lib.newPairingCode()
  while (lib.findPairing(code)) code = lib.newPairingCode()
  record.set("code", code)
  record.set("secret_hash", $security.sha256(secret))
  record.set("device_name", String(e.requestInfo().body.name || "TV").slice(0, 100))
  record.set("status", "pending")
  $app.save(record)
  return e.json(200, { code, secret, expiresInSeconds: lib.PAIRING_TTL_MS / 1000 })
})

// Polled by the TV until the parent approves. Hands over the device key once.
routerAdd("GET", "/api/wonder/pair/poll", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const q = e.request.url.query()
  const pairing = lib.findPairing(q.get("code"))
  if (!pairing || pairing.getString("secret_hash") !== $security.sha256(q.get("secret") || "")) {
    return e.json(410, { status: "expired" })
  }
  if (pairing.getString("status") !== "approved") return e.json(200, { status: "pending" })
  const deviceKey = pairing.getString("device_key")
  $app.delete(pairing)
  return e.json(200, { status: "approved", deviceKey })
})

// Called by the phone app (signed in) to see which TV a code belongs to.
routerAdd("GET", "/api/wonder/pair/info", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  const pairing = lib.findPairing(e.request.url.query().get("code"))
  if (!pairing || pairing.getString("status") !== "pending") throw new NotFoundError("That code has expired. Show a new one on the TV.")
  return e.json(200, { name: pairing.getString("device_name") })
})

// Called by the phone app (signed in) to link the TV to this account.
routerAdd("POST", "/api/wonder/pair/approve", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const pairing = lib.findPairing(e.requestInfo().body.code)
  if (!pairing || pairing.getString("status") !== "pending") throw new NotFoundError("That code has expired. Show a new one on the TV.")
  const deviceKey = $security.randomString(40)
  const device = new Record($app.findCollectionByNameOrId("devices"))
  device.set("user", user.id)
  device.set("name", pairing.getString("device_name"))
  device.set("key_hash", $security.sha256(deviceKey))
  device.set("last_seen", new Date().toISOString())
  $app.save(device)
  pairing.set("status", "approved")
  pairing.set("device_key", deviceKey)
  $app.save(pairing)
  return e.json(200, { name: device.getString("name") })
})

// Called by a TV to unlink itself (grown-ups' settings on the TV).
routerAdd("POST", "/api/wonder/devices/forget", (e) => {
  const key = e.request.header.get("X-Device-Key")
  if (!key) throw new UnauthorizedError("No device key.")
  try {
    $app.delete($app.findFirstRecordByFilter("devices", "key_hash = {:h}", { h: $security.sha256(key) }))
  } catch (_) {}
  return e.json(200, { ok: true })
})

routerAdd("GET", "/api/wonder/devices", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const devices = $app.findRecordsByFilter("devices", "user = {:u}", "-created", 100, 0, { u: user.id })
  return e.json(200, devices.map((d) => ({
    id: d.id,
    name: d.getString("name"),
    lastSeen: d.getString("last_seen"),
    build: d.getInt("build"),
  })))
})

// The newest published build, so the phone app can say which TVs need updating.
routerAdd("GET", "/api/wonder/devices/latest", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  return e.json(200, { build: lib.latestBuild() })
})

routerAdd("POST", "/api/wonder/devices/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("devices", e.requestInfo().body.id, user.id))
  return e.json(200, { ok: true })
})
