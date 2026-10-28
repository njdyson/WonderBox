/// <reference path="../pb_data/types.d.ts" />

// Family photos: the phone app uploads them (already shrunk), the TV shows them as a
// slideshow. Each is for one or more children, like videos. The files are protected, so
// they're served here, only to the parent (phone app token) and their linked TVs
// (X-Device-Key), never by a public link.

routerAdd("GET", "/api/wonder/photos", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  return e.json(200, { photos: photos.photoList(lib.caller(e).id) })
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

// The picture itself: kind is "image" or "thumb" (falls back to the image if there's no thumb).
routerAdd("GET", "/api/wonder/photos/{id}/{kind}", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.caller(e)
  const photo = lib.own("photos", e.request.pathValue("id"), user.id)
  const kind = e.request.pathValue("kind")
  if (kind !== "image" && kind !== "thumb") throw new NotFoundError("That's not there any more.")
  const name = (kind === "thumb" && photo.getString("thumb")) || photo.getString("image")
  // A photo never changes once uploaded (a new one gets a new id), so it can be cached for good.
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, photo.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})
