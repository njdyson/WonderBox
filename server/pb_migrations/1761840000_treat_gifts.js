/// <reference path="../pb_data/types.d.ts" />

// Treats a grown-up gives from the phone app (treats_lib.js): a treat video put straight in a
// child's Treats, or (video "") a treat for them to pick. Treats are kept on the TV, so each
// gift waits here until one linked TV claims it (device, claimed) and adds it.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")
  const devices = app.findCollectionByNameOrId("devices")
  app.save(new Collection({
    type: "base",
    name: "treat_gifts",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "child", type: "relation", collectionId: children.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "video", type: "text", max: 200 },
      { name: "title", type: "text", max: 300 },
      { name: "given_by", type: "text", max: 200 },
      { name: "device", type: "relation", collectionId: devices.id, maxSelect: 1, cascadeDelete: false },
      { name: "claimed", type: "date" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE INDEX idx_treat_gifts_user ON treat_gifts (user, claimed)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("treat_gifts"))
})
