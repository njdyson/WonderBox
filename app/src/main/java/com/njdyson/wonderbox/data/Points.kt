package com.njdyson.wonderbox.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One change to a child's stars made on this TV: the [id] of its event, and when that was sent (0 not yet). */
@Serializable
data class PointsChange(val id: String, val child: String, val delta: Int, val sentAt: Long = 0)

/**
 * Each child's stars. The server keeps the balance, adding up the points.earn, points.spend
 * and points.adjust events (the last from the phone app); this TV adds its own changes the
 * server hasn't counted yet, so the jar is right offline too. Both activities (the main
 * screens and the player) use it. Call [init] once at start.
 */
object Points {
    private val main = Handler(Looper.getMainLooper())
    private var store: Store? = null
    private var initedWith: Context? = null

    /** Balances from the last library fetched. */
    private var server by mutableStateOf<Map<String, Int>>(emptyMap())
    private var changes by mutableStateOf<List<PointsChange>>(emptyList())

    /** How long videos are (seconds), learnt as they load, for prices before they're played. */
    var durations by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        if (store != null && app === initedWith) return
        initedWith = app
        val s = Store(app)
        store = s
        changes = s.pointsChanges
        durations = s.durations
        server = s.library?.children.orEmpty().associate { it.id to it.points }
    }

    /** The stars in [childId]'s jar. */
    fun balance(childId: String): Int = maxOf(0, (server[childId] ?: 0) + changes.filter { it.child == childId }.sumOf { it.delta })

    /** Adds [stars] to the jar, e.g. reason "quiz.answer" with the game as [subject]. */
    fun earn(childId: String, stars: Int, reason: String, subject: String = "", data: JsonObjectBuilder.() -> Unit = {}) {
        if (stars > 0) record("points.earn", childId, stars, stars, reason, subject, data)
    }

    /** Takes [stars] out of the jar to watch something; false (and nothing taken) if there aren't enough. */
    fun spend(childId: String, stars: Int, reason: String, subject: String = "", data: JsonObjectBuilder.() -> Unit = {}): Boolean {
        if (stars <= 0) return true
        if (balance(childId) < stars) return false
        record("points.spend", childId, stars, -stars, reason, subject, data)
        return true
    }

    /**
     * Stars for [seconds] of a learning video played: one per the child's videoMinutes, up to
     * their daily cap. Returns how many were added.
     */
    fun earnFromVideo(child: Child, seconds: Double, youtubeId: String): Int {
        val s = store ?: return 0
        val rates = child.games.points
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val (day, soFar) = s.videoPoints(child.id)
        val already = if (day == today) soFar else 0
        var stars = rates.earned(seconds)
        if (rates.videoCap > 0) stars = minOf(stars, rates.videoCap - already)
        if (stars <= 0) return 0
        s.setVideoPoints(child.id, today, already + stars)
        earn(child.id, stars, "video.watch", youtubeId) { put("seconds", seconds.toInt()) }
        return stars
    }

    /** Remembers how long a video is, for its price or stars badge. */
    fun learnDuration(youtubeId: String, seconds: Double) {
        val whole = seconds.toInt()
        if (whole <= 0 || durations[youtubeId] == whole) return
        durations = (durations + (youtubeId to whole)).entries.toList().takeLast(MAX_DURATIONS).associate { it.key to it.value }
        store?.durations = durations
    }

    /** Events sent to the server ([ids]): counted there from now on. Called by [EventLog] from its thread. */
    internal fun sent(ids: Set<String>) {
        main.post {
            if (changes.none { it.id in ids }) return@post
            val now = System.currentTimeMillis()
            changes = changes.map { if (it.id in ids && it.sentAt == 0L) it.copy(sentAt = now) else it }
            store?.pointsChanges = changes
        }
    }

    /**
     * A new library from the server, asked for at [askedAt]: its balances count every change
     * sent before then, so those are dropped here.
     */
    fun fromServer(library: Library, askedAt: Long) {
        server = library.children.associate { it.id to it.points }
        changes = changes.filter { it.sentAt == 0L || it.sentAt >= askedAt }
        store?.pointsChanges = changes
    }

    /** Unlinked: the stars go with the server. */
    fun forget() {
        server = emptyMap()
        changes = emptyList()
        store?.pointsChanges = changes
    }

    private fun record(type: String, childId: String, stars: Int, delta: Int, reason: String, subject: String, data: JsonObjectBuilder.() -> Unit) {
        val id = EventLog.log(type, childId, subject = subject, value = stars.toDouble()) {
            put("reason", reason)
            data()
        }
        changes = changes + PointsChange(id, childId, delta)
        store?.pointsChanges = changes
    }

    private const val MAX_DURATIONS = 3000
}
