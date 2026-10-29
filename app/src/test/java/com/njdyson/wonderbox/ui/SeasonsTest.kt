package com.njdyson.wonderbox.ui

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.GameSettings
import java.io.File
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonsTest {
    private fun day(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
    }

    @Test fun byTheDate() {
        assertNull(Season.on(day(2026, 10, 16)))
        assertEquals(Season.Halloween, Season.on(day(2026, 10, 17)))
        assertEquals(Season.Halloween, Season.on(day(2026, 10, 31)))
        assertEquals(Season.Halloween, Season.on(day(2026, 11, 1)))
        assertEquals(Season.Bonfire, Season.on(day(2026, 11, 5)))
        assertNull(Season.on(day(2026, 11, 7)))
        assertEquals(Season.Christmas, Season.on(day(2026, 12, 1)))
        assertEquals(Season.Christmas, Season.on(day(2027, 1, 5)))
        assertNull(Season.on(day(2027, 1, 6)))
        assertEquals(Season.Valentine, Season.on(day(2027, 2, 14)))
        // Easter Sunday is 28 March 2027 and 16 April 2028: two weeks up to it and the Monday after.
        assertNull(Season.on(day(2027, 3, 14)))
        assertEquals(Season.Easter, Season.on(day(2027, 3, 15)))
        assertEquals(Season.Easter, Season.on(day(2027, 3, 29)))
        assertNull(Season.on(day(2027, 3, 30)))
        assertEquals(Season.Easter, Season.on(day(2028, 4, 10)))
        assertNull(Season.on(day(2027, 7, 1)))
    }

    @Test fun settings() {
        val summer = day(2026, 7, 1)
        assertNull(Season.of("auto", summer))
        assertEquals(Season.Christmas, Season.of("christmas", summer))
        assertNull(Season.of("off", day(2026, 10, 31)))
        assertEquals(Season.Halloween, Season.of("nonsense", day(2026, 10, 31)))
    }

    @Test fun family() {
        fun kid(season: String) = Child("c", "C", games = GameSettings(season = season))
        val halloween = day(2026, 10, 30)
        assertEquals(Season.Halloween, Season.forFamily(emptyList(), halloween))
        assertNull(Season.forFamily(listOf(kid("off"), kid("off")), halloween))
        assertEquals(Season.Halloween, Season.forFamily(listOf(kid("off"), kid("auto")), halloween))
        assertEquals(Season.Christmas, Season.forFamily(listOf(kid("christmas"), kid("christmas")), halloween))
    }

    /** Each tune makes a whole loop, not too loud, and is saved as a .wav to listen to. */
    @Test fun tunes() {
        val dir = File("build/music").apply { mkdirs() }
        for (tune in Music.Tune.entries) {
            val pcm = Music.render(tune)
            assertTrue("${tune.name} is ${pcm.size / 22_050}s", pcm.size > 22_050 * 20)
            assertTrue(pcm.all { it in -1f..1f })
            writeWav(File(dir, "${tune.name.lowercase()}.wav"), pcm, loops = 2)
        }
    }

    private fun writeWav(file: File, pcm: FloatArray, loops: Int) {
        val samples = pcm.size * loops
        val out = java.io.ByteArrayOutputStream()
        fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray()); int(36 + samples * 2); out.write("WAVEfmt ".toByteArray())
        int(16); short(1); short(1); int(22_050); int(44_100); short(2); short(16)
        out.write("data".toByteArray()); int(samples * 2)
        repeat(loops) { for (s in pcm) short((s * 0.8f * Short.MAX_VALUE).toInt()) }
        file.writeBytes(out.toByteArray())
    }
}
