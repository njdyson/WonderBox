/// <reference path="../pb_data/types.d.ts" />

// The activity log: one row per thing that happened on a TV (a video played, a quiz
// answered, a photo looked at...). See server/EVENTS.md for the event types. Rows are
// written by POST /api/wonder/events and read back as totals by GET /api/wonder/stats.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const children = app.findCollectionByNameOrId("children")
  const devices = app.findCollectionByNameOrId("devices")
  app.save(new Collection({
    type: "base",
    name: "events",
    fields: [
      { name: "user", type: "relation", collectionId: users.id, maxSelect: 1, required: true, cascadeDelete: true },
      // Empty for events that aren't about one child (e.g. the app starting).
      { name: "child", type: "relation", collectionId: children.id, maxSelect: 1, cascadeDelete: true },
      { name: "device", type: "relation", collectionId: devices.id, maxSelect: 1 },
      // Dotted and lower case: "video.play", "quiz.answer".
      { name: "type", type: "text", required: true, max: 64 },
      // What it was about: a YouTube ID, a game, a photo.
      { name: "subject", type: "text", max: 200 },
      // One number worth adding up: seconds watched, 1 or 0 for a right or wrong answer, a score.
      { name: "value", type: "number" },
      // Anything else, kept as sent.
      { name: "data", type: "json", maxSize: 8000 },
      // When it happened on the TV, which may be well before it reached the server.
      { name: "at", type: "date", required: true },
      { name: "created", type: "autodate", onCreate: true },
    ],
    indexes: [
      "CREATE INDEX idx_events_user_at ON events (user, at)",
      "CREATE INDEX idx_events_child_type ON events (child, type, at)",
    ],
  }))
}, (app) => {
  app.delete(app.findCollectionByNameOrId("events"))
})
