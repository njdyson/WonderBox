/// <reference path="../pb_data/types.d.ts" />

// Richer game settings per child, and quizzes the parent writes.
// - children.game_setup: {maths, spelling} setups (see pb_hooks/quizzes_lib.js).
// - quizzes: a quiz's questions, each with 2 to 4 answers in words and/or pictures.
// - quiz_images: pictures for quizzes, uploaded or copied from the clipart. Protected,
//   served only through /api/wonder/quiz-images/{id} to the parent and their TVs.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")
  children.fields.add(new JSONField({ name: "game_setup", maxSize: 100000 }))
  app.save(children)

  app.save(new Collection({
    type: "base",
    name: "quizzes",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "title", type: "text", max: 60, required: true },
      { name: "icon", type: "text", max: 100 },
      { name: "children", type: "relation", collectionId: children.id, maxSelect: 50 },
      { name: "shuffle", type: "bool" },
      // [{text, picture, answers: [{text, picture}], correct}]
      { name: "questions", type: "json", maxSize: 500000 },
      { name: "sort", type: "number" },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE INDEX idx_quizzes_user ON quizzes (user, sort)"],
  }))

  app.save(new Collection({
    type: "base",
    name: "quiz_images",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "image", type: "file", maxSelect: 1, maxSize: 2 * 1024 * 1024, mimeTypes: ["image/jpeg", "image/png", "image/webp"], protected: true, required: true },
      // Where it came from, e.g. "fluent:Dog", so the same clipart is only stored once.
      { name: "source", type: "text", max: 200 },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: ["CREATE INDEX idx_quiz_images_user ON quiz_images (user, source)"],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("quiz_images"))
  app.delete(app.findCollectionByNameOrId("quizzes"))
  const children = app.findCollectionByNameOrId("children")
  children.fields.removeByName("game_setup")
  app.save(children)
})
