package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class WordGameTest {
    private val words = WordList(
        known = setOf("plant", "plan", "pan", "ant", "tan", "nap", "pat", "tap", "lap", "cat", "planet", "plane", "net", "ten", "pen"),
        more = setOf("apt", "pant"),
    )

    @Test
    fun aJumbledWordIsNeverAlreadyInOrder() {
        repeat(30) { seed ->
            val game = Unjumble.new("frog", "🐸", ScrambleSetup(level = 2), Random(seed))
            assertNotEquals("frog", game.tiles.joinToString(""))
            assertEquals("fgor", game.tiles.sorted().joinToString(""))
        }
    }

    @Test
    fun levelOneOnlyTakesTheNextLetter() {
        val game = Unjumble("cat", "🐱", listOf('t', 'c', 'a'), inOrder = true)
        assertEquals(Unjumble.Pick.Wrong, game.pick(0))
        assertEquals(1, game.nextTile())
        assertEquals(Unjumble.Pick.Placed, game.pick(1))
        assertEquals(Unjumble.Pick.Placed, game.pick(2))
        assertEquals(Unjumble.Pick.Solved, game.pick(0))
        assertTrue(game.solved)
        assertEquals(Unjumble.Pick.Again, game.pick(0))
    }

    @Test
    fun aWrongTryKeepsTheRightStart() {
        val game = Unjumble("frog", "", listOf('g', 'o', 'r', 'f'), inOrder = false)
        listOf(3, 2, 0).forEach { assertEquals(Unjumble.Pick.Placed, game.pick(it)) }
        assertEquals(Unjumble.Pick.Missed, game.pick(1))
        assertEquals("fr", game.built())
        assertEquals(1, game.misses)
        assertEquals(1, game.nextTile())
        // Picking a placed letter again takes it (and those after it) back out.
        game.pick(3)
        assertEquals("", game.built())
        assertNull(Unjumble("dog", "", listOf('o', 'g', 'd'), false).apply { pick(0) }.nextTile())
    }

    @Test
    fun wordsComeFromTheChosenListsAndAreLongEnough() {
        val phonics = Unjumble.words(ScrambleSetup(lists = listOf("phase2")), emptyList())
        assertTrue(phonics.all { it.first.length >= 3 })
        assertFalse(phonics.any { it.first == "up" || it.first == "if" })
        assertTrue(phonics.any { it.first == "sock" })
        val mine = Unjumble.words(ScrambleSetup(lists = listOf("mine")), listOf(MyWord("Summer", "☀"), MyWord("ox")))
        assertEquals(listOf("summer" to "☀"), mine)
    }

    @Test
    fun makingWordsFromLetters() {
        val round = MakeWords("plant".toList(), goal = 3, words)
        assertEquals("plant", round.longest)
        fun make(word: String): MakeWords.Check {
            word.forEach { c -> round.pick(round.letters.indices.first { round.letters[it] == c && !round.used(it) }) }
            return round.check()
        }
        assertEquals(MakeWords.Check.Found("pan"), make("pan"))
        assertEquals(MakeWords.Check.Again("pan"), make("pan"))
        assertEquals(MakeWords.Check.NotAWord("lpa"), make("lpa"))
        // A word that isn't on the kid list still counts.
        assertEquals(MakeWords.Check.Found("pant"), make("pant"))
        round.pick(0)
        assertEquals(MakeWords.Check.TooShort, round.check())
        round.back()
        assertFalse(round.done)
        assertFalse(round.gotLongest)
        assertEquals(MakeWords.Check.Found("plant"), make("plant"))
        assertTrue(round.done)
        assertTrue(round.gotLongest)
        // "cat" can't be made: there's no c.
        assertFalse("cat" in round.possible)
    }

    @Test
    fun newLettersMakeEnoughKnownWords() {
        repeat(10) { seed ->
            val round = MakeWords.new(words, size = 5, goal = 7, random = Random(seed))
            assertEquals("alnpt", round.letters.sorted().joinToString(""))
            assertTrue(round.possible.size >= 7)
        }
        // Six letters: only "planet" makes enough.
        assertEquals("aelnpt", MakeWords.new(words, 6, 6, Random(3)).letters.sorted().joinToString(""))
    }

    @Test
    fun theBundledListsMakeGoodPuzzles() {
        val dir = listOf(File("src/main/assets/words"), File("app/src/main/assets/words")).first { it.isDirectory }
        val list = WordList(File(dir, "known.txt").readLines().toSet(), File(dir, "more.txt").readLines().toSet())
        assertTrue(list.known.size > 500 && list.more.size > 3000)
        for (word in listOf("sex", "kill", "dead", "bloody", "crap")) assertFalse(word, list.isWord(word))
        for (word in listOf("colour", "cat", "planet")) assertTrue(word, list.isWord(word))
        for ((size, goal) in listOf(5 to 4, 6 to 6, 7 to 8)) {
            val seeds = list.known.filter { it.length == size && list.knownFrom(it.toList()).size >= goal }
            assertTrue("$size letters: ${seeds.size}", seeds.size >= 30)
        }
    }

    @Test
    fun levelsAndGoals() {
        assertFalse(ScrambleSetup(level = 2).makeWords)
        assertEquals(3, ScrambleSetup(level = 1, words = 3).goal)
        assertEquals(5, ScrambleSetup(level = 3).letters)
        assertEquals(4, ScrambleSetup(level = 3).goal)
        assertEquals(8, ScrambleSetup(level = 5).goal)
    }
}
