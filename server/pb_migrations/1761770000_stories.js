/// <reference path="../pb_data/types.d.ts" />

// The story library: each story a child finishes in the TV's story maker, with a picture
// Gemini draws for it afterwards. The picture is protected, served only through
// /api/wonder/stories/{id}/picture to the parent and their linked TVs.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")
  app.save(new Collection({
    type: "base",
    name: "stories",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "child", type: "relation", collectionId: children.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "title", type: "text", max: 200 },
      // [{word, picture, text}]: the picture picked for each part and what it says.
      { name: "parts", type: "json", maxSize: 50000 },
      { name: "picture", type: "file", maxSelect: 1, maxSize: 8 * 1024 * 1024, mimeTypes: ["image/jpeg", "image/png", "image/webp"], protected: true },
      // "" until drawn, "drawing", "done" or "failed".
      { name: "picture_state", type: "text", max: 20 },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE INDEX idx_stories_user ON stories (user, child, created)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("stories"))
})
