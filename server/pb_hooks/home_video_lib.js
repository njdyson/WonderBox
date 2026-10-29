/// <reference path="../pb_data/types.d.ts" />

// Home videos (see the 1761800000 migration): uploading in pieces, converting with ffmpeg
// (home_video.sh) and picking up the result. Loaded with require() from photos.pb.js.
//
// The phone sends a video in pieces of PIECE bytes, under Plesk's nginx upload limit
// (client_max_body_size 10m) and quick to resend on a weak signal. They're kept in
// <data>/home-videos/<record id>/ until the video is converted, then moved into the record's
// protected files.

const PIECE = 8 * 1024 * 1024
/** The biggest video the phone may send: about half an hour of 4K. */
const MAX_BYTES = 4 * 1024 * 1024 * 1024
const MAX_PIECES = Math.ceil(MAX_BYTES / PIECE)
/** Unfinished uploads are given up on after a day. */
const UPLOAD_HOURS = 24
/** A conversion is restarted this many times (say after the server restarted) before it's given up. */
const TRIES = 3

function folder(id) {
  return `${$app.dataDir()}/home-videos/${id}`
}

function ffmpeg() {
  return $os.getenv("FFMPEG_BIN") || "ffmpeg"
}

function ffprobe() {
  return $os.getenv("FFPROBE_BIN") || "ffprobe"
}

/** Why the server can't convert videos, or "" when it can. */
function problem() {
  try {
    $os.cmd(ffmpeg(), "-hide_banner", "-version").output()
    $os.cmd(ffprobe(), "-hide_banner", "-version").output()
    return ""
  } catch (_) {
    return "The server can't convert videos yet: run the install task again to add ffmpeg."
  }
}

function exists(path) {
  try {
    $os.stat(path)
    return true
  } catch (_) {
    return false
  }
}

function readText(path) {
  try {
    return toString($os.readFile(path)).trim()
  } catch (_) {
    return ""
  }
}

/** A new home video for [kids], waiting for its pieces. */
function start(userId, kids, size) {
  const bytes = Math.round(Number(size) || 0)
  if (bytes <= 0) throw new BadRequestError("Choose a video.")
  if (bytes > MAX_BYTES) throw new BadRequestError("That video is too long. Try one under half an hour, or trim it on your phone first.")
  const why = problem()
  if (why) throw new BadRequestError(why)
  const record = new Record($app.findCollectionByNameOrId("photos"))
  record.set("user", userId)
  record.set("children", kids)
  record.set("status", "uploading")
  $app.save(record)
  $os.mkdirAll(folder(record.id), 0o755)
  return record
}

/** Saves piece [n] (from 0) of an uploading video, sent as the raw request body. */
function savePiece(e, record, n) {
  if (record.getString("status") !== "uploading") throw new BadRequestError("That video has already been sent.")
  const index = Math.floor(Number(n))
  if (!(index >= 0 && index < MAX_PIECES)) throw new BadRequestError("That piece doesn't belong to a video.")
  const bytes = toBytes(e.request.body, PIECE + 1)
  if (!bytes.length || bytes.length > PIECE) throw new BadRequestError("That piece is the wrong size.")
  const dir = folder(record.id)
  $os.mkdirAll(dir, 0o755)
  // Written under another name first, so a piece cut off halfway never counts.
  $os.writeFile(`${dir}/${index}.tmp`, bytes, 0o644)
  $os.rename(`${dir}/${index}.tmp`, `${dir}/${index}.part`)
}

/** All [pieces] have arrived: starts converting the video. */
function finish(record, pieces) {
  if (record.getString("status") !== "uploading") throw new BadRequestError("That video has already been sent.")
  const count = Math.floor(Number(pieces))
  if (!(count > 0 && count <= MAX_PIECES)) throw new BadRequestError("That video didn't arrive. Try again.")
  const dir = folder(record.id)
  for (let i = 0; i < count; i++) {
    if (!exists(`${dir}/${i}.part`)) throw new BadRequestError("Part of that video didn't arrive. Try again.")
  }
  if (exists(`${dir}/${count}.part`)) throw new BadRequestError("That video didn't arrive properly. Try again.")
  record.set("status", "converting")
  $app.save(record)
  convert(record.id)
}

/** Runs home_video.sh in the background; it outlives this request and reports by writing files. */
function convert(id) {
  const dir = folder(id)
  const tries = (parseInt(readText(`${dir}/tries`), 10) || 0) + 1
  $os.writeFile(`${dir}/tries`, String(tries), 0o644)
  $os.cmd("sh", "-c", 'nohup sh "$0" "$1" "$2" "$3" > "$1/run.log" 2>&1 &', `${__hooks}/home_video.sh`, dir, ffmpeg(), ffprobe()).run()
}

/** Whether the conversion in [dir] is still running (it's lost if the server restarted). */
function running(dir) {
  const pid = parseInt(readText(`${dir}/pid`), 10)
  if (!pid) return false
  try {
    $os.cmd("kill", "-0", String(pid)).run()
    return true
  } catch (_) {
    return false
  }
}

function fail(record, why) {
  record.set("status", "failed")
  record.set("problem", String(why || "The server couldn't convert that video.").slice(0, 500))
  $app.save(record)
  try { $os.removeAll(folder(record.id)) } catch (_) {}
}

/**
 * The cron job: moves finished videos into their records, restarts conversions a server
 * restart cut short, and gives up on uploads nobody finished.
 */
function check() {
  const converting = $app.findRecordsByFilter("photos", "status = 'converting'", "created", 100, 0)
  for (let i = 0; i < converting.length; i++) {
    const record = converting[i]
    const dir = folder(record.id)
    try {
      if (exists(`${dir}/done`)) {
        attach(record, dir)
      } else if (exists(`${dir}/failed`)) {
        fail(record, readText(`${dir}/failed`))
      } else if (!running(dir)) {
        // Wait a minute after starting before deciding it's gone (it writes its pid first thing).
        if (Date.now() - record.getDateTime("updated").unix() * 1000 < 60 * 1000) continue
        if ((parseInt(readText(`${dir}/tries`), 10) || 0) >= TRIES) fail(record, "The server kept stopping while it converted that video. Try sending it again.")
        else {
          // Its pieces are gone once joined, so it starts again from the joined file.
          if (!exists(`${dir}/0.part`) && exists(`${dir}/source`)) $os.rename(`${dir}/source`, `${dir}/0.part`)
          if (!exists(`${dir}/0.part`)) fail(record, "That video was lost while it converted. Try sending it again.")
          else convert(record.id)
        }
      }
    } catch (err) {
      console.log(`home video ${record.id}: ${err}`)
      fail(record, "The server couldn't save that video. It may be out of space.")
    }
  }
  const cutoff = new Date(Date.now() - UPLOAD_HOURS * 3600 * 1000).toISOString().replace("T", " ")
  const stale = $app.findRecordsByFilter("photos", "status = 'uploading' && created < {:t}", "", 100, 0, { t: cutoff })
  for (let i = 0; i < stale.length; i++) $app.delete(stale[i])
}

/** Puts a converted video (and its stills) into its record, which makes it show on the TVs. */
function attach(record, dir) {
  let meta = {}
  try { meta = JSON.parse(readText(`${dir}/meta.json`) || "{}") } catch (_) {}
  const info = $os.stat(`${dir}/video.mp4`)
  record.set("video", $filesystem.fileFromPath(`${dir}/video.mp4`))
  record.set("image", $filesystem.fileFromPath(`${dir}/poster.jpg`))
  record.set("thumb", $filesystem.fileFromPath(`${dir}/thumb.jpg`))
  record.set("duration", Math.round((Number(meta.duration) || 0) * 10) / 10)
  record.set("width", Math.round(Number(meta.width) || 0))
  record.set("height", Math.round(Number(meta.height) || 0))
  record.set("size", info.size())
  record.set("status", "")
  record.set("problem", "")
  $app.save(record)
  $os.removeAll(dir)
}

/** Clears up after a deleted photo or video (its upload folder, if it still has one). */
function forget(id) {
  try { $os.removeAll(folder(id)) } catch (_) {}
}

module.exports = { PIECE, MAX_BYTES, problem, start, savePiece, finish, check, forget }
