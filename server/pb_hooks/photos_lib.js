/// <reference path="../pb_data/types.d.ts" />

// Helpers for photos.pb.js, loaded with require() like wonderbox_lib.js.

/** The user's photos, newest first, as the TV and the phone app list them. */
function photoList(userId) {
  const records = $app.findRecordsByFilter("photos", "user = {:u}", "-created", 5000, 0, { u: userId })
  const list = []
  for (let i = 0; i < records.length; i++) {
    const p = records[i]
    list.push({
      id: p.id,
      image: `/api/wonder/photos/${p.id}/image`,
      thumb: `/api/wonder/photos/${p.id}/thumb`,
      width: p.getInt("width"),
      height: p.getInt("height"),
      created: p.getString("created"),
    })
  }
  return list
}

/** The file sent as [key] in a multipart request, or null (findUploadedFiles throws when it's missing). */
function uploaded(e, key) {
  try {
    const files = e.findUploadedFiles(key)
    return files && files.length ? files[0] : null
  } catch (_) {
    return null
  }
}

module.exports = { photoList, uploaded }
