package com.njdyson.wonderbox.games

import android.content.Context
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The word game's [GameEntry.key]; it has its own screen (WordGameScreen). */
const val SCRAMBLE = "scramble"

/**
 * A child's word game, set in the phone app. Levels 1 and 2 put a jumbled word back together
 * (the word said aloud and its picture shown, unless [say] is off); level 1 only takes the
 * letters in the right order, so a 4-year-old can't get in a muddle. Levels 3 to 5 are
 * Scrabble-like: make as many words as you can from 5, 6 or 7 letters, with a bonus star for
 * the longest.
 */
@Serializable
data class ScrambleSetup(
    val on: Boolean = true,
    val level: Int = 1,
    /** For levels 1 and 2: "phase2" (phonics), "short" (3 letters), "long" (4 and 5), "big" (6 to 8), "mine" (the spelling game's own words). */
    val lists: List<String> = listOf("phase2"),
    /** Jumbled words to put right to finish a game (levels 1 and 2). */
    val words: Int = 3,
    val say: Boolean = true,
) {
    val makeWords get() = level >= 3

    /** Letters to make words from (levels 3 to 5). */
    val letters get() = (level.coerceIn(3, LEVELS) + 2)

    /** Words to find to finish a game: [words] jumbled ones, or a few made from the letters. */
    val goal get() = if (makeWords) MAKE_GOALS[letters] ?: 4 else words.coerceIn(1, 10)

    companion object {
        const val LEVELS = 5
        private val MAKE_GOALS = mapOf(5 to 4, 6 to 6, 7 to 8)
    }
}

/**
 * The words the game knows, from assets/words (tools/make_words.py): [known] are ones a young
 * child knows, used for the puzzles; [more] are other everyday words that count when found.
 */
class WordList(val known: Set<String>, val more: Set<String> = emptySet()) {
    fun isWord(word: String) = word in known || word in more

    /** The known words (3 letters or more) that can be made from [letters], longest first. */
    fun knownFrom(letters: List<Char>): List<String> =
        known.filter { canMake(it, letters) }.sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    companion object {
        @Volatile private var loaded: WordList? = null

        fun load(context: Context): WordList = loaded ?: synchronized(this) {
            loaded ?: WordList(read(context, "words/known.txt"), read(context, "words/more.txt")).also { loaded = it }
        }

        private fun read(context: Context, path: String): Set<String> = runCatching {
            context.assets.open(path).bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
        }.getOrDefault(emptySet())

        /** Whether [word] can be spelt from [letters], each used once. */
        fun canMake(word: String, letters: List<Char>): Boolean {
            val left = letters.toMutableList()
            return word.all { left.remove(it) }
        }
    }
}

/**
 * One jumbled word to put right. Letters are picked from [tiles] (by position, as a word can
 * have the same letter twice) into the gaps from the left. With [inOrder] only the next right
 * letter goes in; otherwise the gaps fill up and then it's checked, the right start kept.
 */
class Unjumble(val word: String, val picture: String, val tiles: List<Char>, val inOrder: Boolean) {
    /** Which [tiles] are in the gaps, in order. */
    val placed = mutableListOf<Int>()
    /** Tries that filled every gap but weren't the word. */
    var misses = 0
        private set

    enum class Pick { Placed, Solved, Wrong, Missed, Again }

    val solved get() = placed.size == word.length && built() == word

    fun built() = placed.joinToString("") { "${tiles[it]}" }

    fun used(tile: Int) = tile in placed

    /** The tile the child should pick next (a hint), or null when the gaps so far aren't right. */
    fun nextTile(): Int? {
        val done = built()
        if (!word.startsWith(done) || done.length >= word.length) return null
        return tiles.indices.firstOrNull { it !in placed && tiles[it] == word[done.length] }
    }

    fun pick(tile: Int): Pick {
        if (solved || tile !in tiles.indices) return Pick.Again
        if (used(tile)) {
            // Picking a letter already in the gaps takes it out again (and those after it).
            takeOut(placed, tile)
            return Pick.Again
        }
        if (inOrder && tiles[tile] != word[placed.size]) return Pick.Wrong
        placed += tile
        if (placed.size < word.length) return Pick.Placed
        if (solved) return Pick.Solved
        misses++
        // Keep the right start; the rest go back.
        val right = word.zip(built()).takeWhile { (a, b) -> a == b }.size
        while (placed.size > right) placed.removeAt(placed.lastIndex)
        return Pick.Missed
    }

    /** Takes the last letter out of the gaps. */
    fun back() {
        if (!solved && placed.isNotEmpty()) placed.removeAt(placed.lastIndex)
    }

    companion object {
        /** The words for [setup]: the ready-made lists and the parent's own spelling words, 3 to 8 letters. */
        fun words(setup: ScrambleSetup, myWords: List<MyWord>): List<Pair<String, String>> = buildList {
            if ("phase2" in setup.lists) addAll(Phonics.PHASE2)
            if ("short" in setup.lists) addAll(SpellingQuestions.SHORT)
            if ("long" in setup.lists) addAll(SpellingQuestions.LONG)
            if ("big" in setup.lists) addAll(SpellingQuestions.BIG)
            if ("mine" in setup.lists) myWords.forEach { add(it.word.lowercase() to it.picture) }
        }.filter { (word, _) -> word.length in 3..8 && word.all { it in 'a'..'z' } && word.toSet().size > 1 }
            .distinctBy { it.first }
            .ifEmpty { SpellingQuestions.SHORT }

        /** [word] with its letters jumbled, never already in order. */
        fun new(word: String, picture: String, setup: ScrambleSetup, random: Random = Random): Unjumble =
            Unjumble(word, picture, jumble(word.toList(), word, random), inOrder = setup.level <= 1)
    }
}

/**
 * Scrabble without the board: make as many words as you can from [letters]. [goal] words
 * finishes it; finding a word that uses every letter ([longest]) earns a bonus.
 */
class MakeWords(val letters: List<Char>, val goal: Int, private val words: WordList) {
    /** Which [letters] are in the word being made, in order. */
    val placed = mutableListOf<Int>()
    val found = mutableListOf<String>()

    /** The known words these letters make, longest first: the ones to show the child at the end. */
    val possible = words.knownFrom(letters)

    /** A known word that uses every letter. */
    val longest get() = possible.firstOrNull { it.length == letters.size } ?: possible.firstOrNull().orEmpty()

    val gotLongest get() = found.any { it.length >= longest.length }
    val done get() = found.size >= goal

    sealed interface Check {
        data class Found(val word: String) : Check
        data class Again(val word: String) : Check
        data class NotAWord(val word: String) : Check
        data object TooShort : Check
    }

    fun built() = placed.joinToString("") { "${letters[it]}" }

    fun used(tile: Int) = tile in placed

    /** Adds a letter to the word, or takes it (and those after it) out if it's already in. */
    fun pick(tile: Int): Boolean {
        if (tile !in letters.indices) return false
        if (used(tile)) {
            takeOut(placed, tile)
            return false
        }
        placed += tile
        return true
    }

    fun back() {
        if (placed.isNotEmpty()) placed.removeAt(placed.lastIndex)
    }

    /** Checks the word made so far; the letters go back either way, except for a short one. */
    fun check(): Check {
        val word = built()
        if (word.length < 3) return Check.TooShort
        placed.clear()
        return when {
            word in found -> Check.Again(word)
            words.isWord(word) -> Check.Found(word).also { found += word }
            else -> Check.NotAWord(word)
        }
    }

    companion object {
        /**
         * New letters: the letters of a known word [size] long that makes at least [goal] known
         * words, jumbled. A recent word is skipped if there's another (see [RecentTopics]).
         */
        fun new(words: WordList, size: Int, goal: Int, random: Random = Random, recent: RecentTopics? = null): MakeWords {
            val sizes = (size downTo 3)
            for (n in sizes) {
                val seeds = words.known.filter { it.length == n }.sorted()
                    .filter { words.knownFrom(it.toList()).size >= goal }
                if (seeds.isEmpty()) continue
                val seed = recent?.fresh({ seeds.random(random) }) { it } ?: seeds.random(random)
                return MakeWords(jumble(seed.toList(), seed, random), goal, words)
            }
            // No word list (it's missing from the APK): still something to play with.
            val fallback = WordList(SpellingQuestions.SHORT.map { it.first }.toSet())
            return MakeWords(jumble("cat".toList(), "cat", random), 1, fallback)
        }
    }
}

/** Takes [tile] out of [placed], with any after it. */
private fun takeOut(placed: MutableList<Int>, tile: Int) {
    val at = placed.indexOf(tile)
    if (at >= 0) while (placed.size > at) placed.removeAt(placed.lastIndex)
}

/** [letters] shuffled so they don't spell [word] (unless every letter is the same). */
private fun jumble(letters: List<Char>, word: String, random: Random): List<Char> {
    var out = letters.shuffled(random)
    var tries = 0
    while (out.joinToString("") == word && letters.toSet().size > 1 && tries++ < 20) out = letters.shuffled(random)
    return out
}
