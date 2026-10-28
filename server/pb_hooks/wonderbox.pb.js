/// <reference path="../pb_data/types.d.ts" />

// Wonder Box API. The phone app authenticates with a PocketBase user token
// (Authorization header); linked TVs send their device key (X-Device-Key).
// Every editing route returns the whole library, so the phone app just redraws.

// The children and videos, for the TV or the phone app.
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

// {id, delta: -1 or 1}: the order of the faces on the TV.
routerAdd("POST", "/api/wonder/children/move", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  lib.move(lib.childRecords(user.id), lib.own("children", body.id, user.id), Number(body.delta) < 0 ? -1 : 1)
  return e.json(200, lib.library(user.id))
})

// --- Videos ------------------------------------------------------------------

// {url}: whether a video will play on the TV, and its title and thumbnail. Errors say why not.
routerAdd("POST", "/api/wonder/videos/check", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  return e.json(200, lib.checkVideo(e.requestInfo().body.url))
})

// {url, children: [ids]}: checks the video again, then adds it at the top.
routerAdd("POST", "/api/wonder/videos/add", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const video = lib.addVideo(user.id, body.url, body.children)
  return e.json(200, { ...lib.library(user.id), saved: video.id })
})

// {id, children: [ids]}: who a video is for. No one left removes it.
routerAdd("POST", "/api/wonder/videos/update", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const video = lib.own("videos", body.id, user.id)
  const kids = lib.ownChildIds(body.children, user.id)
  if (kids.length) {
    video.set("children", kids)
    $app.save(video)
  } else {
    $app.delete(video)
  }
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
  lib.moveVideo(user.id, body.id, Number(body.delta) < 0 ? -1 : 1, body.child ? String(body.child) : null)
  return e.json(200, lib.library(user.id))
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
  })))
})

routerAdd("POST", "/api/wonder/devices/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("devices", e.requestInfo().body.id, user.id))
  return e.json(200, { ok: true })
})
