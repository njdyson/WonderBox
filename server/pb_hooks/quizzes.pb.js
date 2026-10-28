/// <reference path="../pb_data/types.d.ts" />

// Quizzes the parent writes in the phone app, and their pictures. The TV gets the quizzes
// in the library (GET /api/wonder/library), like everything else; every editing route here
// returns the whole library too.

// {id?, title, icon, children: [ids], shuffle, questions: [{text, picture, answers: [{text, picture}], correct}]}
routerAdd("POST", "/api/wonder/quizzes/save", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  const clean = quiz.cleanQuiz(body, user.id, lib.ownChildIds)
  let record
  if (body.id) {
    record = lib.own("quizzes", body.id, user.id)
  } else {
    record = new Record($app.findCollectionByNameOrId("quizzes"))
    record.set("user", user.id)
    record.set("sort", quiz.quizRecords(user.id).length)
  }
  for (const [key, value] of Object.entries(clean)) record.set(key, value)
  $app.save(record)
  quiz.cleanupImages(user.id)
  return e.json(200, { ...lib.library(user.id), saved: record.id })
})

routerAdd("POST", "/api/wonder/quizzes/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("quizzes", e.requestInfo().body.id, user.id))
  quiz.cleanupImages(user.id)
  return e.json(200, lib.library(user.id))
})

// {id, delta: -1 or 1}: the order of the quizzes on the TV.
routerAdd("POST", "/api/wonder/quizzes/move", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/quizzes_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body
  lib.move(quiz.quizRecords(user.id), lib.own("quizzes", body.id, user.id), Number(body.delta) < 0 ? -1 : 1)
  return e.json(200, lib.library(user.id))
})

// Multipart: "image" (already shrunk by the phone), "source" (optional, e.g. "fluent:Dog").
// Returns {id, path}. The same source is stored once and reused.
routerAdd("POST", "/api/wonder/quiz-images/add", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const photos = require(`${__hooks}/photos_lib.js`)
  const user = lib.requireUser(e)
  const source = String(e.requestInfo().body.source || "").slice(0, 200)
  if (source) {
    try {
      const existing = $app.findFirstRecordByFilter("quiz_images", "user = {:u} && source = {:s}", { u: user.id, s: source })
      return e.json(200, { id: existing.id, path: `/api/wonder/quiz-images/${existing.id}` })
    } catch (_) {}
  }
  const image = photos.uploaded(e, "image")
  if (!image) throw new BadRequestError("Choose a picture.")
  const record = new Record($app.findCollectionByNameOrId("quiz_images"))
  record.set("user", user.id)
  record.set("image", image)
  record.set("source", source)
  try {
    $app.save(record)
  } catch (err) {
    throw new BadRequestError("That picture couldn't be saved. Try a different one.")
  }
  return e.json(200, { id: record.id, path: `/api/wonder/quiz-images/${record.id}` })
})

// The picture itself, for the parent (token) or their TVs (X-Device-Key).
routerAdd("GET", "/api/wonder/quiz-images/{id}", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const user = lib.caller(e)
  const record = lib.own("quiz_images", e.request.pathValue("id"), user.id)
  const name = record.getString("image")
  // A quiz image never changes (a new picture gets a new id), so it can be cached for good.
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, record.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})
