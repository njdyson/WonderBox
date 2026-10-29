/// <reference path="../pb_data/types.d.ts" />

// The photo quiz (photo_quiz_lib.js): look at a family photo, then answer questions about it
// from memory. Gemini writes the questions ahead of time; the TV fetches them here, and the
// phone app checks them, asks for new ones or leaves a photo out.

// ?child=: {photos: [{photo, questions: [{level, text, right, wrong}]}]}, the photos that child
// can be quizzed on (TV or parent). 503 when the server has no Gemini key.
routerAdd("GET", "/api/wonder/photo-quiz", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const quiz = require(`${__hooks}/photo_quiz_lib.js`)
  const user = lib.caller(e)
  const child = lib.own("children", e.request.url.query().get("child"), user.id)
  return e.json(200, { photos: quiz.forChild(user.id, child) })
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
