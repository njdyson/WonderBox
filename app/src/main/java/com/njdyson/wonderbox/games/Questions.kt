package com.njdyson.wonderbox.games

import kotlin.random.Random

/**
 * One question in a game. Every answer is picked from big buttons with the remote, so a
 * 4-year-old never has to type. [spoken] is read aloud, for children who can't read yet.
 */
data class Question(
    /** Shown big, e.g. "3 + 2" or "c_t" (an underscore is the missing letter). */
    val prompt: String,
    val spoken: String,
    val choices: List<String>,
    val answer: String,
    /** Emoji shown above the prompt: things to count, or what the word is. */
    val picture: String = "",
    /** Spell the whole of [answer] by picking its letters in order from [choices]. */
    val build: Boolean = false,
)

/** The two games. Levels are 1-based; the phone app sets each child's level. */
enum class Game(val title: String, val icon: String, val levels: List<String>) {
    Maths(
        "Maths", "🔢",
        listOf(
            "Counting to 5",
            "Adding to 5",
            "Adding to 10",
            "Adding and taking away to 10",
            "Adding and taking away to 20",
            "2, 5 and 10 times tables",
        ),
    ),
    Spelling(
        "Spelling", "🔤",
        listOf(
            "First letter",
            "Middle letter",
            "Missing letter, longer words",
            "Which is spelt right?",
            "Spell the word",
        ),
    ),
    ;

    fun question(level: Int, random: Random = Random): Question = when (this) {
        Maths -> MathsQuestions.question(level.coerceIn(1, levels.size), random)
        Spelling -> SpellingQuestions.question(level.coerceIn(1, levels.size), random)
    }
}

internal object MathsQuestions {
    // Things to count. Old emoji only, so they show on older Fire TV software.
    private val THINGS = listOf("🍎", "⭐", "🐟", "🎈", "🍓", "🐞", "🚗", "🌸")

    fun question(level: Int, random: Random): Question = when (level) {
        1 -> {
            val n = random.nextInt(1, 6)
            Question("How many?", "How many can you count?", choices(n, 3, 1..6, random), "$n", THINGS.random(random).repeat(n))
        }
        2 -> {
            val a = random.nextInt(1, 5)
            val b = random.nextInt(1, 6 - a)
            val thing = THINGS.random(random)
            add(a, b, 3, random, 6, picture = "${thing.repeat(a)} + ${thing.repeat(b)}")
        }
        3 -> {
            val a = random.nextInt(1, 10)
            add(a, random.nextInt(1, 11 - a), 4, random, 10)
        }
        4 -> addOrTakeAway(10, random)
        5 -> addOrTakeAway(20, random)
        else -> {
            val table = listOf(2, 5, 10).random(random)
            val n = random.nextInt(1, 13)
            Question("$n × $table", "What is $n times $table?", choices(n * table, 4, 0..130, random), "${n * table}")
        }
    }

    private fun addOrTakeAway(max: Int, random: Random): Question {
        if (random.nextBoolean()) {
            val a = random.nextInt(1, max)
            return add(a, random.nextInt(1, max - a + 1), 4, random, max)
        }
        val a = random.nextInt(2, max + 1)
        val b = random.nextInt(1, a)
        return Question("$a − $b", "What is $a take away $b?", choices(a - b, 4, 0..max, random), "${a - b}")
    }

    private fun add(a: Int, b: Int, count: Int, random: Random, max: Int = a + b + 3, picture: String = "") =
        Question("$a + $b", "What is $a plus $b?", choices(a + b, count, 0..max, random), "${a + b}", picture)

    /** [answer] and [count] - 1 others near it, in a random order. */
    fun choices(answer: Int, count: Int, range: IntRange, random: Random): List<String> {
        val others = (-3..3).map { answer + it }.filter { it != answer && it in range }.shuffled(random).take(count - 1)
        return (others + answer).shuffled(random).map { "$it" }
    }
}

internal object SpellingQuestions {
    /** Three-letter words, consonant-vowel-consonant, with a picture each. */
    val SHORT = listOf(
        "cat" to "🐱", "dog" to "🐶", "pig" to "🐷", "hen" to "🐔", "bus" to "🚌", "box" to "📦",
        "bat" to "🦇", "bed" to "🛏", "hat" to "🎩", "sun" to "🌞", "van" to "🚐", "web" to "🕸",
        "pen" to "🖊", "map" to "🗺", "fox" to "🦊", "bag" to "👜", "jam" to "🍯", "mug" to "☕",
    )

    /** Four- and five-letter words with a picture each. */
    val LONG = listOf(
        "fish" to "🐟", "frog" to "🐸", "duck" to "🦆", "star" to "⭐", "moon" to "🌙", "cake" to "🎂",
        "tree" to "🌳", "ship" to "🚢", "bell" to "🔔", "ball" to "⚽", "bird" to "🐦", "boat" to "⛵",
        "book" to "📖", "lion" to "🦁", "bear" to "🐻", "crab" to "🦀", "snail" to "🐌", "snake" to "🐍",
        "horse" to "🐴", "sheep" to "🐑", "mouse" to "🐭", "apple" to "🍎", "house" to "🏠", "train" to "🚂",
        "clock" to "🕐", "bread" to "🍞", "shell" to "🐚", "whale" to "🐳", "tiger" to "🐯", "pizza" to "🍕",
    )

    private const val VOWELS = "aeiou"
    private const val CONSONANTS = "bcdfghjklmnprstvwz"

    fun question(level: Int, random: Random): Question = when (level) {
        1 -> {
            val (word, picture) = SHORT.random(random)
            missing(word, 0, picture, 3, random, "Which letter does $word start with?")
        }
        2 -> {
            val (word, picture) = SHORT.random(random)
            missing(word, 1, picture, 3, random, "Which letter is missing from $word?")
        }
        3 -> {
            val (word, picture) = LONG.random(random)
            missing(word, random.nextInt(word.length), picture, 4, random, "Which letter is missing from $word?")
        }
        4 -> {
            val (word, picture) = (SHORT + LONG).random(random)
            val wrong = misspellings(word, random).take(2)
            Question("", "Which one spells $word?", (wrong + word).shuffled(random), word, picture)
        }
        else -> {
            val (word, picture) = LONG.random(random)
            var letters = word.map { "$it" }.shuffled(random)
            // Never already in order: that's no puzzle.
            while (letters.joinToString("") == word) letters = letters.shuffled(random)
            Question(word.map { "_" }.joinToString(""), "Spell $word", letters, word, picture, build = true)
        }
    }

    /** [word] with the letter at [index] hidden, and letters to choose from. */
    private fun missing(word: String, index: Int, picture: String, count: Int, random: Random, spoken: String): Question {
        val letter = word[index]
        val pool = if (letter in VOWELS) VOWELS else CONSONANTS
        val others = pool.filter { it != letter }.toList().shuffled(random).take(count - 1)
        val prompt = word.substring(0, index) + "_" + word.substring(index + 1)
        return Question(prompt, "$spoken $word.", (others + letter).shuffled(random).map { "$it" }, "$letter", picture)
    }

    /** Believable wrong spellings of [word], all different, none of them [word]. */
    fun misspellings(word: String, random: Random): List<String> {
        val tries = mutableListOf<String>()
        // A different vowel.
        word.indices.filter { word[it] in VOWELS }.forEach { i ->
            VOWELS.filter { it != word[i] }.forEach { v -> tries += word.substring(0, i) + v + word.substring(i + 1) }
        }
        // Two letters swapped, a letter doubled, or one missed out.
        for (i in 0 until word.length - 1) tries += word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2)
        for (i in 1 until word.length) tries += word.substring(0, i) + word[i] + word.substring(i)
        for (i in 1 until word.length) tries += word.removeRange(i, i + 1)
        return tries.distinct().filter { it != word }.shuffled(random)
    }
}
