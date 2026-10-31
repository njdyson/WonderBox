package com.njdyson.wonderbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PicturesTest {
    @Test
    fun cutsEmojiFromText() {
        assertEquals(listOf("🦩"), emojiPieces("🦩"))
        assertEquals(listOf("🍎", "🍎", " ", "+", " ", "🍎"), emojiPieces("🍎🍎 + 🍎"))
        // Variation selectors, skin tones and joined emoji stay with their emoji.
        assertEquals(listOf("⬆️", "a"), emojiPieces("⬆️a"))
        assertEquals(listOf("👋🏽"), emojiPieces("👋🏽"))
        assertEquals(listOf("👩‍🚀", "!"), emojiPieces("👩‍🚀!"))
    }
}
