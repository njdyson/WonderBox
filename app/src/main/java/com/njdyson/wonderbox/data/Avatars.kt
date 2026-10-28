package com.njdyson.wonderbox.data

import android.content.Context

/**
 * The avatars' pictures, bundled in assets/avatars (Fluent Emoji, the same set the phone app
 * shows). Older Fire TV sticks can't draw newer emoji like 🦄 or 🦖, so the TV never relies on
 * its own emoji font for a face.
 */
object Avatars {
    private var files: Set<String>? = null

    /** The bundled picture for [avatar] as a URI Coil can load, or null if there isn't one. */
    fun uri(context: Context, avatar: String): String? {
        val name = fileName(avatar) ?: return null
        val known = files ?: (context.assets.list("avatars")?.toSet() ?: emptySet()).also { files = it }
        return if (name in known) "file:///android_asset/avatars/$name" else null
    }

    /** An emoji's file name: its code points in hex, without variation selectors ("🦊" is "1f98a.webp"). */
    fun fileName(avatar: String): String? {
        val text = avatar.trim()
        if (text.isEmpty()) return null
        val points = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp != 0xFE0F) points += Integer.toHexString(cp)
            i += Character.charCount(cp)
        }
        return points.joinToString("-") + ".webp"
    }
}
