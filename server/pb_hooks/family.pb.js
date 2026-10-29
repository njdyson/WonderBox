/// <reference path="../pb_data/types.d.ts" />

// Grown-ups sharing a family (family_lib.js): invite links made in the phone app's Family tab.
// There is still no public sign-up; only someone with an invite link can make an account.

// Who's in the family, and invites waiting.
routerAdd("GET", "/api/wonder/family", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  return e.json(200, require(`${__hooks}/family_lib.js`).familyInfo(e.auth))
})

// A new invite link's code. It works once, for a week.
routerAdd("POST", "/api/wonder/family/invite", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  const family = require(`${__hooks}/family_lib.js`)
  return e.json(200, { ...family.invite(e.auth), ...family.familyInfo(e.auth) })
})

routerAdd("POST", "/api/wonder/family/invites/cancel", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  const family = require(`${__hooks}/family_lib.js`)
  family.cancelInvite(e.auth, String(e.requestInfo().body.id || ""))
  return e.json(200, family.familyInfo(e.auth))
})

// {id}: removes a grown-up (their account is deleted). Removing yourself leaves the family.
routerAdd("POST", "/api/wonder/family/remove", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  lib.requireUser(e)
  const family = require(`${__hooks}/family_lib.js`)
  const id = String(e.requestInfo().body.id || "")
  family.remove(e.auth, id)
  return e.json(200, id === e.auth.id ? { left: true } : family.familyInfo(e.auth))
})

// Not signed in: who sent the invite, and the children it's for.
routerAdd("GET", "/api/wonder/join", (e) => {
  return e.json(200, require(`${__hooks}/family_lib.js`).inviteInfo(e.request.url.query().get("code")))
})

// {code, email, password}: makes the account and signs it in, like auth-with-password.
routerAdd("POST", "/api/wonder/join", (e) => {
  const body = e.requestInfo().body
  const user = require(`${__hooks}/family_lib.js`).join(body.code, body.email, body.password)
  return $apis.recordAuthResponse(e, user, "password", null)
})
