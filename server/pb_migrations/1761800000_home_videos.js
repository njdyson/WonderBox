/// <reference path="../pb_data/types.d.ts" />

// Home videos: clips the parent uploads from their phone, shown in the TV's Photos alongside
// the photos. They're kept in the photos collection so they're for children, ordered and
// protected the same way. The server converts each one to a 720p H.264 MP4 (what a Fire
// Stick plays best); "image" and "thumb" are then stills from it, so a TV that doesn't know
// about videos yet shows the still. "status" is "" when ready, else "uploading", "converting"
// or "failed"; only ready ones go to the TVs. "size" is the MP4's bytes, for the phone app's
// total.
migrate((app) => {
  const photos = app.findCollectionByNameOrId("photos")
  // A video has no still until it's converted.
  photos.fields.getByName("image").required = false
  photos.fields.add(new FileField({ name: "video", maxSelect: 1, maxSize: 4 * 1024 * 1024 * 1024, mimeTypes: ["video/mp4"], protected: true }))
  photos.fields.add(new NumberField({ name: "duration" }))
  photos.fields.add(new NumberField({ name: "size" }))
  photos.fields.add(new TextField({ name: "status", max: 20 }))
  photos.fields.add(new TextField({ name: "problem", max: 500 }))
  photos.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  app.save(photos)
}, (app) => {
  const photos = app.findCollectionByNameOrId("photos")
  for (const name of ["video", "duration", "size", "status", "problem", "updated"]) photos.fields.removeByName(name)
  photos.fields.getByName("image").required = true
  app.save(photos)
})
