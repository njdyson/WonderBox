package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionsTest {
    private val random = Random(42)

    /** Every level of every game, many times over. */
    private fun everyQuestion() = Game.entries.flatMap { game ->
        (1..game.levels.size).flatMap { level -> List(300) { Triple(game, level, game.question(level, random)) } }
    }

    @Test
    fun theAnswerIsAlwaysAChoiceAndChoicesAreDifferent() {
        for ((game, level, q) in everyQuestion()) {
            val where = "$game level $level: $q"
            if (q.build) {
                assertEquals(where, q.answer.toList().sorted(), q.choices.map { it.single() }.sorted())
                assertTrue(where, q.choices.joinToString("") != q.answer)
            } else {
                assertTrue(where, q.answer in q.choices)
                assertEquals(where, q.choices.size, q.choices.distinct().size)
                assertTrue(where, q.choices.size >= 3)
            }
            assertTrue(where, q.spoken.isNotBlank())
        }
    }

    @Test
    fun mathsStaysWithinEachLevel() {
        for ((game, level, q) in everyQuestion().filter { it.first == Game.Maths }) {
            val answer = q.answer.toInt()
            val where = "level $level: $q"
            when (level) {
                1 -> assertTrue(where, answer in 1..5 && q.picture.isNotEmpty())
                2 -> assertTrue(where, answer in 2..5 && "+" in q.prompt)
                3 -> assertTrue(where, answer in 2..10)
                4 -> assertTrue(where, answer in 0..10)
                5 -> assertTrue(where, answer in 0..20)
                6 -> assertTrue(where, "×" in q.prompt)
            }
            assertTrue(where, q.choices.all { it.toInt() >= 0 })
            if (game == Game.Maths && level <= 2) assertEquals(where, 3, q.choices.size)
        }
    }

    @Test
    fun sumsAreRight() {
        for ((_, _, q) in everyQuestion().filter { it.first == Game.Maths && it.third.prompt != "How many?" }) {
            val parts = q.prompt.split(" ")
            val a = parts[0].toInt()
            val b = parts[2].toInt()
            val expected = when (parts[1]) {
                "+" -> a + b
                "−" -> a - b
                else -> a * b
            }
            assertEquals(q.toString(), "$expected", q.answer)
        }
    }

    @Test
    fun missingLetterFillsTheGap() {
        for ((_, level, q) in everyQuestion().filter { it.first == Game.Spelling && it.second <= 3 }) {
            val word = q.prompt.replace("_", q.answer)
            assertTrue("level $level: $q", word in q.spoken)
            if (level == 1) assertTrue(q.prompt.startsWith("_"))
            if (level == 2) assertEquals(1, q.prompt.indexOf('_'))
        }
    }

    @Test
    fun misspellingsAreNeverTheWord() {
        for ((word, _) in SpellingQuestions.SHORT + SpellingQuestions.LONG) {
            val wrong = SpellingQuestions.misspellings(word, random)
            assertTrue(word, wrong.size >= 2)
            assertFalse(word, word in wrong)
        }
    }
}
