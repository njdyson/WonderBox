/// <reference path="../pb_data/types.d.ts" />

// The TV's menu pictures, drawn by Gemini (menu_lib.js). The TV gets the drawn ones in the
// library's menuPictures; the phone app lists and redraws them here.

// {pictures: [{key, title, home, picture, state}], drawing: the server has a Gemini key}.
routerAdd("GET", "/api/wonder/menu-pictures", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const menu = require(`${__hooks}/menu_lib.js`)
  const user = lib.requireUser(e)
  return e.json(200, { pictures: menu.pictureList(user.id), drawing: menu.available() })
})

// {key}: the parent wants that picture drawn again. It's done within a minute or two.
routerAdd("POST", "/api/wonder/menu-pictures/redraw", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const menu = require(`${__hooks}/menu_lib.js`)
  const user = lib.requireUser(e)
  if (!menu.available()) throw new ApiError(503, "This needs a Gemini key on the server.")
  menu.redraw(user.id, String((e.requestInfo().body || {}).key || ""))
  return e.json(200, { pictures: menu.pictureList(user.id), drawing: true })
})

// A menu picture, for the parent or a linked TV.
routerAdd("GET", "/api/wonder/menu-pictures/{id}", (e) => {
  const lib = require(`${__hooks}/wonderbox_lib.js`)
  const record = lib.own("menu_pictures", e.request.pathValue("id"), lib.caller(e).id)
  const name = record.getString("picture")
  if (!name) throw new NotFoundError("That picture isn't drawn yet.")
  e.response.header().set("Cache-Control", "private, max-age=31536000, immutable")
  const fsys = $app.newFilesystem()
  try {
    fsys.serve(e.response, e.request, record.baseFilesPath() + "/" + name, name)
  } finally {
    fsys.close()
  }
})

// Draws waiting menu pictures, a few at a time.
cronAdd("wonderbox_menu_pictures", "* * * * *", () => {
  require(`${__hooks}/menu_lib.js`).drawPending(2)
})
