/// <reference path="../pb_data/types.d.ts" />

// Wonder Box schema. Everything is reached through the custom routes in
// pb_hooks/wonderbox.pb.js, so the collections have no public API rules.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  // Accounts are created by the admin in the dashboard; there is no public sign-up.
  users.createRule = null
  // Keep the phone app signed in for 90 days between uses.
  users.authToken.duration = 90 * 24 * 60 * 60
  app.save(users)

  // The children who watch on the TV, picked by avatar when the app opens.
  const children = new Collection({
    type: "base",
    name: "children",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "name", type: "text", max: 40, required: true },
      { name: "avatar", type: "text", max: 16 },
      { name: "color", type: "text", max: 9 },
      { name: "sort", type: "number" },
      { name: "created", type: "autodate", onCreate: true },
    ],
  })
  app.save(children)

  // Curated YouTube videos. Each one is shown to one or more children.
  app.save(new Collection({
    type: "base",
    name: "videos",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "youtube_id", type: "text", required: true, max: 11 },
      { name: "title", type: "text", max: 300 },
      { name: "channel", type: "text", max: 200 },
      { name: "thumbnail", type: "text", max: 300 },
      { name: "children", type: "relation", collectionId: children.id, maxSelect: 50 },
      { name: "sort", type: "number" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_videos_user_youtube ON videos (user, youtube_id)"],
  }))

  // TVs linked to a user. The TV holds the key; only its hash is stored.
  app.save(new Collection({
    type: "base",
    name: "devices",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "name", type: "text", max: 100 },
      { name: "key_hash", type: "text", required: true },
      { name: "last_seen", type: "date" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_devices_key ON devices (key_hash)"],
  }))

  // Short-lived link requests: the TV shows the code, the parent approves it in the phone app.
  app.save(new Collection({
    type: "base",
    name: "pairings",
    fields: [
      { name: "code", type: "text", required: true },
      { name: "secret_hash", type: "text", required: true },
      { name: "device_name", type: "text", max: 100 },
      { name: "status", type: "text" },
      { name: "device_key", type: "text" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_pairings_code ON pairings (code)"],
  }))
}, (app) => {
  for (const name of ["pairings", "devices", "videos", "children"]) {
    app.delete(app.findCollectionByNameOrId(name))
  }
})
