/// <reference path="../pb_data/types.d.ts" />

// The first story pictures were asked for at 16:9 and came back with black bars. Ask for them
// again (now 4:3, edge to edge); each keeps its old picture until the new one is drawn.
migrate((app) => {
  const stories = app.findRecordsByFilter("stories", "picture_state != ''", "", 1000, 0)
  for (let i = 0; i < stories.length; i++) {
    stories[i].set("picture_state", "")
    app.save(stories[i])
  }
}, () => {})
