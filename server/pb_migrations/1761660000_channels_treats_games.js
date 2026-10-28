/// <reference path="../pb_data/types.d.ts" />

// Whole YouTube channels, "treat" videos that a game unlocks, a Music section, and each
// child's game settings.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")

  // How hard each child's games are, and how many right answers earn a treat.
  children.fields.add(new NumberField({ name: "maths_level", min: 0, max: 20, onlyInt: true }))
  children.fields.add(new NumberField({ name: "spelling_level", min: 0, max: 20, onlyInt: true }))
  children.fields.add(new NumberField({ name: "round_size", min: 0, max: 50, onlyInt: true }))
  app.save(children)

  // "free" (always there) or "treat" (unlocked by finishing a game). Empty means free.
  const videos = app.findCollectionByNameOrId("videos")
  videos.fields.add(new TextField({ name: "tier", max: 10 }))
  // "music" puts it in the Music row on the TV; empty is an ordinary video.
  videos.fields.add(new TextField({ name: "section", max: 10 }))
  app.save(videos)

  // A YouTube channel: its latest videos are fetched when it's added and every few hours.
  app.save(new Collection({
    type: "base",
    name: "channels",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "channel_id", type: "text", required: true, max: 24 },
      { name: "title", type: "text", max: 200 },
      { name: "thumbnail", type: "text", max: 500 },
      { name: "children", type: "relation", collectionId: children.id, maxSelect: 50 },
      { name: "tier", type: "text", max: 10 },
      { name: "section", type: "text", max: 10 },
      // [{youtubeId, title, thumbnail}], newest first.
      { name: "videos", type: "json", maxSize: 200000 },
      { name: "refreshed", type: "date" },
      { name: "sort", type: "number" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_channels_user_channel ON channels (user, channel_id)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("channels"))
  const videos = app.findCollectionByNameOrId("videos")
  videos.fields.removeByName("tier")
  videos.fields.removeByName("section")
  app.save(videos)
  const children = app.findCollectionByNameOrId("children")
  for (const name of ["maths_level", "spelling_level", "round_size"]) children.fields.removeByName(name)
  app.save(children)
})
