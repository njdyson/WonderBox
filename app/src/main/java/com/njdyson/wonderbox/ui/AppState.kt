package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.njdyson.wonderbox.BuildConfig
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.Points
import com.njdyson.wonderbox.data.Server
import com.njdyson.wonderbox.data.Store
import com.njdyson.wonderbox.data.UnlinkedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The TV app's state: whether it's linked, the children and videos, and the grown-ups' settings. Stars are in [Points]. */
class AppState(context: Context) {
    private val store = Store(context)
    val serverUrl = BuildConfig.SERVER_URL

    var deviceKey by mutableStateOf(store.deviceKey)
        private set
    val linked get() = deviceKey != null

    var library by mutableStateOf(store.library ?: Library())
        private set

    /** The family photos, newest first, each for one or more children. */
    var photos by mutableStateOf(store.photos)
        private set

    /** Why the last sync failed, for the grown-ups' settings; null when it worked. */
    var syncError by mutableStateOf<String?>(null)
        private set

    var speakTitles by mutableStateOf(store.speakTitles)
        private set

    /** Videos each child has watched all the way through (YouTube IDs), recorded by the player. */
    var watched by mutableStateOf(store.watched.mapValues { it.value.toSet() })
        private set

    fun watchedFor(childId: String): Set<String> = watched[childId].orEmpty()

    /** Picks up the videos the player recorded as watched while it was on top. */
    fun reloadFromPlayer() {
        watched = store.watched.mapValues { it.value.toSet() }
    }

    /** The stars in [childId]'s jar. */
    fun starsFor(childId: String) = Points.balance(childId)

    init {
        Points.init(context)
        // Treats won or saved before stars: five stars each, about a treat video's worth.
        store.takeOldTreats().forEach { (child, n) -> Points.earn(child, n * OLD_TREAT_STARS, "treats.convert") }
    }

    /** The games' sound effects. */
    val sounds = Sounds(context.applicationContext)

    /** Reads questions and titles aloud: the server's voice, or this TV's. */
    val voice = Voice(context) { deviceKey?.let { serverUrl to it } }.also { it.serverHasVoice = library.voice }

    /** Fetches the latest children and videos. Keeps the cached ones if the network is down. */
    suspend fun sync() {
        val key = deviceKey ?: return
        try {
            val server = Server(serverUrl, key)
            val askedAt = System.currentTimeMillis()
            val fresh = withContext(Dispatchers.IO) { server.library() }
            Points.fromServer(fresh, askedAt)
            library = fresh
            store.library = fresh
            voice.serverHasVoice = fresh.voice
            syncError = null
            // Photos are extra: if they fail (say, a server without them yet) the videos still work.
            runCatching { withContext(Dispatchers.IO) { server.photos() } }.onSuccess {
                photos = it
                store.photos = it
            }
        } catch (e: UnlinkedException) {
            forgetLink()
        } catch (e: Exception) {
            syncError = e.message ?: e.javaClass.simpleName
        }
    }

    suspend fun completeLink(key: String) {
        store.deviceKey = key
        deviceKey = key
        sync()
    }

    suspend fun unlink() {
        val key = deviceKey ?: return
        runCatching { withContext(Dispatchers.IO) { Server(serverUrl, key).forget() } }
        forgetLink()
    }

    private fun forgetLink() {
        store.deviceKey = null
        store.library = null
        store.photos = emptyList()
        deviceKey = null
        library = Library()
        photos = emptyList()
        Points.forget()
    }

    /** One child's photos, newest first. */
    fun photosFor(childId: String) = photos.filter { childId in it.children }

    /** The full address of a photo file; the server wants this TV's key with it ([deviceKey]). */
    fun photoUrl(path: String) = serverUrl.trimEnd('/') + path

    fun chooseSpeakTitles(on: Boolean) {
        speakTitles = on
        store.speakTitles = on
        if (!on) voice.stop()
    }

    /** Reads [text] aloud (a 4-year-old can't read the titles), replacing anything still being said. */
    fun speak(text: String) {
        if (speakTitles) say(text)
    }

    /** Reads [text] aloud even when titles aren't read: the games' questions need it. */
    fun say(text: String) {
        voice.say(text)
    }

    fun stopSpeaking() {
        voice.stop()
    }

    fun close() {
        voice.close()
        sounds.close()
    }

    companion object {
        const val OLD_TREAT_STARS = 5
    }
}
