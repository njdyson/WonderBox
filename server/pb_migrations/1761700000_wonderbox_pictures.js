/// <reference path="../pb_data/types.d.ts" />

// A picture for each child (uploaded from the phone app), shown on the TV instead of their
// avatar. The phone app shrinks it to 512 pixels first, so the files stay small.
migrate((app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.add(new FileField({
    name: "picture",
    maxSelect: 1,
    maxSize: 2 * 1024 * 1024,
    mimeTypes: ["image/jpeg", "image/png", "image/webp"],
  }))
  app.save(children)
}, (app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.removeByName("picture")
  app.save(children)
})
