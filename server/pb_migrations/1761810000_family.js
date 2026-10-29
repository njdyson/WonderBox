/// <reference path="../pb_data/types.d.ts" />

// Grown-ups sharing one family. A user whose "family" is set is a second grown-up of that
// user's family: the API acts on the family owner's children, videos, photos and TVs for
// them (wonderbox_lib.js familyOf). They join with an invite link made in the phone app;
// only a hash of the code is stored, and it can be used once, within a week.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.add(new RelationField({ name: "family", collectionId: users.id, maxSelect: 1, cascadeDelete: false }))
  app.save(users)

  app.save(new Collection({
    type: "base",
    name: "invites",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "code_hash", type: "text", required: true },
      { name: "made_by", type: "text", max: 200 },
      { name: "expires", type: "date", required: true },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_invites_code ON invites (code_hash)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("invites"))
  const users = app.findCollectionByNameOrId("users")
  users.fields.removeByName("family")
  app.save(users)
})
