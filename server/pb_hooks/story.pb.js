/// <reference path="../pb_data/types.d.ts" />

// POST /api/wonder/story, from a linked TV (X-Device-Key) or the signed-in parent:
// {child, turn, story: [{word, text}], options: [word, word, word]} → {parts: [{word, text}]},
// the three possible next parts of the child's story, one for each picture (story_lib.js).
// A part with text "" didn't come out right: the TV tells that one itself. 503 when the
// server has no Gemini key.
routerAdd("POST", "/api/wonder/story", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const stories = require(`${__hooks}/story_lib.js`)
  const user = lib.caller(e)
  const body = e.requestInfo().body || {}
  const child = lib.own("children", body.child, user.id)
  const setup = lib.games(child).story
  const options = (Array.isArray(body.options) ? body.options : []).map(stories.cleanWord).filter(Boolean).slice(0, 4)
  if (!options.length) throw new BadRequestError("No pictures to write about.")
  const story = (Array.isArray(body.story) ? body.story : []).slice(0, stories.MAX_TURNS)
    .map((p) => ({ word: stories.cleanWord(p && p.word), text: stories.cleanPart(p && p.text) }))
    .filter((p) => p.text)
  const turns = Math.min(stories.MAX_TURNS, Math.max(story.length + 1, setup.turns))
  const turn = Math.min(turns, story.length + 1)
  return e.json(200, { parts: stories.write(user.id, child, setup, turn, turns, story, options) })
})

// --- The story library -------------------------------------------------------

// {child, parts: [{word, picture, text}]}: a story the child finished, kept in their library.
// Its picture is drawn within a minute or two (the cron job below). Returns {stories}.
routerAdd("POST", "/api/wonder/stories/save", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const stories = require(`${__hooks}/story_lib.js`)
  const user = lib.caller(e)
  const body = e.requestInfo().body || {}
  const record = stories.save(user.id, lib.own("children", body.child, user.id), body.parts)
  return e.json(200, { stories: stories.storyList(user.id), saved: record.id })
})

// {stories: [{id, child, title, parts, picture, created}]}, newest first.
routerAdd("GET", "/api/wonder/stories", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const stories = require(`${__hooks}/story_lib.js`)
  return e.json(200, { stories: stories.storyList(lib.caller(e).id) })
})

// {id}: the parent removes a story (phone app).
routerAdd("POST", "/api/wonder/stories/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const stories = require(`${__hooks}/story_lib.js`)
  const user = lib.requireUser(e)
  $app.delete(lib.own("stories", e.requestInfo().body.id, user.id))
  return e.json(200, { stories: stories.storyList(user.id) })
})

// A story's picture, for the parent or a linked TV.
routerAdd("GET", "/api/wonder/stories/{id}/picture", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const story = lib.own("stories", e.request.pathValue("id"), lib.caller(e).id)
  const name = story.getString("picture")
  if (!name) throw new NotFoundError("That story has no picture yet.")
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, story.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})

// Draws pictures for new stories, a couple at a time.
cronAdd("wonderbox_story_pictures", "* * * * *", () => {
  require(`${__hooks}/story_lib.js`).drawPending(2)
})
