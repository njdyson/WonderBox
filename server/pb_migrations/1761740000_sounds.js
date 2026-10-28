/// <reference path="../pb_data/types.d.ts" />

// The games' sound effects on the TV for each child: "off", "calm" or "silly" (empty, not
// set yet, means silly).
migrate((app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.add(new TextField({ name: "sounds", max: 10 }))
  app.save(children)
}, (app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.removeByName("sounds")
  app.save(children)
})
