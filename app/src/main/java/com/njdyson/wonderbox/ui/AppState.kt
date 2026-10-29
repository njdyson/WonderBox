package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.njdyson.wonderbox.BuildConfig
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.SavedStoryPart
import com.njdyson.wonderbox.data.Server
import com.njdyson.wonderbox.data.Store
import com.njdyson.wonderbox.data.UnlinkedException
import com.njdyson.wonderbox.data.Shelf
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.games.AutoLevel
import com.njdyson.wonderbox.games.PhotoQuizPhoto
import com.njdyson.wonderbox.games.StoryPart
import com.njdyson.wonderbox.games.StoryPick
import com.njdyson.wonderbox.games.autoLevel
import com.njdyson.wonderbox.games.withLevel
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put

/** The TV app's state: whether it's linked, the children and videos, treats earned, and the grown-ups' settings. */
class AppState(context: Context) {
    private companion object {
        /** How long a level change waits for the server to catch up before the server's level wins. */
        const val PENDING_LEVEL_MS = 30 * 60 * 1000L
    }

    private val store = Store(context)
    val serverUrl = BuildConfig.SERVER_URL

    var deviceKey by mutableStateOf(store.deviceKey)
        private set
    val linked get() = deviceKey != null

    var library by mutableStateOf(store.library ?: Library())
        private set

    /** The children's finished stories, newest first. */
    var stories by mutableStateOf(store.stories)
        private set

    /** One child's stories, newest first. */
    fun storiesFor(childId: String) = stories.filter { it.child == childId }

    /**
     * Saves a finished story in [childId]'s library and returns its id. If the server can't be
     * reached the story is just not kept, and this returns null.
     */
    suspend fun saveStory(childId: String, parts: List<StoryPart>): String? {
        val key = deviceKey ?: return null
        val saved = parts.map { SavedStoryPart(it.pick.word, it.pick.picture, it.text) }
        return runCatching { withContext(Dispatchers.IO) { Server(serverUrl, key).saveStory(childId, saved) } }.onSuccess {
            stories = it.stories
            store.stories = it.stories
        }.getOrNull()?.saved?.ifBlank { null }
    }

    /** Has the server draw story [id]'s picture now, waiting for it (a minute or two at most); false if it couldn't. */
    suspend fun drawStory(id: String): Boolean {
        val key = deviceKey ?: return false
        if (!library.stories) return false
        return runCatching { withContext(Dispatchers.IO) { Server(serverUrl, key).drawStory(id) } }.onSuccess {
            stories = it
            store.stories = it
        }.getOrNull()?.firstOrNull { it.id == id }?.picture?.isNotBlank() == true
    }

    /** Fetches the stories again (to see a picture that's just been drawn). */
    suspend fun refreshStories() {
        val key = deviceKey ?: return
        runCatching { withContext(Dispatchers.IO) { Server(serverUrl, key).stories() } }.onSuccess {
            stories = it
            store.stories = it
        }
    }

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

    /** Videos each child has watched all the way through (YouTube IDs), recorded by the player. */
    var watched by mutableStateOf(store.watched.mapValues { it.value.toSet() })
        private set

    fun watchedFor(childId: String): Set<String> = watched[childId].orEmpty()

    /**
     * Picks up what the player recorded while it was on top: videos watched, and treats won by
     * watching them. Returns the children who won a treat since.
     */
    fun reloadFromPlayer(): Set<String> {
        watched = store.watched.mapValues { it.value.toSet() }
        val fresh = store.treats
        val won = fresh.filter { (child, n) -> n > treatsFor(child) }.keys
        treats = fresh
        reportTreats()
        return won
    }

    fun earnTreat(childId: String) {
        treats = treats + (childId to treatsFor(childId) + 1)
        store.treats = treats
        reportTreats()
    }

    /**
     * Logs `treats.count` for each child whose treats changed since it was last logged: treats
     * won and not picked, and picked ones waiting in their Treats section. The phone app's
     * Stats show the latest, as the treats themselves are only kept on this TV.
     */
    private fun reportTreats() {
        val reported = store.reportedTreats
        val now = library.children.associate { it.id to "${treatsFor(it.id)}/${savedTreatsFor(it.id).size}" }
        val changed = now.filter { (id, counts) -> reported[id] != counts }
        if (changed.isEmpty()) return
        for ((id, counts) in changed) {
            val (toPick, saved) = counts.split("/").map { it.toInt() }
            EventLog.log("treats.count", id, subject = "treat", value = (toPick + saved).toDouble()) {
                put("toPick", toPick)
                put("saved", saved)
            }
        }
        store.reportedTreats = reported + changed
    }

    /** How the last rounds went at each auto-level game's current level, by "<child>/<game>" ([AutoLevel.step]). */
    private var autoHistory = store.autoHistory

    /**
     * Level changes not yet seen back from the server, by "<child>/<game>": the level and when.
     * A sync that comes back before the TV's `level.change` event reached the server keeps them.
     */
    private val pendingLevels = mutableMapOf<String, Pair<Int, Long>>()

    /**
     * After a round of [game] that went as [result]: moves [childId] up or down a level when the
     * game is on auto and it's time ([AutoLevel.step]). Returns the new level when it changed.
     */
    fun autoLevel(childId: String, game: String, result: AutoLevel.Result): Int? {
        val child = library.children.firstOrNull { it.id == childId } ?: return null
        val (level, levels) = child.games.autoLevel(game) ?: return null
        val key = "$childId/$game"
        // "<level>:<results>", so rounds at a level the parent has since changed don't count.
        val before = autoHistory[key].orEmpty().split(":").takeIf { it.size == 2 && it[0] == "$level" }?.get(1).orEmpty()
        val (next, history) = AutoLevel.step(level, levels, before, result)
        autoHistory = autoHistory + (key to "$next:$history")
        store.autoHistory = autoHistory
        if (next == level) return null
        EventLog.log("level.change", childId, subject = game, value = next.toDouble()) {
            put("from", level)
            put("to", next)
            put("result", result.name.lowercase())
        }
        pendingLevels[key] = next to System.currentTimeMillis()
        putLibrary(withLevels(library))
        return next
    }

    /** [fresh] with the level changes the server hasn't caught up with yet. */
    private fun withLevels(fresh: Library): Library {
        val now = System.currentTimeMillis()
        pendingLevels.entries.removeAll { (key, pending) ->
            val (childId, game) = key.split("/", limit = 2)
            val current = fresh.children.firstOrNull { it.id == childId }?.games?.autoLevel(game)
            current == null || current.first == pending.first || now - pending.second > PENDING_LEVEL_MS
        }
        if (pendingLevels.isEmpty()) return fresh
        return fresh.copy(children = fresh.children.map { child ->
            pendingLevels.entries.filter { it.key.startsWith("${child.id}/") }.fold(child) { c, (key, pending) ->
                c.copy(games = c.games.withLevel(key.substringAfter("/"), pending.first))
            }
        })
    }

    private fun putLibrary(value: Library) {
        library = value
        store.library = value
        // Also the first report after an update, and a saved treat the parent has since removed.
        reportTreats()
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

    /** The Treats section's row: only the treats they've won and picked, never the rest. */
    fun treatsSectionFor(childId: String): List<Shelf> =
        savedTreatsFor(childId).let { mine -> if (mine.isEmpty()) emptyList() else listOf(Shelf("saved", "My treats", true, mine)) }

    /** Uses up one of the child's treats to save [video] in their Treats section; false if they have none. */
    fun saveTreat(childId: String, video: Video): Boolean {
        val have = treatsFor(childId)
        if (have <= 0) return false
        treats = treats + (childId to have - 1)
        store.treats = treats
        savedTreats = savedTreats + (childId to savedTreats[childId].orEmpty() + video.id)
        store.savedTreats = savedTreats
        reportTreats()
        return true
    }

    /** Takes a saved treat (one from [savedTreatsFor]) out of the Treats section, as it's watched. */
    fun useSavedTreat(childId: String, saved: Video) {
        val index = saved.id.split("/").getOrNull(1)?.toIntOrNull() ?: return
        val list = savedTreats[childId].orEmpty()
        if (index !in list.indices) return
        savedTreats = savedTreats + (childId to list.filterIndexed { i, _ -> i != index })
        store.savedTreats = savedTreats
        reportTreats()
    }

    /** The games' sound effects. */
    val sounds = Sounds(context.applicationContext)

    /** Reads questions and titles aloud: the server's voice, or this TV's. */
    val voice = Voice(context) { deviceKey?.let { serverUrl to it } }.also { it.serverHasVoice = library.voice }

    /** The tune behind the menus; quieter while something's read aloud. */
    val music = Music { voice.speaking }

    /** [child]'s decorations today (their phone app setting, or the date's), or null for none. */
    fun seasonFor(child: Child) = Season.of(child.games.season)

    /** Fetches the latest children and videos. Keeps the cached ones if the network is down. */
    suspend fun sync() {
        val key = deviceKey ?: return
        try {
            val server = Server(serverUrl, key)
            val fresh = withContext(Dispatchers.IO) { server.library() }
            putLibrary(withLevels(fresh))
            voice.serverHasVoice = fresh.voice
            syncError = null
            // Photos are extra: if they fail (say, a server without them yet) the videos still work.
            runCatching { withContext(Dispatchers.IO) { server.photos() } }.onSuccess {
                photos = it
                store.photos = it
            }
            runCatching { withContext(Dispatchers.IO) { server.stories() } }.onSuccess {
                stories = it
                store.stories = it
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
        store.stories = emptyList()
        stories = emptyList()
        deviceKey = null
        library = Library()
        photos = emptyList()
    }

    /** One child's photos and home videos, newest first. */
    fun photosFor(childId: String) = photos.filter { childId in it.children }

    /**
     * One child's photos and home videos in a shuffled order, so the Photos section feels fresh
     * each time it's opened. The same [seed] always gives the same order, and each photo's place
     * comes from its own id, so a photo arriving in a sync slots in without moving the others.
     */
    fun shuffledPhotosFor(childId: String, seed: Long) =
        photosFor(childId).sortedBy { Random(seed xor it.id.hashCode().toLong()).nextLong() }

    /** One child's photos without the videos, for the photo games. */
    fun stillsFor(childId: String) = photosFor(childId).filter { !it.isVideo }

    /** The full address of a photo file; the server wants this TV's key with it ([deviceKey]). */
    fun photoUrl(path: String) = serverUrl.trimEnd('/') + path

    fun chooseSpeakTitles(on: Boolean) {
        speakTitles = on
        store.speakTitles = on
        if (!on) voice.stop()
    }

    /** Reads a menu item's [text] aloud (a 4-year-old can't read it), replacing anything still being said. */
    fun speak(text: String) {
        if (speakTitles) say(text)
    }

    /**
     * Reads [text] aloud even when titles aren't read: the games' questions need it. [onDone] when
     * it's been said; [slow] for stories.
     */
    fun say(text: String, onDone: (() -> Unit)? = null, slow: Boolean = false) {
        voice.say(text, onDone, slow)
    }

    /**
     * The three possible next parts of a child's story, written by the server (Gemini), one for
     * each of [options]; "" for one that didn't come out right. Null when the server can't write
     * stories (no key) or can't be reached: the TV then tells a simple story itself.
     */
    suspend fun storyParts(childId: String, story: List<StoryPart>, options: List<StoryPick>): List<String>? {
        val key = deviceKey ?: return null
        if (!library.stories) return null
        return runCatching {
            withContext(Dispatchers.IO) { Server(serverUrl, key).story(childId, story.map { it.pick.word to it.text }, options.map { it.word }) }
        }.getOrNull()
    }

    /**
     * The photos [childId] can be quizzed on and their questions, from the server (it may write
     * the first ones there and then, so this can take a few seconds). Null when it can't be reached.
     */
    suspend fun photoQuiz(childId: String): List<PhotoQuizPhoto>? {
        val key = deviceKey ?: return null
        if (!library.photoQuiz) return null
        return runCatching { withContext(Dispatchers.IO) { Server(serverUrl, key).photoQuiz(childId) } }.getOrNull()
    }

    fun stopSpeaking() {
        voice.stop()
    }

    fun close() {
        voice.close()
        sounds.close()
        music.close()
    }
}
