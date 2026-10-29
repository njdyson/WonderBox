/// <reference path="../pb_data/types.d.ts" />

// The photo quiz (pb_hooks/photo_quiz_lib.js): questions Gemini writes about each family
// photo, written once, ahead of time, so the TV never waits. One record per photo, deleted
// with it. "questions" is [{level, text, right, wrong: [..]}]; "state" is "" until written,
// then "writing", "done", "none" (nothing to ask about) or "failed". "off" is a photo the
// parent left out of the quiz.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const photos = app.findCollectionByNameOrId("photos")
  app.save(new Collection({
    type: "base",
    name: "photo_quizzes",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "photo", type: "relation", collectionId: photos.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "questions", type: "json", maxSize: 50000 },
      { name: "state", type: "text", max: 20 },
      { name: "tries", type: "number" },
      { name: "off", type: "bool" },
      { name: "updated", type: "autodate", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE UNIQUE INDEX idx_photo_quizzes_photo ON photo_quizzes (photo)",
      "CREATE INDEX idx_photo_quizzes_state ON photo_quizzes (state, updated)",
    ],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("photo_quizzes"))
})
