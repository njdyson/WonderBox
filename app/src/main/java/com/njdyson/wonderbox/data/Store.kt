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

    /** Treats each child has picked and not watched yet, by child ID: [Video.id]s, oldest first. */
    var savedTreats: Map<String, List<String>>
        get() = prefs.getString("saved_treats", null)?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit().putString("saved_treats", json.encodeToString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), value)).apply()

    /** Read video titles aloud when they're focused. */
    var speakTitles: Boolean
        get() = prefs.getBoolean("speak_titles", true)
        set(value) = prefs.edit().putBoolean("speak_titles", value).apply()
}
