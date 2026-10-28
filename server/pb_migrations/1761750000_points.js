/// <reference path="../pb_data/types.d.ts" />

// Each child's stars: added up from the points.earn, points.spend and points.adjust events
// as they're saved (see wonderbox_events.js), so the TV and the phone app see one balance.
migrate((app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.add(new NumberField({ name: "points", onlyInt: true }))
  app.save(children)
}, (app) => {
  const children = app.findCollectionByNameOrId("children")
  children.fields.removeByName("points")
  app.save(children)
})
