/// <reference path="../pb_data/types.d.ts" />

// Pictures for the TV's menu tiles (the home page categories and the Games and Quizzes
// tiles), drawn once by Gemini for each family and kept (menu_lib.js). Served only through
// /api/wonder/menu-pictures/{id} to the parent and their linked TVs.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  app.save(new Collection({
    type: "base",
    name: "menu_pictures",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      // "home.games", "game.jigsaw": one of menu_lib.js's SUBJECTS.
      { name: "key", type: "text", max: 40, required: true },
      { name: "picture", type: "file", maxSelect: 1, maxSize: 8 * 1024 * 1024, mimeTypes: ["image/jpeg", "image/png", "image/webp"], protected: true },
      // "" until drawn, "drawing", "done" or "failed".
      { name: "state", type: "text", max: 20 },
      { name: "updated", type: "autodate", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_menu_pictures_key ON menu_pictures (user, key)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("menu_pictures"))
})
