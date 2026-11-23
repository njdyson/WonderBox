/// <reference path="../pb_data/types.d.ts" />

// The photo quiz (photo_quiz_lib.js): look at a family photo, then answer questions about it
// from memory. Gemini writes the questions ahead of time; the TV fetches them here, and the
// phone app checks them, asks for new ones or leaves a photo out.

// ?child=: {photos: [{photo, questions: [{level, text, right, wrong}]}], made: [{photo, picture,
// theme, questions}]}, the photos that child can be quizzed on and the pictures Gemini made
// them that they've played (TV or parent). 503 when the server has no Gemini key.
routerAdd("GET", "/api/wonder/photo-quiz", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.caller(e)
  const child = lib.own("children", e.request.url.query().get("child"), user.id)
  const made = quiz.makeOn(lib.games(child).photoQuiz) ? quiz.madeFor(child) : []
  return e.json(200, { photos: quiz.forChild(user.id, child), made })
})

// {child}: {picture: {photo, picture, theme, questions}}, a new made-up picture for the child to
// play, from the TV's "Make a new picture" card. Usually ready at once; otherwise drawn now, which
// takes about half a minute.
routerAdd("POST", "/api/wonder/photo-quiz/make", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.caller(e)
  const child = lib.own("children", (e.requestInfo().body || {}).child, user.id)
  return e.json(200, { picture: quiz.makeFor(user.id, child) })
})

// A made-up picture, for the parent or a linked TV.
routerAdd("GET", "/api/wonder/photo-quiz/pictures/{id}", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const record = lib.own("quiz_pictures", e.request.pathValue("id"), lib.caller(e).id)
  const name = record.getString("image")
  if (!name) throw new NotFoundError("That picture isn't drawn yet.")
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, record.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})

// {pictures: [{id, child, photo, picture, theme, questions, created}]}: the made-up pictures the
// children have played, newest first, for the phone app to look over.
routerAdd("GET", "/api/wonder/photo-quiz/pictures", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.requireUser(e)
  return e.json(200, { pictures: quiz.picturesFor(user.id) })
})

// {id}: the parent removes a made-up picture. Returns the list.
routerAdd("POST", "/api/wonder/photo-quiz/pictures/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("quiz_pictures", (e.requestInfo().body || {}).id, user.id))
  return e.json(200, { pictures: quiz.picturesFor(user.id) })
})

// {photos: [{photo, thumb, children, state, off, questions}], writing: the server has a Gemini key}: every
// photo of a child with the quiz on, for the phone app. state is "" (waiting), "done", "none"
// (Gemini found nothing to ask) or "failed".
routerAdd("GET", "/api/wonder/photo-quiz/list", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.requireUser(e)
  return e.json(200, { photos: quiz.listFor(user.id), writing: quiz.available() })
})

// {photo}: new questions for that photo, written now. Returns the list.
routerAdd("POST", "/api/wonder/photo-quiz/redo", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.requireUser(e)
  const photo = lib.own("photos", (e.requestInfo().body || {}).photo, user.id)
  return e.json(200, { photos: quiz.redo(user.id, photo.id), writing: true })
})

// {photo, off}: leave that photo out of the quiz, or put it back. Returns the list.
routerAdd("POST", "/api/wonder/photo-quiz/off", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body || {}
  const photo = lib.own("photos", body.photo, user.id)
  return e.json(200, { photos: quiz.setOff(user.id, photo.id, !!body.off), writing: quiz.available() })
})

// Writes questions for new photos, a couple at a time.
cronAdd("wonderbox_photo_quiz", "* * * * *", () => {
  require(`${__hooks}/photo_quiz_lib.js`).writePending(2)
})

// Keeps a made-up picture ready for each child who has them on, one drawn at a time.
cronAdd("wonderbox_photo_quiz_pictures", "* * * * *", () => {
  require(`${__hooks}/photo_quiz_lib.js`).stockPictures(1)
})
