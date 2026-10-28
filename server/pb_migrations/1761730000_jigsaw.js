/// <reference path="../pb_data/types.d.ts" />

// Photo jigsaws on the TV: how many pieces along each side for each child. 0 (not set yet)
// means the default, 3 x 3; 1 means jigsaws are turned off for that child.
migrate((app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.add(new NumberField({ name: "jigsaw_size", min: 0, max: 9, onlyInt: true }))
  app.save(children)
}, (app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.removeByName("jigsaw_size")
  app.save(children)
})
