/// <reference path="../pb_data/types.d.ts" />

// The activity log: TVs send what happened (videos played, quizzes answered...), the phone
// app reads it back as stats. The event types are listed in server/EVENTS.md.

// {events: [{id, type, child, subject, value, data, at}, ...]} from a TV (or the phone app).
routerAdd("POST", "/api/wonder/events", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const events = require(`${__hooks}/wonderbox_events.js`)
  const user = lib.caller(e)
  return e.json(200, events.saveEvents(user.id, events.callerDevice(e), e.requestInfo().body))
})

// The raw events, newest first: ?child=&type=&since=&limit=
routerAdd("GET", "/api/wonder/events", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const events = require(`${__hooks}/wonderbox_events.js`)
  const user = lib.requireUser(e)
  const q = e.request.url.query()
  return e.json(200, events.listEvents(user.id, {
    child: q.get("child"), type: q.get("type"), since: q.get("since"), limit: q.get("limit"),
  }))
})

// Totals per child for the phone app's stats: ?days=7&tz=<Date.getTimezoneOffset()>
routerAdd("GET", "/api/wonder/stats", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const events = require(`${__hooks}/wonderbox_events.js`)
  const user = lib.requireUser(e)
  const q = e.request.url.query()
  return e.json(200, events.stats(user.id, q.get("days"), q.get("tz")))
})
