/// <reference path="../pb_data/types.d.ts" />

// Family photos and home videos: the phone app uploads them (photos already shrunk, videos
// converted here, see home_video_lib.js), the TV shows them as a slideshow. Each is for one or
// more children, like videos. The files are protected, so they're served here, only to the
// parent (phone app token) and their linked TVs (X-Device-Key), never by a public link.

routerAdd("GET", "/api/wonder/photos", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const user = lib.caller(e)
  const phone = !!(e.auth && e.auth.collection().name === "users")
  const body = { photos: photos.photoList(user.id, !phone) }
  if (phone) body.videoProblem = require(`${__hooks}/home_video_lib.js`).problem()
  return e.json(200, body)
})

// Multipart: "image" (the TV-sized picture), "thumb" (optional), "width", "height", and
// "children" (a JSON list of who it's for; left out, it's for everyone).
// Returns the list with the new photo first.
routerAdd("POST", "/api/wonder/photos/add", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const user = lib.requireUser(e)
  const image = photos.uploaded(e, "image")
  if (!image) throw new BadRequestError("Choose a photo.")
  const thumb = photos.uploaded(e, "thumb")
  const body = e.requestInfo().body
  const kids = photos.photoChildren(body.children, user.id)
  if (!kids.length) throw new BadRequestError("Choose who it's for.")
  const record = new Record($app.findCollectionByNameOrId("photos"))
  record.set("user", user.id)
  record.set("children", kids)
  record.set("image", image)
  if (thumb) record.set("thumb", thumb)
  record.set("width", Math.max(0, Math.round(Number(body.width) || 0)))
  record.set("height", Math.max(0, Math.round(Number(body.height) || 0)))
  try {
    $app.save(record)
  } catch (err) {
    // Too big or not a picture (the field limits in the migration).
    throw new BadRequestError("That photo couldn't be saved. Try a different one.")
  }
  return e.json(200, { photos: photos.photoList(user.id), saved: record.id })
})

// {id, children: [ids]}: who a photo is for. No one left removes it.
routerAdd("POST", "/api/wonder/photos/update", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const photo = lib.own("photos", body.id, user.id)
  const kids = lib.ownChildIds(body.children, user.id)
  if (kids.length) {
    photo.set("children", kids)
    $app.save(photo)
  } else {
    $app.delete(photo)
  }
  return e.json(200, { photos: photos.photoList(user.id) })
})

routerAdd("POST", "/api/wonder/photos/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("photos", e.requestInfo().body.id, user.id))
  return e.json(200, { photos: photos.photoList(user.id) })
})

// Home videos, sent in pieces. {children, size} starts one (children left out = everyone) and
// returns {id, piece}: the size of each piece in bytes.
routerAdd("POST", "/api/wonder/photos/video/start", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const videos = require(`${__hooks}/home_video_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const kids = photos.photoChildren(body.children, user.id)
  if (!kids.length) throw new BadRequestError("Choose who it's for.")
  const record = videos.start(user.id, kids, body.size)
  return e.json(200, { id: record.id, piece: videos.PIECE })
})

// Piece ?n= (from 0) of a video, as the raw body (application/octet-stream).
routerAdd("POST", "/api/wonder/photos/video/{id}/piece", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const videos = require(`${__hooks}/home_video_lib.js`)
  const user = lib.requireUser(e)
  videos.savePiece(e, lib.own("photos", e.request.pathValue("id"), user.id), e.request.url.query().get("n"))
  return e.json(200, { ok: true })
})

// {pieces}: everything's sent, so the server converts it. Returns the list (it shows as converting).
routerAdd("POST", "/api/wonder/photos/video/{id}/finish", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const videos = require(`${__hooks}/home_video_lib.js`)
  const user = lib.requireUser(e)
  videos.finish(lib.own("photos", e.request.pathValue("id"), user.id), e.requestInfo().body.pieces)
  return e.json(200, { photos: photos.photoList(user.id) })
})

// Every minute: pick up converted videos, restart any a server restart cut short.
cronAdd("wonderbox_home_videos", "* * * * *", () => {
  require(`${__hooks}/home_video_lib.js`).check()
})

onRecordAfterDeleteSuccess((e) => {
  require(`${__hooks}/home_video_lib.js`).forget(e.record.id)
  e.next()
}, "photos")

// The file itself: kind is "image", "thumb" (falls back to the image if there's no thumb) or
// "video" (a home video's MP4; it can be fetched in ranges, so the TV can skip about).
routerAdd("GET", "/api/wonder/photos/{id}/{kind}", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.caller(e)
  const photo = lib.own("photos", e.request.pathValue("id"), user.id)
  const kind = e.request.pathValue("kind")
  if (kind !== "image" && kind !== "thumb" && kind !== "video") throw new NotFoundError("That's not there any more.")
  const name = kind === "video" ? photo.getString("video") : (kind === "thumb" && photo.getString("thumb")) || photo.getString("image")
  if (!name) throw new NotFoundError("That's not ready yet.")
  // A file never changes once uploaded (a new one gets a new id), so it can be cached for good.
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, photo.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})
