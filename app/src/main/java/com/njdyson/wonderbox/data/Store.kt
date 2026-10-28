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
    var photos: List<Photo>
        get() = prefs.getString("photos", null)?.let { runCatching { json.decodeFromString(Photos.serializer(), it).photos }.getOrNull() }.orEmpty()
        set(value) = prefs.edit().putString("photos", json.encodeToString(Photos.serializer(), Photos(value))).apply()

    /** Treats each child has earned in games and not yet picked, by child ID. */
    var treats: Map<String, Int>
        get() = prefs.getString("treats", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("treats", json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), value)).apply()

    /** This TV's star changes the server hasn't counted yet (see [Points]). */
    var pointsChanges: List<PointsChange>
        get() = prefs.getString("points_changes", null)?.let {
            runCatching { json.decodeFromString(ListSerializer(PointsChange.serializer()), it) }.getOrNull()
        }.orEmpty()
        set(value) = prefs.edit().putString("points_changes", json.encodeToString(ListSerializer(PointsChange.serializer()), value)).commit().let {}

    /** How long videos are, in seconds, by YouTube ID. */
    var durations: Map<String, Int>
        get() = prefs.getString("durations", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("durations", json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), value)).apply()

    /** The day ("2026-10-28") and how many stars [childId] has earned from learning videos on it, for the daily cap. */
    fun videoPoints(childId: String): Pair<String, Int> =
        (prefs.getString("video_points_day_$childId", "") ?: "") to prefs.getInt("video_points_$childId", 0)

    fun setVideoPoints(childId: String, day: String, stars: Int) {
        prefs.edit().putString("video_points_day_$childId", day).putInt("video_points_$childId", stars).apply()
    }

    /** Old treats (won or saved, from before stars), turned into stars once; cleared after. */
    fun takeOldTreats(): Map<String, Int> {
        val won = treats
        val saved = savedTreats.mapValues { it.value.size }
        val all = (won.keys + saved.keys).associateWith { (won[it] ?: 0) + (saved[it] ?: 0) }.filterValues { it > 0 }
        prefs.edit().remove("treats").remove("saved_treats").commit()
        return all
    }

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

    /** Read video titles aloud when they're focused. */
    var speakTitles: Boolean
        get() = prefs.getBoolean("speak_titles", true)
        set(value) = prefs.edit().putBoolean("speak_titles", value).apply()
}
