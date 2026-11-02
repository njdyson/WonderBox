/// <reference path="../pb_data/types.d.ts" />

// Treats a grown-up gives from the phone app, to correct a lost treat or reward a job done.
// The treats themselves live on the TV (app ui/AppState.kt), so a gift waits in treat_gifts
// until a linked TV claims it: only one TV gets each gift, even with several linked.

// Old claimed gifts are kept a while for the phone app's "given lately", then tidied away.
const KEEP_CLAIMED_MS = 7 * 24 * 3600 * 1000
const MAX_WAITING = 50

// Copied into a plain array: the Go slices PocketBase returns aren't proper JS arrays.
function find(filter, sort, limit, params) {
  const records = $app.findRecordsByFilter("treat_gifts", filter, sort, limit, 0, params)
  const list = []
  for (let i = 0; i < records.length; i++) list.push(records[i])
  return list
}

/** Gifts not yet on a TV, oldest first, as the TV and the phone app see them. */
function waiting(userId) {
  return find("user = {:u} && claimed = ''", "created", 200, { u: userId }).map(shape)
}

/** The last week's gifts that reached a TV, newest first, for the phone app. */
function recent(userId) {
  const since = new Date(Date.now() - KEEP_CLAIMED_MS).toISOString().replace("T", " ")
  return find("user = {:u} && claimed != '' && claimed > {:s}", "-claimed", 20, { u: userId, s: since }).map((r) => {
    const gift = shape(r)
    let tv = ""
    try { tv = $app.findRecordById("devices", r.getString("device")).getString("name") } catch (_) {}
    return { ...gift, claimed: r.getString("claimed"), tv }
  })
}

function shape(r) {
  return {
    id: r.id,
    child: r.getString("child"),
    video: r.getString("video"),
    title: r.getString("title"),
    by: r.getString("given_by"),
    created: r.getString("created"),
  }
}

/** For the phone app: what's waiting and what arrived lately. */
function list(userId) {
  return { gifts: waiting(userId), recent: recent(userId) }
}

/**
 * Gives [child] a treat: [video] is the TV's id for one of their treat videos (a video's id, or
 * "<channel id>/<YouTube ID>" for a channel's), or "" for a treat they pick themselves.
 */
function give(userId, child, video, title, by) {
  const count = $app.countRecords("treat_gifts", $dbx.hashExp({ user: userId, claimed: "" }))
  if (count >= MAX_WAITING) throw new BadRequestError("Lots of treats are already waiting for the TV. Turn the TV on to deliver them first.")
  const record = new Record($app.findCollectionByNameOrId("treat_gifts"))
  record.set("user", userId)
  record.set("child", child.id)
  record.set("video", String(video || "").trim().slice(0, 200))
  record.set("title", String(title || "").trim().slice(0, 300))
  record.set("given_by", String(by || "").slice(0, 200))
  $app.save(record)
  return record
}

/** Takes back a gift that hasn't reached a TV yet. */
function cancel(userId, id) {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const record = lib.own("treat_gifts", id, userId)
  if (!record.getDateTime("claimed").isZero()) throw new BadRequestError("That treat is already on the TV.")
  $app.delete(record)
}

/**
 * A TV takes gift [id]; returns it, or null when another TV (or this one, on a retry) got it
 * first. Claiming and checking happen in one transaction so two TVs can't both get it.
 */
function claim(userId, device, id) {
  let gift = null
  $app.runInTransaction((tx) => {
    let record
    try {
      record = tx.findRecordById("treat_gifts", String(id || ""))
    } catch (_) {
      return
    }
    if (record.getString("user") !== userId || !record.getDateTime("claimed").isZero()) return
    record.set("device", device.id)
    record.set("claimed", new Date().toISOString())
    tx.save(record)
    gift = shape(record)
  })
  return gift
}

/** Tidies away gifts that reached a TV over a week ago. */
function cleanup() {
  const before = new Date(Date.now() - KEEP_CLAIMED_MS).toISOString().replace("T", " ")
  const old = find("claimed != '' && claimed < {:b}", "", 500, { b: before })
  for (const r of old) $app.delete(r)
}

module.exports = { waiting, list, give, cancel, claim, cleanup }
