package com.njdyson.wonderbox.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.njdyson.wonderbox.BuildConfig
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.Server
import com.njdyson.wonderbox.data.Store
import com.njdyson.wonderbox.data.UnlinkedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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

    /** Uses up one of the child's treats; false if they have none. */
    fun spendTreat(childId: String): Boolean {
        val have = treatsFor(childId)
        if (have <= 0) return false
        treats = treats + (childId to have - 1)
        store.treats = treats
        return true
    }

    /** The games' sound effects. */
    val sounds = Sounds(context.applicationContext)

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.UK
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    /** Fetches the latest children and videos. Keeps the cached ones if the network is down. */
    suspend fun sync() {
        val key = deviceKey ?: return
        try {
            val server = Server(serverUrl, key)
            val fresh = withContext(Dispatchers.IO) { server.library() }
            library = fresh
            store.library = fresh
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
        if (!on) tts?.stop()
    }

    /** Reads [text] aloud (a 4-year-old can't read the titles), replacing anything still being said. */
    fun speak(text: String) {
        if (speakTitles) say(text)
    }

    /** Reads [text] aloud even when titles aren't read: the games' questions need it. */
    fun say(text: String) {
        if (ttsReady && text.isNotBlank()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun close() {
        tts?.shutdown()
        tts = null
        sounds.close()
    }
}
