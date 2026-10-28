package com.njdyson.wonderbox.games

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Hangman's [GameEntry.key]; it has its own screen (HangmanScreen). */
const val HANGMAN = "hangman"

/**
 * A child's hangman, set in the phone app. There's no gallows: the child holds a bunch of
 * [balloons] and each wrong letter pops one. Letters are picked from a small set on screen
 * ([letters] of them, or the whole alphabet), so a 4-year-old isn't hunting through 26.
 */
@Serializable
data class HangmanSetup(
    val on: Boolean = true,
    /** Which words: "short" (3 letters), "long" (4 and 5), "big" (6 to 8), "mine" (the spelling game's own words). */
    val lists: List<String> = listOf("short"),
    /** Letters to pick from, the word's own among them; 26 is the whole alphabet. */
    val letters: Int = 8,
    val balloons: Int = 6,
    /** Words to find to finish a game. */
    val words: Int = 3,
    /** Say the word and show its picture, so it's spelling from the sound. Off: a guessing game. */
    val say: Boolean = true,
) {
    companion object {
        val LETTERS = listOf(8, 12, 26)
        val BALLOONS = listOf(4, 6, 8)
    }
}

/** One word of hangman: which letters have been tried, and how many balloons are left. */
class Hangman(val word: String, val picture: String, val letters: List<Char>, val balloons: Int) {
    private val tried = mutableSetOf<Char>()
    var wrong = 0
        private set

    enum class Guess { Right, Wrong, Again }

    val left get() = (balloons - wrong).coerceAtLeast(0)
    val solved get() = word.all { it in tried }
    val lost get() = !solved && left == 0
    val over get() = solved || lost

    fun tried(letter: Char) = letter in tried

    /** The word with the letters not found yet as "_". */
    fun shown(): String = word.map { if (it in tried) it else '_' }.joinToString("")

    fun guess(letter: Char): Guess {
        if (over || !tried.add(letter)) return Guess.Again
        return if (letter in word) Guess.Right else Guess.Wrong.also { wrong++ }
    }

    companion object {
        /** The words for [setup]: the ready-made lists and the parent's own spelling words. */
        fun words(setup: HangmanSetup, myWords: List<MyWord>): List<Pair<String, String>> = buildList {
            if ("short" in setup.lists) addAll(SpellingQuestions.SHORT)
            if ("long" in setup.lists) addAll(SpellingQuestions.LONG)
            if ("big" in setup.lists) addAll(SpellingQuestions.BIG)
            if ("mine" in setup.lists) myWords.forEach { add(it.word.lowercase() to it.picture) }
        }.filter { (word, _) -> word.length in 2..12 && word.all { it in 'a'..'z' } }.ifEmpty { SpellingQuestions.SHORT }

        /**
         * A word to find, with the letters to pick from: all of the word's, then others to make
         * up [HangmanSetup.letters] (always at least two that aren't in it), in ABC order.
         */
        fun new(word: String, picture: String, setup: HangmanSetup, random: Random = Random): Hangman {
            val own = word.toSet()
            val count = setup.letters.coerceIn(own.size + 2, 26)
            val others = ('a'..'z').filter { it !in own }.shuffled(random).take(count - own.size)
            return Hangman(word, picture, (own + others).sorted(), setup.balloons.coerceIn(1, 10))
        }
    }
}
