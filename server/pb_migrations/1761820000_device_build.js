/// <reference path="../pb_data/types.d.ts" />

// Which build of the app each TV runs (0 not known yet). The TV sends it in an X-App-Build
// header on every call (wonderbox_lib.js caller), so the phone app can show which TVs need
// updating.
migrate((app) => {
  const devices = app.findCollectionByNameOrId("devices")
  devices.fields.add(new NumberField({ name: "build", onlyInt: true }))
  app.save(devices)
}, (app) => {
  const devices = app.findCollectionByNameOrId("devices")
  devices.fields.removeByName("build")
  app.save(devices)
})
