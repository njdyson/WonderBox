package com.njdyson.wonderbox.data

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

/** A child, picked by their avatar when the app opens. */
@Serializable
data class Child(val id: String, val name: String, val avatar: String = "", val color: String = "#FFD23F")

/** A YouTube video the parent chose, for one or more children. */
@Serializable
data class Video(
    val id: String,
    val youtubeId: String,
    val title: String = "",
    val channel: String = "",
    val thumbnail: String = "",
    val children: List<String> = emptyList(),
)

/** Everything the TV shows, as the server sends it (GET /api/wonder/library). */
@Serializable
data class Library(val children: List<Child> = emptyList(), val videos: List<Video> = emptyList()) {
    fun videosFor(childId: String) = videos.filter { childId in it.children }
}

/** A family photo, shown to everyone in the slideshow. [image] and [thumb] are paths on the server. */
@Serializable
data class Photo(val id: String, val image: String, val thumb: String = "", val width: Int = 0, val height: Int = 0)

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
