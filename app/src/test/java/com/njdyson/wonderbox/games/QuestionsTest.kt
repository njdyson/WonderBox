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
                assertTrue(where, q.choices.indices.count { q.isRight(it) } == 1)
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

    /** Every kind of question, with every number range, answer count and word list. */
    private fun customQuestions() = buildList {
        for (type in listOf("count", "add", "sub", "missing", "bigger", "times")) for (max in listOf(5, 10, 20, 50, 100)) for (choices in 2..4) {
            val setup = MathsSetup(types = listOf(type), max = max, tables = listOf(3, 7, 12), choices = choices)
            repeat(60) { add(setup to MathsQuestions.question(setup, random)) }
        }
    }

    @Test
    fun everyMathsSetupGivesFairQuestions() {
        for ((setup, q) in customQuestions()) {
            val where = "$setup: $q"
            assertTrue(where, q.answer in q.choices)
            assertEquals(where, setup.choices, q.choices.size)
            assertEquals(where, q.choices.size, q.choices.distinct().size)
            assertTrue(where, q.choices.all { it.toInt() >= 0 })
            val answer = q.answer.toInt()
            when (setup.types.single()) {
                "count" -> assertTrue(where, answer in 1..10)
                "add", "sub" -> assertTrue(where, answer in 0..setup.max)
                "missing" -> {
                    val (a, _, _, _, total) = q.prompt.split(" ")
                    assertEquals(where, total.toInt(), a.toInt() + answer)
                    assertTrue(where, total.toInt() <= setup.max)
                }
                "bigger" -> assertEquals(where, q.choices.maxOf { it.toInt() }, answer)
                "times" -> assertTrue(where, q.prompt.split(" ")[2].toInt() in setup.tables)
            }
        }
    }

    @Test
    fun spellingUsesTheParentsOwnWords() {
        val words = listOf(MyWord("because", "🤔"), MyWord("friend"), MyWord("rhythmically"))
        for (type in listOf("first", "missing", "pick", "build")) for (choices in 2..4) {
            val setup = SpellingSetup(types = listOf(type), lists = listOf("mine"), choices = choices, myWords = words)
            repeat(100) {
                val q = SpellingQuestions.question(setup, random)
                val where = "$type: $q"
                assertTrue(where, words.any { it.word in q.spoken })
                if (q.build) {
                    assertTrue(where, q.answer.length <= SpellingQuestions.LONGEST_BUILD)
                    assertEquals(where, q.answer.toList().sorted(), q.choices.map { it.single() }.sorted())
                } else {
                    assertTrue(where, q.answer in q.choices)
                    assertEquals(where, q.choices.distinct().size, q.choices.size)
                    assertEquals(where, choices, q.choices.size)
                }
                // The long word is never spelt letter by letter: it's asked another way.
                if (type == "build" && "rhythmically" in q.spoken) assertFalse(where, q.build)
            }
        }
    }

    @Test
    fun eachLevelIsItsOwnPreset() {
        MathsSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        SpellingSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        assertEquals(0, MathsSetup.preset(3).copy(max = 20).level())
        // Changing which tables doesn't matter to a level without times tables.
        assertEquals(3, MathsSetup.preset(3).copy(tables = listOf(7)).level())
        assertEquals(0, SpellingSetup.preset(2).copy(lists = listOf("short", "mine")).level())
        assertEquals(2, SpellingSetup.preset(2).copy(myWords = listOf(MyWord("cat")), on = false).level())
    }

    @Test
    fun quizAnswersAreShuffledButTheRightOneIsKnown() {
        val question = QuizQuestion(
            "Which one says moo?",
            answers = listOf(QuizAnswer("Dog", "🐶"), QuizAnswer("Cow", "/api/wonder/quiz-images/abc"), QuizAnswer("", "🐱")),
            correct = 1,
        )
        val orders = mutableSetOf<List<String>>()
        repeat(50) {
            val q = question.toQuestion(random)
            orders += q.choices
            assertTrue(q.isRight(q.choices.indexOf("Cow")))
            assertEquals(1, q.choices.indices.count { q.isRight(it) })
            assertEquals("/api/wonder/quiz-images/abc", q.choicePictures[q.choices.indexOf("Cow")])
            assertEquals("picture ${q.choices.indexOf("") + 1}", q.describe(q.choices.indexOf("")))
        }
        assertTrue(orders.size > 1)
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
