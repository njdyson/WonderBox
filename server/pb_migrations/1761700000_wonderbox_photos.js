/// <reference path="../pb_data/types.d.ts" />

// Family photos for the TV's slideshow. The phone app shrinks each photo before uploading
// it (a TV-sized picture and a small thumbnail), so the originals never reach the server.
// The files are protected: they're only served through /api/wonder/photos/…, to the parent
// and their linked TVs.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const image = ["image/jpeg", "image/png", "image/webp"]
  app.save(new Collection({
    type: "base",
    name: "photos",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "image", type: "file", maxSelect: 1, maxSize: 5 * 1024 * 1024, mimeTypes: image, protected: true, required: true },
      { name: "thumb", type: "file", maxSelect: 1, maxSize: 1024 * 1024, mimeTypes: image, protected: true },
      { name: "width", type: "number" },
      { name: "height", type: "number" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE INDEX idx_photos_user ON photos (user, created)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("photos"))
})
