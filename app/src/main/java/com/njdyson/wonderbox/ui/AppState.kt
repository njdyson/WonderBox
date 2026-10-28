package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.njdyson.wonderbox.BuildConfig
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.Server
import com.njdyson.wonderbox.data.Store
import com.njdyson.wonderbox.data.UnlinkedException
import com.njdyson.wonderbox.data.Shelf
import com.njdyson.wonderbox.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The TV app's state: whether it's linked, the children and videos, treats earned, and the grown-ups' settings. */
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

    /** Treats earned by finishing a game and not yet picked, by child ID. Kept on this TV. */
    var treats by mutableStateOf(store.treats)
        private set

    fun treatsFor(childId: String) = treats[childId] ?: 0

    fun earnTreat(childId: String) {
        treats = treats + (childId to treatsFor(childId) + 1)
        store.treats = treats
    }

    /** Picked treats waiting in each child's Treats section, as [Video.id]s, by child ID. Kept on this TV. */
    var savedTreats by mutableStateOf(store.savedTreats)
        private set

    /**
     * A child's saved treats, oldest first, that are still in the library (the parent may have
     * removed one). Each video's ID is "saved/<n>/<id>", n its place in [savedTreats], so the
     * same treat saved twice shows twice.
     */
    fun savedTreatsFor(childId: String): List<Video> {
        val all = library.treatShelvesFor(childId).flatMap { it.videos }.associateBy { it.id }
        return savedTreats[childId].orEmpty().mapIndexedNotNull { i, id -> all[id]?.copy(id = "saved/$i/$id") }
    }

    /** The Treats section's rows: their saved treats, then all the treats while they've one to pick. */
    fun treatsSectionFor(childId: String): List<Shelf> = buildList {
        val mine = savedTreatsFor(childId)
        if (mine.isNotEmpty()) add(Shelf("saved", "My treats", true, mine))
        if (treatsFor(childId) > 0) {
            addAll(library.treatShelvesFor(childId).map { it.copy(title = if (it.key == "treats") "Pick one to save" else "Pick one to save: ${it.title}") })
        }
    }

    /** Uses up one of the child's treats to save [video] in their Treats section; false if they have none. */
    fun saveTreat(childId: String, video: Video): Boolean {
        val have = treatsFor(childId)
        if (have <= 0) return false
        treats = treats + (childId to have - 1)
        store.treats = treats
        savedTreats = savedTreats + (childId to savedTreats[childId].orEmpty() + video.id)
        store.savedTreats = savedTreats
        return true
    }

    /** Takes a saved treat (one from [savedTreatsFor]) out of the Treats section, as it's watched. */
    fun useSavedTreat(childId: String, saved: Video) {
        val index = saved.id.split("/").getOrNull(1)?.toIntOrNull() ?: return
        val list = savedTreats[childId].orEmpty()
        if (index !in list.indices) return
        savedTreats = savedTreats + (childId to list.filterIndexed { i, _ -> i != index })
        store.savedTreats = savedTreats
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
            val fresh = withContext(Dispatchers.IO) { server.library() }
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
}
