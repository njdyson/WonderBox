package com.njdyson.wonderbox.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * What the TV keeps between launches: its device key, the last library it fetched (so the
 * app opens straight to the children's videos, even offline), and the grown-ups' settings.
 * Thumbnails are cached on disk by Coil.
 */
class Store(context: Context) {
    private companion object {
        const val MAX_WATCHED = 2000
    }

    private val prefs = context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE)

    var deviceKey: String?
        get() = prefs.getString("device_key", null)
        set(value) = prefs.edit().apply { if (value == null) remove("device_key") else putString("device_key", value) }.apply()

    var library: Library?
        get() = prefs.getString("library", null)?.let { runCatching { json.decodeFromString(Library.serializer(), it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove("library") else putString("library", json.encodeToString(Library.serializer(), value))
        }.apply()

    /** The last photo list fetched; the pictures themselves are cached on disk by Coil. */
    var stories: List<SavedStory>
        get() = prefs.getString("stories", null)?.let { runCatching { json.decodeFromString(SavedStories.serializer(), it).stories }.getOrNull() }.orEmpty()
        set(value) = prefs.edit().putString("stories", json.encodeToString(SavedStories.serializer(), SavedStories(value))).apply()

    var photos: List<Photo>
        get() = prefs.getString("photos", null)?.let { runCatching { json.decodeFromString(Photos.serializer(), it).photos }.getOrNull() }.orEmpty()
        set(value) = prefs.edit().putString("photos", json.encodeToString(Photos.serializer(), Photos(value))).apply()

    /** Treats each child has earned in games and not yet picked, by child ID. */
    var treats: Map<String, Int>
        get() = prefs.getString("treats", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("treats", json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), value)).apply()

    /** Treats each child has picked and not watched yet, by child ID: [Video.id]s, oldest first. */
    var savedTreats: Map<String, List<String>>
        get() = prefs.getString("saved_treats", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("saved_treats", json.encodeToString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), value)).apply()

    /** YouTube IDs each child has watched all the way through, by child ID, oldest first. */
    var watched: Map<String, List<String>>
        get() = prefs.getString("watched", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("watched", json.encodeToString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), value)).commit().let {}

    /**
     * Marks [youtubeId] watched all the way through by [childId]; true if it's the first time.
     * Called from the player, so it writes straight away for the main screen to read on its return.
     */
    fun markWatched(childId: String, youtubeId: String): Boolean {
        val mine = watched[childId].orEmpty()
        if (youtubeId in mine) return false
        watched = watched + (childId to (mine + youtubeId).takeLast(MAX_WATCHED))
        return true
    }

    /** One more won treat for [childId], from the player (the main screen's [treats] are reloaded on its return). */
    fun addTreat(childId: String) {
        val now = treats
        prefs.edit().putString("treats", json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), now + (childId to (now[childId] ?: 0) + 1))).commit()
    }

    /** How the last rounds went at each auto-level game's level, by "<child>/<game>" (games/AutoLevel.kt). */
    var autoHistory: Map<String, String>
        get() = prefs.getString("auto_history", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("auto_history", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)).apply()

    /** Read video titles aloud when they're focused. */
    var speakTitles: Boolean
        get() = prefs.getBoolean("speak_titles", true)
        set(value) = prefs.edit().putBoolean("speak_titles", value).apply()
}
