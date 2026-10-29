/// <reference path="../pb_data/types.d.ts" />

// Grown-ups sharing a family (family.pb.js). The first account, made in the server's dashboard,
// owns the family; anyone it invites gets their own email and password, with "family" pointing
// at the owner, so wonderbox_lib.js familyOf() shows them the same children, videos and TVs.

const INVITE_DAYS = 7

const lib = () => require(`${__hooks}/wonderbox_lib.js`)

/** Everyone in the family: the owner first, then the others in the order they joined. */
function grownUps(owner) {
  const others = $app.findRecordsByFilter("users", "family = {:o}", "created", 50, 0, { o: owner.id })
  const list = [owner]
  for (let i = 0; i < others.length; i++) list.push(others[i])
  return list
}

function inviteRecords(owner) {
  return $app.findRecordsByFilter("invites", "user = {:o} && expires > {:now}", "-created", 50, 0,
    { o: owner.id, now: new Date().toISOString().replace("T", " ") })
}

/** What the Family tab shows: who's in it, and invites not yet used. */
function familyInfo(me) {
  const owner = lib().familyOf(me)
  return {
    you: me.id,
    owner: owner.id,
    grownUps: grownUps(owner).map((u) => ({ id: u.id, email: u.email(), name: u.getString("name"), owner: u.id === owner.id })),
    invites: inviteRecords(owner).map((i) => ({ id: i.id, by: i.getString("made_by"), expires: i.getString("expires"), created: i.getString("created") })),
  }
}

/** A new invite for [me]'s family. Returns the code, which is only stored hashed. */
function invite(me) {
  const owner = lib().familyOf(me)
  cleanup()
  if (inviteRecords(owner).length >= 10) throw new BadRequestError("There are already 10 invites waiting. Cancel some first.")
  const code = $security.randomStringWithAlphabet(24, "abcdefghijkmnpqrstuvwxyz23456789")
  const record = new Record($app.findCollectionByNameOrId("invites"))
  record.set("user", owner.id)
  record.set("code_hash", $security.sha256(code))
  record.set("made_by", me.email())
  record.set("expires", new Date(Date.now() + INVITE_DAYS * 86400 * 1000).toISOString())
  $app.save(record)
  return { code, days: INVITE_DAYS }
}

/** The invite for [code], if it's still good. */
function findInvite(code) {
  const c = String(code || "").trim().toLowerCase()
  if (!c) return null
  try {
    const record = $app.findFirstRecordByFilter("invites", "code_hash = {:h}", { h: $security.sha256(c) })
    if (new Date(record.getString("expires").replace(" ", "T")).getTime() < Date.now()) return null
    return record
  } catch (_) {
    return null
  }
}

/** What the person opening an invite link sees before they make their account. */
function inviteInfo(code) {
  const record = findInvite(code)
  if (!record) throw new NotFoundError("That invite has expired or been used. Ask for a new link.")
  const owner = $app.findRecordById("users", record.getString("user"))
  return {
    by: record.getString("made_by"),
    children: lib().childRecords(owner.id).map((c) => c.getString("name")),
  }
}

/** Makes the invited grown-up's account and uses up the invite. Returns the new user. */
function join(code, email, password) {
  const record = findInvite(code)
  if (!record) throw new NotFoundError("That invite has expired or been used. Ask for a new link.")
  const address = String(email || "").trim().toLowerCase()
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(address)) throw new BadRequestError("That email doesn't look right.")
  if (String(password || "").length < 8) throw new BadRequestError("Choose a password of at least 8 letters or numbers.")
  try {
    $app.findAuthRecordByEmail("users", address)
    throw new BadRequestError("That email already has an account. Use a different one, or sign in with it.")
  } catch (e) {
    if (e instanceof BadRequestError) throw e
  }
  const user = new Record($app.findCollectionByNameOrId("users"))
  user.setEmail(address)
  user.setPassword(String(password))
  user.setVerified(true)
  user.set("family", record.getString("user"))
  $app.runInTransaction((txApp) => {
    txApp.save(user)
    txApp.delete(record)
  })
  return user
}

/**
 * Takes [id] out of [me]'s family: the owner can remove anyone else, and anyone can remove
 * themselves. Their account is deleted; it never held anything of its own.
 */
function remove(me, id) {
  const owner = lib().familyOf(me)
  if (id === owner.id) throw new BadRequestError("The family's first account can't be removed here.")
  if (me.id !== owner.id && me.id !== id) throw new ForbiddenError("Only the family's first account can remove other grown-ups.")
  let user = null
  try {
    user = $app.findRecordById("users", String(id || ""))
  } catch (_) {}
  if (!user || user.getString("family") !== owner.id) throw new NotFoundError("They're not in this family any more.")
  $app.delete(user)
}

function cancelInvite(me, id) {
  const owner = lib().familyOf(me)
  $app.delete(lib().own("invites", id, owner.id))
}

/** Old invites, removed now and then. */
function cleanup() {
  const old = $app.findRecordsByFilter("invites", "expires < {:now}", "", 200, 0, { now: new Date().toISOString().replace("T", " ") })
  for (let i = 0; i < old.length; i++) $app.delete(old[i])
}

module.exports = { familyInfo, invite, inviteInfo, join, remove, cancelInvite }
