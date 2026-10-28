/// <reference path="../pb_data/types.d.ts" />

// Playlists, kept in "channels" alongside whole channels: kind "playlist" holds a playlist ID
// (longer than a channel ID) in channel_id. Empty kind is a channel.
migrate((app) => {
  const channels = app.findCollectionByNameOrId("channels")
  channels.fields.getByName("channel_id").max = 64
  channels.fields.add(new TextField({ name: "kind", max: 10 }))
  app.save(channels)
}, (app) => {
  const channels = app.findCollectionByNameOrId("channels")
  channels.fields.removeByName("kind")
  app.save(channels)
})
