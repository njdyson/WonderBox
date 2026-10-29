/// <reference path="../pb_data/types.d.ts" />

// The activity log behind events.pb.js: saving events from TVs, and adding them up for the
// phone app's stats. The event types are listed in server/EVENTS.md.

const TYPE_PATTERN = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/
const ID_PATTERN = /^[a-z0-9]{15}$/
const MAX_BATCH = 500
const MAX_DATA_BYTES = 8000
const DAY_MS = 24 * 3600 * 1000

/** The linked TV making the request, or null (e.g. the phone app). */
function callerDevice(e) {
  const key = e.request.header.get("X-Device-Key")
  if (!key) return null
  try {
    return $app.findFirstRecordByFilter("devices", "key_hash = {:h}", { h: $security.sha256(key) })
  } catch (_) {
    return null
  }
}

/** PocketBase's date format, for filters. */
function dbDate(ms) {
  return new Date(ms).toISOString().replace("T", " ")
}

/** When an event happened: the time the TV sent, or now if it's missing, broken or in the future. */
function eventTime(value) {
  const ms = Date.parse(String(value || ""))
  if (isNaN(ms) || ms > Date.now() + 60 * 1000) return new Date().toISOString()
  return new Date(ms).toISOString()
}

/**
 * Saves a batch of events ({events: [...]}, or one event on its own) for [userId]. Each has
 * a type, and optionally child, subject, value, data, at and id. A TV retrying a batch sends
 * the same ids, so events already saved are skipped. Events that don't fit are skipped too,
 * not refused, so one bad event can't block a TV's whole queue.
 */
function saveEvents(userId, device, body) {
  const list = Array.isArray(body.events) ? body.events : body.type ? [body] : []
  if (list.length > MAX_BATCH) throw new BadRequestError(`At most ${MAX_BATCH} events at a time.`)
  const kids = new Set()
  const children = $app.findRecordsByFilter("children", "user = {:u}", "", 500, 0, { u: userId })
  for (let i = 0; i < children.length; i++) kids.add(children[i].id)
  const collection = $app.findCollectionByNameOrId("events")
  let saved = 0
  let skipped = 0
  for (const item of list) {
    const ev = item || {}
    const type = String(ev.type || "")
    const childId = ev.child ? String(ev.child) : ""
    const value = Number(ev.value || 0)
    const data = ev.data && typeof ev.data === "object" && !Array.isArray(ev.data) ? ev.data : {}
    // A child removed while the TV was offline: their events go with them.
    if (!TYPE_PATTERN.test(type) || type.length > 64 || (childId && !kids.has(childId)) || !isFinite(value) ||
        JSON.stringify(data).length > MAX_DATA_BYTES) {
      skipped++
      continue
    }
    const id = ID_PATTERN.test(String(ev.id || "")) ? String(ev.id) : ""
    if (id) {
      let existing = null
      try { existing = $app.findRecordById("events", id) } catch (_) {}
      if (existing) {
        skipped++
        continue
      }
    }
    const record = new Record(collection)
    if (id) record.set("id", id)
    record.set("user", userId)
    if (childId) record.set("child", childId)
    if (device) record.set("device", device.id)
    record.set("type", type)
    record.set("subject", String(ev.subject || "").slice(0, 200))
    record.set("value", value)
    record.set("data", data)
    record.set("at", eventTime(ev.at))
    $app.save(record)
    saved++
    // An auto-level game moving up or down: the child's new level (only the first time it's sent).
    if (type === "level.change" && childId) {
      require(`${__hooks}/wonderbox_lib.js`).autoLevel(userId, childId, String(ev.subject || ""), data.to)
    }
  }
  return { saved, skipped }
}

/** A plain object from an event record, as the API returns it. */
function eventJson(r) {
  return {
    id: r.id,
    type: r.getString("type"),
    child: r.getString("child"),
    subject: r.getString("subject"),
    value: r.getFloat("value"),
    data: JSON.parse(r.getString("data") || "{}") || {},
    at: r.getDateTime("at").string(),
  }
}

/**
 * The user's events, newest first. Optional query: child, type ("video.play", or "video."
 * for every video event), since (a date), limit (up to 1000).
 */
function listEvents(userId, query) {
  const params = { u: userId }
  let filter = "user = {:u}"
  if (query.child) { filter += " && child = {:c}"; params.c = query.child }
  if (query.type) {
    if (query.type.endsWith(".")) { filter += " && type ~ {:t}"; params.t = query.type + "%" }
    else { filter += " && type = {:t}"; params.t = query.type }
  }
  if (query.since) { filter += " && at >= {:s}"; params.s = dbDate(Date.parse(query.since) || 0) }
  const limit = Math.max(1, Math.min(1000, Number(query.limit) || 100))
  const records = $app.findRecordsByFilter("events", filter, "-at", limit, 0, params)
  const list = []
  for (let i = 0; i < records.length; i++) list.push(eventJson(records[i]))
  return list
}

function bump(map, key, init) {
  if (!map[key]) map[key] = init()
  return map[key]
}

function top(map, n) {
  return Object.values(map).sort((a, b) => b.watchSeconds - a.watchSeconds || b.plays - a.plays).slice(0, n)
}

// The activity feed: these say nothing on their own (they're steps inside something
// that has its own line, or bookkeeping), so they're left out.
const FEED_SKIP = new Set(["app.start", "profile.pick", "treats.count", "video.watch", "quiz.answer", "category.open",
  "hangman.word", "scramble.word", "scramble.found", "scramble.skip", "story.pick", "jigsaw.start", "memory.start", "photoquiz.start"])
// One line for a run of these, whatever each one's subject (e.g. "Looked at 12 photos").
const FEED_RUNS = new Set(["photo.view", "homevideo.play", "story.read", "reward.earn"])
const FEED_GAP_MS = 15 * 60 * 1000
const FEED_SIZE = 100
// The data kept in a feed line, so the answer stays small (a story's whole text is left out).
const FEED_DATA = ["title", "total", "wrong", "words", "from", "to", "reason", "game", "size", "pairs", "level", "missed"]

/**
 * Adds event [r] to [feed] (oldest first): a new line, or one more in the run it continues. A
 * video's line also gets the seconds watched from its `video.watch`.
 */
function addToFeed(feed, r, type, subject, value, ms, videoInfo) {
  let data = {}
  try { data = JSON.parse(r.getString("data") || "{}") || {} } catch (_) {}
  if (type === "video.watch") {
    for (let i = feed.length - 1; i >= 0 && i >= feed.length - 5; i--) {
      if (feed[i].type === "video.play" && feed[i].subject === subject) { feed[i].seconds = (feed[i].seconds || 0) + value; break }
    }
    return
  }
  if (FEED_SKIP.has(type)) return
  const prev = feed[feed.length - 1]
  if (prev && prev.type === type && ms - prev.last < FEED_GAP_MS && (FEED_RUNS.has(type) || prev.subject === subject)) {
    prev.count++
    prev.value += value
    prev.last = ms
    return
  }
  const kept = {}
  for (const k of FEED_DATA) if (data[k] !== undefined) kept[k] = data[k]
  if (type === "video.play") kept.title = (videoInfo[subject] || {}).title || data.title || subject
  feed.push({ type, subject, value, count: 1, at: ms, last: ms, data: kept })
}

/**
 * Totals for the phone app, per child, over the last [days] days. [tzOffset] is the phone's
 * Date.getTimezoneOffset(), so each day starts at the parent's midnight.
 *
 * Every event type is counted (counts and totals of value, by type), so new kinds of event
 * show up without changes here; videos and quizzes also get their own summaries.
 */
function stats(userId, days, tzOffset) {
  days = Math.max(1, Math.min(365, Math.round(Number(days) || 7)))
  const offsetMs = (Number(tzOffset) || 0) * 60 * 1000
  const localDay = (ms) => new Date(ms - offsetMs).toISOString().slice(0, 10)
  // From the parent's midnight [days - 1] days ago, so "7 days" is today and the six before.
  const todayStart = Date.parse(localDay(Date.now()) + "T00:00:00Z") + offsetMs
  const from = todayStart - (days - 1) * DAY_MS

  const videoInfo = {}
  const videos = $app.findRecordsByFilter("videos", "user = {:u}", "", 5000, 0, { u: userId })
  for (let i = 0; i < videos.length; i++) {
    videoInfo[videos[i].getString("youtube_id")] = { title: videos[i].getString("title"), channel: videos[i].getString("channel") }
  }

  const children = $app.findRecordsByFilter("children", "user = {:u}", "sort,created", 500, 0, { u: userId })
  const perChild = {}
  const order = []
  for (let i = 0; i < children.length; i++) {
    const c = children[i]
    order.push(c.id)
    perChild[c.id] = { id: c.id, name: c.getString("name"), avatar: c.getString("avatar"), color: c.getString("color") }
  }
  const summary = {}
  const blank = () => ({ watchSeconds: 0, plays: 0, answers: 0, correct: 0, byType: {}, daily: {}, videos: {}, channels: {}, games: {}, feed: [] })
  for (const id of order) summary[id] = blank()

  // A game's totals; a parent's own quiz (subject quiz/<id>) also carries its name, from the event.
  const game = (s, subject, r) => {
    const g = bump(s.games, subject, () => ({ game: subject, answers: 0, correct: 0, rounds: 0, bestScore: 0 }))
    if (subject.startsWith("quiz/") && !g.title) {
      try {
        const title = (JSON.parse(r.getString("data") || "{}") || {}).title
        if (title) g.title = String(title)
      } catch (_) {}
    }
    return g
  }

  const records = $app.findRecordsByFilter("events", "user = {:u} && at >= {:f}", "at", 50000, 0, { u: userId, f: dbDate(from) })
  let total = 0
  for (let i = 0; i < records.length; i++) {
    const r = records[i]
    const childId = r.getString("child")
    const s = summary[childId || ""] || (summary[childId || ""] = blank())
    const type = r.getString("type")
    const subject = r.getString("subject")
    const value = r.getFloat("value")
    const ms = r.getDateTime("at").unix() * 1000
    total++

    const t = bump(s.byType, type, () => ({ count: 0, total: 0 }))
    t.count++
    t.total += value
    const day = bump(s.daily, localDay(ms), () => ({ watchSeconds: 0, plays: 0, answers: 0, correct: 0 }))

    addToFeed(s.feed, r, type, subject, value, ms, videoInfo)

    if (type === "video.play" || type === "video.watch") {
      const data = JSON.parse(r.getString("data") || "{}") || {}
      const info = videoInfo[subject] || {}
      const title = info.title || data.title || subject
      const channel = info.channel || data.channel || ""
      const v = bump(s.videos, subject, () => ({ youtubeId: subject, title, channel, plays: 0, watchSeconds: 0, finished: 0, lastAt: 0 }))
      v.lastAt = Math.max(v.lastAt, ms)
      const ch = bump(s.channels, channel || "Other", () => ({ channel: channel || "Other", plays: 0, watchSeconds: 0 }))
      if (type === "video.play") {
        s.plays++; day.plays++; v.plays++; ch.plays++
      } else {
        s.watchSeconds += value; day.watchSeconds += value; v.watchSeconds += value; ch.watchSeconds += value
        if (data.finished) v.finished++
      }
    } else if (type === "quiz.answer") {
      const g = game(s, subject, r)
      const right = value > 0 ? 1 : 0
      s.answers++; day.answers++; g.answers++
      s.correct += right; day.correct += right; g.correct += right
    } else if (type === "quiz.finish") {
      const g = game(s, subject, r)
      g.rounds++
      g.bestScore = Math.max(g.bestScore, value)
    }
  }

  const dayList = []
  for (let d = 0; d < days; d++) dayList.push(localDay(from + d * DAY_MS + 12 * 3600 * 1000))
  const shape = (s) => ({
    watchSeconds: Math.round(s.watchSeconds),
    plays: s.plays,
    answers: s.answers,
    correct: s.correct,
    daily: dayList.map((date) => {
      const d = s.daily[date] || { watchSeconds: 0, plays: 0, answers: 0, correct: 0 }
      return { date, watchSeconds: Math.round(d.watchSeconds), plays: d.plays, answers: d.answers, correct: d.correct }
    }),
    topVideos: top(s.videos, 10).map((v) => ({ ...v, watchSeconds: Math.round(v.watchSeconds) })),
    // Most recently watched first, each video once.
    recentVideos: Object.values(s.videos).sort((a, b) => b.lastAt - a.lastAt).slice(0, 10)
      .map((v) => ({ ...v, watchSeconds: Math.round(v.watchSeconds), lastAt: new Date(v.lastAt).toISOString() })),
    topChannels: top(s.channels, 5).map((c) => ({ ...c, watchSeconds: Math.round(c.watchSeconds) })),
    games: Object.values(s.games).sort((a, b) => b.answers - a.answers),
    byType: s.byType,
    // What they did and when, newest first.
    feed: s.feed.slice(-FEED_SIZE).reverse().map((f) => ({ ...f, at: new Date(f.at).toISOString(), last: new Date(f.last).toISOString() })),
  })
  const treats = treatCounts(userId)
  return {
    days,
    from: new Date(from).toISOString(),
    events: total,
    children: order.map((id) => ({ ...perChild[id], ...shape(summary[id]), treats: treats[id] || null })),
    // Events not about one child, e.g. the app starting.
    other: shape(summary[""] || blank()),
  }
}

/**
 * Each child's treats right now, from the TVs' latest `treats.count` events (the treats are kept
 * on the TVs): won and not picked yet (toPick), and picked and waiting in their Treats section
 * (saved), added up over the TVs. A child no TV has reported on is left out.
 */
function treatCounts(userId) {
  const records = $app.findRecordsByFilter("events", "user = {:u} && type = 'treats.count'", "-at", 5000, 0, { u: userId })
  const seen = {}
  const counts = {}
  for (let i = 0; i < records.length; i++) {
    const r = records[i]
    const childId = r.getString("child")
    const key = childId + "/" + r.getString("device")
    if (!childId || seen[key]) continue
    seen[key] = true
    let data = {}
    try { data = JSON.parse(r.getString("data") || "{}") || {} } catch (_) {}
    const c = counts[childId] || (counts[childId] = { toPick: 0, saved: 0, at: "" })
    c.toPick += Math.max(0, Number(data.toPick) || 0)
    c.saved += Math.max(0, Number(data.saved) || 0)
    const at = r.getDateTime("at").string()
    if (at > c.at) c.at = at
  }
  return counts
}

module.exports = { callerDevice, saveEvents, listEvents, stats }
