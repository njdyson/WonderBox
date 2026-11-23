/// <reference path="../pb_data/types.d.ts" />

// The photo quiz's made-up pictures (pb_hooks/photo_quiz_lib.js): Gemini draws a picture-book
// scene for a child, then looks at it and writes questions about it, like it does for a family
// photo. One record per picture. "state" is "" until drawn, then "drawing", "done" or
// "failed"; "questions" is [{level, text, right, wrong: [..]}] like photo_quizzes. A picture
// is kept ready ahead of time; "played" once the child has had it, after which it's in their
// row of pictures to play again.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")
  app.save(new Collection({
    type: "base",
    name: "quiz_pictures",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "child", type: "relation", collectionId: children.id, maxSelect: 1, required: true, cascadeDelete: true },
      { name: "theme", type: "text", max: 200 },
      { name: "image", type: "file", maxSelect: 1, maxSize: 15 * 1024 * 1024, protected: true },
      { name: "questions", type: "json", maxSize: 50000 },
      { name: "state", type: "text", max: 20 },
      { name: "played", type: "bool" },
      { name: "created", type: "autodate", onCreate: true },
      { name: "updated", type: "autodate", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE INDEX idx_quiz_pictures_child ON quiz_pictures (child, state, played)",
    ],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("quiz_pictures"))
})
