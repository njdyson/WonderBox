package com.njdyson.wonderbox.data

import com.njdyson.wonderbox.games.HangmanSetup
import com.njdyson.wonderbox.games.ClockSetup
import com.njdyson.wonderbox.games.MathsSetup
import com.njdyson.wonderbox.games.MemorySetup
import com.njdyson.wonderbox.games.Quiz
import com.njdyson.wonderbox.games.SpellingSetup
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A child, picked by their face when the app opens. [picture] is a server path to their own
 * picture, or "" to use [avatar] (an emoji). [color] is their screen's background.
 */
@Serializable
data class Child(
    val id: String,
    val name: String,
    val avatar: String = "",
    val color: String = "#FFD23F",
    val picture: String = "",
    val games: GameSettings = GameSettings(),
)

/**
 * How hard a child's games are (set in the phone app), and how many right answers earn a treat.
 * [maths] and [spelling] are levels; the setups say exactly what each game asks (an older
 * server sends only the levels). [jigsaw] is pieces along each side of a photo jigsaw (3 is
 * 3 x 3), or 0 for no jigsaws. [treatGames] are the games that win a treat: "maths",
 * "spelling", "clock", "quizzes" (all the parent's quizzes), "hangman", "memory", "jigsaw".
 * [hangman] and [memory] are those games' setups.
 */
@Serializable
data class GameSettings(
    val maths: Int = 1,
    val spelling: Int = 1,
    val roundSize: Int = 10,
    val mathsSetup: MathsSetup? = null,
    val spellingSetup: SpellingSetup? = null,
    val jigsaw: Int = 3,
    val sounds: String = "silly",
    val hangman: HangmanSetup = HangmanSetup(),
    val memory: MemorySetup = MemorySetup(),
    val clock: Int = 1,
    val clockSetup: ClockSetup? = null,
    val treatGames: List<String> = TREAT_DEFAULT,
) {
    fun maths() = mathsSetup ?: MathsSetup.preset(maths)
    fun spelling() = spellingSetup ?: SpellingSetup.preset(spelling)
    fun clock() = clockSetup ?: ClockSetup.preset(clock)

    /** Whether finishing the game with this [GameEntry.key] wins a treat ("quiz/<id>" is "quizzes"). */
    fun earnsTreat(key: String) = (if (key.startsWith("quiz/")) "quizzes" else key) in treatGames

    companion object {
        // The same as the server's, for an older server that doesn't send them.
        val TREAT_DEFAULT = listOf("maths", "spelling", "clock", "quizzes", "hangman")
    }
}

/** "free" videos are always there; "treat" ones are unlocked by finishing a game. */
const val TREAT = "treat"
const val MUSIC = "music"

/** A YouTube video the parent chose, for one or more children. */
@Serializable
data class Video(
    val id: String,
    val youtubeId: String,
    val title: String = "",
    val channel: String = "",
    val thumbnail: String = "",
    val children: List<String> = emptyList(),
    val tier: String = "free",
    /** "music" for the Music row, otherwise "videos". */
    val section: String = "videos",
) {
    val isTreat get() = tier == TREAT
    val isMusic get() = section == MUSIC
}

/** A whole YouTube channel; the server keeps its latest videos up to date. */
@Serializable
data class Channel(
    val id: String,
    val channelId: String = "",
    val title: String = "",
    val thumbnail: String = "",
    val children: List<String> = emptyList(),
    val tier: String = "free",
    val section: String = "videos",
    val videos: List<ChannelVideo> = emptyList(),
)

@Serializable
data class ChannelVideo(val youtubeId: String, val title: String = "", val thumbnail: String = "")

/** A row of videos on a child's screen: their own videos, or one channel's. [music] rows go on the Music screen. */
data class Shelf(val key: String, val title: String, val treat: Boolean, val videos: List<Video>, val picture: String = "", val music: Boolean = false)

/** Everything the TV shows, as the server sends it (GET /api/wonder/library). */
@Serializable
data class Library(
    val children: List<Child> = emptyList(),
    val quizzes: List<Quiz> = emptyList(),
    val videos: List<Video> = emptyList(),
    val channels: List<Channel> = emptyList(),
) {
    fun videosFor(childId: String) = videos.filter { childId in it.children }

    /**
     * A child's rows, always-there ones first: their own videos, their music, then each
     * channel, then the same again for treats. Empty rows are left out.
     */
    fun shelvesFor(childId: String): List<Shelf> {
        val mine = videosFor(childId)
        val channels = channels.filter { childId in it.children }
        fun shelves(treat: Boolean): List<Shelf> {
            val own = mine.filter { it.isTreat == treat }
            val ownShelf = Shelf(if (treat) "treats" else "videos", if (treat) "Treats" else "My videos", treat, own.filter { !it.isMusic })
            val musicShelf = Shelf(if (treat) "treat-music" else "music", if (treat) "Music treats" else "Music", treat, own.filter { it.isMusic }, music = true)
            val channelShelves = channels.filter { (it.tier == TREAT) == treat }.map { ch ->
                Shelf(
                    "channel/${ch.id}",
                    ch.title.ifBlank { "Channel" },
                    treat,
                    ch.videos.map { v ->
                        Video("${ch.id}/${v.youtubeId}", v.youtubeId, v.title, ch.title, v.thumbnail, ch.children, ch.tier, ch.section)
                    },
                    ch.thumbnail,
                    music = ch.section == MUSIC,
                )
            }
            return (listOf(ownShelf, musicShelf) + channelShelves).filter { it.videos.isNotEmpty() }
        }
        return shelves(treat = false) + shelves(treat = true)
    }

    /** The Videos screen's rows: no music, no treats. */
    fun videoShelvesFor(childId: String) = shelvesFor(childId).filter { !it.treat && !it.music }

    /** The Music screen's rows (no treats). */
    fun musicShelvesFor(childId: String) = shelvesFor(childId).filter { !it.treat && it.music }

    /** The treats, videos and music, only shown once a treat has been won. */
    fun treatShelvesFor(childId: String) = shelvesFor(childId).filter { it.treat }
}

/** A family photo, for the [children] it's for. [image] and [thumb] are paths on the server. */
@Serializable
data class Photo(
    val id: String,
    val image: String,
    val thumb: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val children: List<String> = emptyList(),
)

/** GET /api/wonder/photos: newest first. */
@Serializable
data class Photos(val photos: List<Photo> = emptyList())

/** Thrown when the server no longer accepts this TV's key (it was unlinked from the phone app). */
class UnlinkedException : Exception("This TV was unlinked")

val json = Json { ignoreUnknownKeys = true }

/** Blocking HTTP calls to the server's /api/wonder routes; call from a background thread. */
private fun request(baseUrl: String, path: String, body: JsonElement? = null, deviceKey: String? = null): String {
    val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.setRequestProperty("Content-Type", "application/json")
    deviceKey?.let { conn.setRequestProperty("X-Device-Key", it) }
    if (body != null) {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
    }
    val code = conn.responseCode
    if (code == 401) throw UnlinkedException()
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (code == 410) return text // pairing expired: the caller reads the status
    if (code !in 200..299) error("Server error $code")
    // PocketBase answers unknown routes with the phone app's page when its hooks aren't loaded.
    if (text.trimStart().startsWith("<")) error("The server isn't ready yet")
    return text
}

/** This TV's view of its parent's account. */
class Server(private val baseUrl: String, private val deviceKey: String) {
    fun library(): Library = json.decodeFromString(request(baseUrl, "/api/wonder/library", deviceKey = deviceKey))

    fun photos(): List<Photo> = json.decodeFromString(Photos.serializer(), request(baseUrl, "/api/wonder/photos", deviceKey = deviceKey)).photos
    /** Sends events from [EventLog]; the server skips any it already has. */
    fun sendEvents(events: List<LoggedEvent>) {
        val body = buildJsonObject { put("events", json.encodeToJsonElement(ListSerializer(LoggedEvent.serializer()), events)) }
        request(baseUrl, "/api/wonder/events", body, deviceKey)
    }

    fun forget() {
        request(baseUrl, "/api/wonder/devices/forget", JsonObject(emptyMap()), deviceKey)
    }
}

/** Linking a TV: show a code, the parent approves it in the phone app, the TV receives its key. */
object Pairing {
    @Serializable
    data class Started(val code: String, val secret: String, val expiresInSeconds: Int = 600)

    @Serializable
    data class Poll(val status: String, val deviceKey: String? = null)

    fun start(baseUrl: String, deviceName: String): Started =
        json.decodeFromString(request(baseUrl, "/api/wonder/pair/start", buildJsonObject { put("name", deviceName) }))

    fun poll(baseUrl: String, started: Started): Poll {
        val query = "code=${URLEncoder.encode(started.code, "UTF-8")}&secret=${URLEncoder.encode(started.secret, "UTF-8")}"
        return json.decodeFromString(request(baseUrl, "/api/wonder/pair/poll?$query"))
    }

    /** The address the TV's QR code opens: the phone app, ready to approve this code. */
    fun approveUrl(baseUrl: String, code: String) = "${baseUrl.trimEnd('/')}/#pair=$code"
}
