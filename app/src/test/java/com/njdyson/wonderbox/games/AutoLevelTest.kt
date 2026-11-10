package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.games.AutoLevel.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoLevelTest {
    @Test
    fun judgingARound() {
        // 10 right answers: one slip is still easy.
        assertEquals(Result.Easy, AutoLevel.quiz(10, 1, 9))
        assertEquals(Result.Ok, AutoLevel.quiz(10, 3, 7))
        assertEquals(Result.Hard, AutoLevel.quiz(10, 5, 5))
        // Guessing from 3 or 4 answers (about a third right first time) is hard, not too hard.
        assertEquals(Result.Hard, AutoLevel.quiz(10, 12, 3))
        assertEquals(Result.Hard, AutoLevel.quiz(10, 15, 2))
        // Under 20% right first time is too hard.
        assertEquals(Result.TooHard, AutoLevel.quiz(10, 16, 1))
        // A short round has no room for slips.
        assertEquals(Result.Ok, AutoLevel.quiz(5, 1, 4))
        assertEquals(Result.Easy, AutoLevel.unjumble(3, 0, 1))
        assertEquals(Result.Hard, AutoLevel.unjumble(3, 1, 4))
        assertEquals(Result.TooHard, AutoLevel.unjumble(3, 2, 6))
        assertEquals(Result.Easy, AutoLevel.makeWords(gotLongest = true, skips = 0))
        assertEquals(Result.Ok, AutoLevel.makeWords(gotLongest = false, skips = 0))
        assertEquals(Result.Hard, AutoLevel.makeWords(gotLongest = false, skips = 2))
    }

    @Test
    fun steppingUpAndDown() {
        // Two easy rounds in a row go up, and the history starts again.
        assertEquals(3 to "E", AutoLevel.step(3, 6, "", Result.Easy))
        assertEquals(4 to "", AutoLevel.step(3, 6, "E", Result.Easy))
        assertEquals(3 to "OE", AutoLevel.step(3, 6, "EO", Result.Easy))
        // Only two too-hard rounds in a row go down: hard rounds (or guessing) stay put.
        assertEquals(3 to "HH", AutoLevel.step(3, 6, "H", Result.Hard))
        assertEquals(3 to "HT", AutoLevel.step(3, 6, "H", Result.TooHard))
        assertEquals(3 to "T", AutoLevel.step(3, 6, "", Result.TooHard))
        assertEquals(2 to "", AutoLevel.step(3, 6, "T", Result.TooHard))
        // Never past the first or last level.
        assertEquals(6 to "EE", AutoLevel.step(6, 6, "E", Result.Easy))
        assertEquals(1 to "TT", AutoLevel.step(1, 6, "T", Result.TooHard))
    }

    @Test
    fun onlyGamesOnAuto() {
        val settings = GameSettings(maths = 2, mathsSetup = MathsSetup.preset(2).copy(auto = true), scramble = ScrambleSetup(level = 4, auto = true))
        assertEquals(2 to 6, settings.autoLevel("maths"))
        assertEquals(4 to 5, settings.autoLevel(SCRAMBLE))
        assertNull(settings.autoLevel("spelling"))
        assertNull(settings.autoLevel("quiz/q1"))
        val up = settings.withLevel("maths", 3).withLevel(SCRAMBLE, 5)
        assertEquals(3, up.maths().level())
        assertEquals(3 to 6, up.autoLevel("maths"))
        assertEquals(5, up.scramble.level)
        // Spelling on auto keeps the parent's own words for the other games.
        val words = GameSettings(spellingSetup = SpellingSetup.preset(1).copy(auto = true, myWords = listOf(MyWord("said"))))
        assertEquals(listOf(MyWord("said")), words.withLevel("spelling", 2).spelling().myWords)
        assertEquals(listOf("missing"), words.withLevel("spelling", 2).spelling().types)
    }
}
