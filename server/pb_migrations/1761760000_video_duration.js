/// <reference path="../pb_data/types.d.ts" />

// How long each video is, in seconds (0 not known yet), from the YouTube Data API: the TV
// prices treat and music videos by length. Filled in when a video is added, and for older
// ones by the durations job in wonderbox.pb.js.
migrate((app) => {
  const videos = app.findCollectionByNameOrId("videos")
  videos.fields.add(new NumberField({ name: "duration", onlyInt: true }))
  app.save(videos)
}, (app) => {
  const videos = app.findCollectionByNameOrId("videos")
  videos.fields.removeByName("duration")
  app.save(videos)
})
