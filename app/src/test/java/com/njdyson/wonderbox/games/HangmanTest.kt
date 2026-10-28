package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HangmanTest {
    @Test
    fun theLettersToPickFromHoldTheWordsAndSomeOthers() {
        repeat(20) { seed ->
            val game = Hangman.new("frog", "🐸", HangmanSetup(letters = 8), Random(seed))
            assertEquals(8, game.letters.size)
            assertTrue(game.letters.containsAll("frog".toList()))
            assertEquals(game.letters.sorted(), game.letters)
        }
        // A word with many different letters still gets two that aren't in it.
        assertEquals(10, Hangman.new("crayfish", "", HangmanSetup(letters = 8), Random(1)).letters.size)
        assertEquals(('a'..'z').toList(), Hangman.new("cat", "", HangmanSetup(letters = 26), Random(1)).letters)
    }

    @Test
    fun rightLettersFillInAndWrongOnesPopBalloons() {
        val game = Hangman("bell", "🔔", listOf('b', 'e', 'l', 'x', 'z'), balloons = 2)
        assertEquals(Hangman.Guess.Right, game.guess('l'))
        assertEquals("__ll", game.shown())
        assertEquals(Hangman.Guess.Again, game.guess('l'))
        assertEquals(Hangman.Guess.Wrong, game.guess('x'))
        assertEquals(1, game.left)
        assertEquals(Hangman.Guess.Right, game.guess('b'))
        assertEquals(Hangman.Guess.Right, game.guess('e'))
        assertTrue(game.solved)
        assertFalse(game.lost)
        // Once it's over, nothing more counts.
        assertEquals(Hangman.Guess.Again, game.guess('z'))
        assertEquals(1, game.left)
    }

    @Test
    fun runningOutOfBalloonsEndsTheWord() {
        val game = Hangman("cat", "🐱", listOf('a', 'c', 't', 'x', 'z'), balloons = 2)
        game.guess('x')
        game.guess('z')
        assertTrue(game.lost)
        assertTrue(game.over)
        assertEquals(Hangman.Guess.Again, game.guess('c'))
    }

    @Test
    fun wordsComeFromTheChosenLists() {
        val mine = listOf(MyWord("Said"), MyWord("it's"), MyWord("because", "/api/wonder/quiz-images/x"))
        val words = Hangman.words(HangmanSetup(lists = listOf("mine")), mine)
        assertEquals(listOf("said" to "", "because" to "/api/wonder/quiz-images/x"), words)
        // Nothing usable: the short words.
        assertEquals(SpellingQuestions.SHORT, Hangman.words(HangmanSetup(lists = listOf("mine")), emptyList()))
    }
}
