package com.njdyson.wonderbox.data

import android.content.Context

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

    /** Read video titles aloud when they're focused. */
    var speakTitles: Boolean
        get() = prefs.getBoolean("speak_titles", true)
        set(value) = prefs.edit().putBoolean("speak_titles", value).apply()
}
