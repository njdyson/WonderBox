package com.njdyson.wonderbox.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.njdyson.wonderbox.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** Something that happened on the TV, for the parent's stats. The types are listed in server/EVENTS.md. */
@Serializable
data class LoggedEvent(
    // Made here, so a batch sent twice (the answer was lost) is only saved once.
    val id: String,
    val type: String,
    val child: String? = null,
    val subject: String = "",
    val value: Double = 0.0,
    val data: JsonObject? = null,
    val at: String,
)

/**
 * The activity log. Anything on the TV can call [log]; events are kept in a file and sent
 * to the server in the background a few seconds later, so they survive the network being
 * down or the app closing. Call [init] once at start.
 *
 * ```
 * EventLog.log("quiz.answer", child.id, subject = "maths.add", value = 1.0) {
 *     put("question", "2 + 3")
 *     put("answer", "5")
 * }
 * ```
 */
object EventLog {
    private const val MAX_QUEUED = 2000
    private const val BATCH = 200
    private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val listSerializer = ListSerializer(LoggedEvent.serializer())
    private val lock = Any()
    private var queue = mutableListOf<LoggedEvent>()
    private var file: File? = null
    private var store: Store? = null
    private val flushSoon = Runnable { worker.execute { send() } }

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "events.json")
        file = f
        store = Store(context.applicationContext)
        worker.execute {
            val saved = runCatching { json.decodeFromString(listSerializer, f.readText()) }.getOrNull().orEmpty()
            synchronized(lock) { queue.addAll(0, saved) }
            send()
        }
    }

    /**
     * Records an event. [type] is dotted and lower case ("video.play"); [child] is who was
     * using the TV; [subject] what it was about (a YouTube ID, a game); [value] one number
     * worth adding up; [data] anything else. Returns the event's ID.
     */
    fun log(
        type: String,
        child: String? = null,
        subject: String = "",
        value: Double = 0.0,
        data: (JsonObjectBuilder.() -> Unit)? = null,
    ): String {
        val event = LoggedEvent(newId(), type, child, subject, value, data?.let { buildJsonObject(it) }, now())
        synchronized(lock) {
            queue.add(event)
            if (queue.size > MAX_QUEUED) queue = queue.takeLast(MAX_QUEUED).toMutableList()
        }
        worker.execute { save() }
        main.removeCallbacks(flushSoon)
        main.postDelayed(flushSoon, 5_000)
        return event.id
    }

    /** Sends anything waiting now, e.g. when the app comes back to the foreground. */
    fun flush() {
        main.removeCallbacks(flushSoon)
        worker.execute { send() }
    }

    // On the worker thread.
    private fun send() {
        val key = store?.deviceKey ?: return
        val server = Server(BuildConfig.SERVER_URL, key)
        while (true) {
            val batch = synchronized(lock) { queue.take(BATCH) }
            if (batch.isEmpty()) return
            try {
                server.sendEvents(batch)
            } catch (e: UnlinkedException) {
                // Unlinked from the phone app: there's no one to send them to any more.
                synchronized(lock) { queue.clear() }
                save()
                return
            } catch (e: Exception) {
                return // Offline: try again with the next event or flush.
            }
            synchronized(lock) { queue.removeAll(batch.toSet()) }
            save()
            Points.sent(batch.mapTo(HashSet()) { it.id })
        }
    }

    private fun save() {
        val f = file ?: return
        val text = synchronized(lock) { json.encodeToString(listSerializer, queue.toList()) }
        runCatching {
            val tmp = File(f.parentFile, "events.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }

    private fun newId() = String(CharArray(15) { ID_CHARS[random.nextInt(ID_CHARS.length)] })

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
