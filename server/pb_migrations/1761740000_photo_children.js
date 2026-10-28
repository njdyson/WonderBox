/// <reference path="../pb_data/types.d.ts" />

// Each photo is for one or more children, like videos. Photos added before this were for
// everyone, so they're given to all of that parent's children.
migrate((app) => {
  const photos = app.findCollectionByNameOrId("photos")
  const children = app.findCollectionByNameOrId("children")
  photos.fields.add(new RelationField({ name: "children", collectionId: children.id, maxSelect: 50 }))
  app.save(photos)
  for (const photo of app.findAllRecords("photos")) {
    const kids = app.findRecordsByFilter("children", "user = {:u}", "", 0, 0, { u: photo.getString("user") })
    const ids = []
    for (let i = 0; i < kids.length; i++) ids.push(kids[i].id)
    photo.set("children", ids)
    app.save(photo)
  }
}, (app) => {
  const photos = app.findCollectionByNameOrId("photos")
  photos.fields.removeByName("children")
  app.save(photos)
})
