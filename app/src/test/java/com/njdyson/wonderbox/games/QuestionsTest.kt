package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.njdyson.wonderbox.data.Avatars
import org.junit.Test
import java.io.File
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
                // Flags to pick from are pictures, told apart by their names.
                val names = q.choiceNames.ifEmpty { q.choices }
                assertTrue(where, q.answer in names)
                assertEquals(where, names.size, names.distinct().size)
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
        for (type in listOf("count", "add", "sub", "add3", "missing", "bonds", "double", "half", "more", "bigger", "times")) for (max in listOf(5, 10, 20, 50, 100)) for (choices in 2..4) {
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
                "add3" -> {
                    val (a, _, b, _, c) = q.prompt.split(" ")
                    assertEquals(where, a.toInt() + b.toInt() + c.toInt(), answer)
                    assertTrue(where, answer <= setup.max)
                }
                "bonds" -> {
                    val (a, _, _, _, total) = q.prompt.split(" ")
                    assertEquals(where, total.toInt(), a.toInt() + answer)
                    assertTrue(where, total.toInt() <= setup.max && total.toInt() in listOf(5, 10, 20, 100))
                }
                "double" -> assertEquals(where, q.prompt.removePrefix("Double ").toInt() * 2, answer)
                "half" -> assertEquals(where, q.prompt.removePrefix("Half of ").toInt(), answer * 2)
                "more" -> {
                    val (step, words, _, n) = q.prompt.split(" ")
                    assertEquals(where, if (words == "more") n.toInt() + step.toInt() else n.toInt() - step.toInt(), answer)
                    assertTrue(where, answer in 0..setup.max)
                }
                "times" -> assertTrue(where, q.prompt.split(" ")[2].toInt() in setup.tables)
            }
        }
    }

    @Test
    fun spellingUsesTheParentsOwnWords() {
        val words = listOf(MyWord("because", "🤔"), MyWord("friend"), MyWord("rhythmically"))
        for (type in listOf("first", "missing", "pick", "build", "hear", "hearbuild")) for (choices in 2..4) {
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
                if (type.endsWith("build") && "rhythmically" in q.spoken) assertFalse(where, q.build)
                // Spelt from the sound: no picture or letters giving it away.
                if (type.startsWith("hear")) {
                    assertEquals(where, SpellingQuestions.HEAR, q.picture)
                    assertFalse(where, q.answer in q.prompt)
                }
            }
        }
    }

    @Test
    fun eachLevelIsItsOwnPreset() {
        MathsSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        SpellingSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        ClockSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        FlagSetup.PRESETS.forEachIndexed { i, p -> assertEquals(i + 1, p.level()) }
        assertEquals(0, ClockSetup.preset(1).copy(choices = 4).level())
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
        for ((word, _) in SpellingQuestions.SHORT + SpellingQuestions.LONG + SpellingQuestions.BIG) {
            val wrong = SpellingQuestions.misspellings(word, random)
            assertTrue(word, wrong.size >= 2)
            assertFalse(word, word in wrong)
        }
    }

    @Test
    fun everyPictureWordHasItsPictureBundled() {
        val words = PictureWords.SHORT + PictureWords.LONG + PictureWords.BIG
        assertTrue(words.size > 200)
        assertEquals(words.size, words.map { it.first }.toSet().size)
        for ((word, picture) in words) {
            assertTrue(word, File("src/main/assets/avatars/${Avatars.fileName(picture)}").exists())
        }
    }

    @Test
    fun clockTimesAreSaidTheBritishWay() {
        assertEquals("3 o'clock", ClockQuestions.say(3, 0))
        assertEquals("quarter past 3", ClockQuestions.say(3, 15))
        assertEquals("half past 12", ClockQuestions.say(12, 30))
        assertEquals("quarter to 1", ClockQuestions.say(12, 45))
        assertEquals("20 past 7", ClockQuestions.say(7, 20))
        assertEquals("25 to 8", ClockQuestions.say(7, 35))
        assertEquals("1 minute past 2", ClockQuestions.say(2, 1))
        assertEquals("13 minutes to 6", ClockQuestions.say(5, 47))
    }

    @Test
    fun clockQuestionsMatchTheClock() {
        for (type in listOf("oclock", "half", "quarter", "five", "minute")) for (choices in 2..4) {
            val setup = ClockSetup(types = listOf(type), choices = choices)
            repeat(200) {
                val q = ClockQuestions.question(setup, random)
                val where = "$type: $q"
                val (h, m) = ClockQuestions.parse(q.picture)!!
                assertTrue(where, h in 1..12 && m in 0..59)
                assertEquals(where, ClockQuestions.say(h, m), q.answer)
                assertEquals(where, choices, q.choices.size)
                assertEquals(where, choices, q.choices.distinct().size)
                assertTrue(where, q.answer in q.choices)
                when (type) {
                    "oclock" -> assertEquals(where, 0, m)
                    "half" -> assertEquals(where, 30, m)
                    "quarter" -> assertTrue(where, m == 15 || m == 45)
                    "five" -> assertEquals(where, 0, m % 5)
                }
            }
        }
        assertEquals(null, ClockQuestions.parse("🕒"))
    }

    @Test
    fun phonicsWordsHaveSoundButtons() {
        assertEquals(listOf("s", "o", "ck"), Phonics.graphemes("sock"))
        assertEquals(listOf("o", "ff"), Phonics.graphemes("off"))
        assertEquals(listOf("b", "u", "ck", "e", "t"), Phonics.graphemes("bucket"))
        assertEquals(4, Phonics.set("kick"))
        assertEquals(5, Phonics.set("fun"))
        assertEquals(1, Phonics.set("sat"))
        assertEquals(0, Phonics.set("zip"))
        // Every word on Jesse's sheets is there, and every word can be sounded out with Phase 2.
        val sheet = "beg bat of fun bug bit if fig bun bucket off fog kick sick pen met sack pack ten men sock ticket net neck dock pocket rug up pick get peg mum"
        val words = Phonics.PHASE2.map { it.first }
        for (w in sheet.split(" ")) assertTrue(w, w in words)
        for (w in words) assertTrue(w, Phonics.set(w) > 0)
        assertEquals(words.size, words.distinct().size)
        for (type in listOf("first", "missing", "pick", "build", "hear", "hearbuild")) {
            val setup = SpellingSetup(types = listOf(type), lists = listOf("phase2"), choices = 3)
            repeat(100) {
                val q = SpellingQuestions.question(setup, random)
                assertEquals(q.toString(), q.topic, q.sounds.joinToString(""))
                if (q.prompt.isNotEmpty()) assertEquals(q.toString(), q.topic.length, q.prompt.length)
            }
        }
        // Other words get none.
        val other = SpellingQuestions.question(SpellingSetup(types = listOf("pick"), lists = listOf("long")), random)
        assertTrue(other.sounds.isEmpty())
    }

    @Test
    fun flagQuestionsAreFair() {
        val codes = FlagQuestions.COUNTRIES.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        assertTrue(FlagQuestions.COUNTRIES.size > 150)
        for (code in FlagQuestions.UK + FlagQuestions.FAMOUS) assertTrue(code, code in codes)
        for (region in FlagQuestions.REGIONS) assertTrue(region, FlagQuestions.pool(listOf(region)).size >= 4)
        for (region in FlagQuestions.REGIONS) for (type in listOf("name", "flag")) for (choices in 2..4) {
            val setup = FlagSetup(regions = listOf(region), types = listOf(type), choices = choices)
            val pool = FlagQuestions.pool(listOf(region))
            repeat(60) {
                val q = FlagQuestions.question(setup, random)
                val where = "$setup: $q"
                val country = FlagQuestions.COUNTRIES.first { it.code == q.topic }
                assertTrue(where, country in pool)
                assertEquals(where, choices, q.choices.size)
                assertEquals(where, 1, q.choices.indices.count { q.isRight(it) })
                if (type == "flag") {
                    assertEquals(where, country.picture, q.choicePictures[q.answerIndex])
                    assertEquals(where, choices, q.choicePictures.distinct().size)
                    assertEquals(where, country.name, q.describe(q.answerIndex))
                    assertTrue(where, q.choices.all { it.isEmpty() })
                } else {
                    assertEquals(where, country.picture, q.picture)
                    assertEquals(where, choices, q.choices.distinct().size)
                }
                assertTrue(where, q.picture.isEmpty() || q.picture.startsWith("/clipart/flags/"))
            }
        }
    }
}
