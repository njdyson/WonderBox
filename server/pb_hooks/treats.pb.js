/// <reference path="../pb_data/types.d.ts" />

// Treats given from the phone app (treats_lib.js). The phone app gives and takes back; a
// linked TV fetches what's waiting and claims each one before adding it to the child's treats.

// The TV: {gifts: [{id, child, video, title, by, created}]}, oldest first. The phone app also
// gets {recent}: the last week's gifts that reached a TV, each with {claimed, tv}.
routerAdd("GET", "/api/wonder/treats/gifts", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const treats = require(`${__hooks}/treats_lib.js`)
  const user = lib.caller(e)
  if (e.auth) return e.json(200, treats.list(user.id))
  return e.json(200, { gifts: treats.waiting(user.id) })
})

// The phone app: {child, video, title}, video "" for a treat they pick on the TV. Returns the list.
routerAdd("POST", "/api/wonder/treats/give", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const treats = require(`${__hooks}/treats_lib.js`)
  const user = lib.requireUser(e)
  const body = e.requestInfo().body || {}
  treats.give(user.id, lib.own("children", body.child, user.id), body.video, body.title, e.auth.email())
  return e.json(200, treats.list(user.id))
})

// The phone app: {id}, a gift not on a TV yet. Returns the list.
routerAdd("POST", "/api/wonder/treats/cancel", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const treats = require(`${__hooks}/treats_lib.js`)
  const user = lib.requireUser(e)
  treats.cancel(user.id, (e.requestInfo().body || {}).id)
  return e.json(200, treats.list(user.id))
})

// A linked TV: {id} → {gift} when this TV gets it, {gift: null} when another TV already did.
routerAdd("POST", "/api/wonder/treats/claim", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const events = require(`${__hooks}/wonderbox_events.js`)
  const treats = require(`${__hooks}/treats_lib.js`)
  const user = lib.caller(e)
  const device = events.callerDevice(e)
  if (!device) throw new UnauthorizedError("Only a linked TV can take a treat.")
  return e.json(200, { gift: treats.claim(user.id, device, (e.requestInfo().body || {}).id) })
})

cronAdd("wonderbox_treat_gifts_cleanup", "17 3 * * *", () => {
  require(`${__hooks}/treats_lib.js`).cleanup()
})
