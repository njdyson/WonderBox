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
    /** A picture for each choice (an emoji or a quiz image's path), for the parent's quizzes. */
    val choicePictures: List<String> = emptyList(),
    /** Which choice is right, when choices aren't told apart by their words. -1: the one that's [answer]. */
    val answerIndex: Int = -1,
    /** What kind of question it is, for the activity log: "add", "first", "quiz"... */
    val kind: String = "",
    /** What it's about ("van", "3 + 2", "count 4"): a round avoids asking the same one again soon. */
    val topic: String = prompt.ifBlank { spoken },
) {
    fun isRight(choice: Int) = if (answerIndex >= 0) choice == answerIndex else choices.getOrNull(choice) == answer

    /** A choice in words, for the activity log. */
    fun describe(choice: Int) = choices.getOrNull(choice)?.ifBlank { null } ?: "picture ${choice + 1}"
}

/** The two made-up games. Levels are 1-based ready-made setups; the phone app sets each child's. */
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
        Maths -> MathsQuestions.question(MathsSetup.preset(level), random)
        Spelling -> SpellingQuestions.question(SpellingSetup.preset(level), random)
    }
}

internal object MathsQuestions {
    // Things to count. Old emoji only, so they show on older Fire TV software.
    private val THINGS = listOf("🍎", "⭐", "🐟", "🎈", "🍓", "🐞", "🚗", "🌸")

    fun question(setup: MathsSetup, random: Random): Question {
        val max = setup.max.coerceIn(5, 100)
        val count = setup.choices.coerceIn(2, 4)
        val type = setup.types.filter { it in TYPES }.ifEmpty { listOf("add") }.random(random)
        val thing = THINGS.random(random)
        // Pictures only while there are few enough things to count.
        val draw = setup.pictures && max <= 10
        return when (type) {
            "count" -> {
                val n = random.nextInt(1, minOf(max, 10) + 1)
                Question("How many?", "How many can you count?", choices(n, count, 1..maxOf(n + 3, 6), random), "$n", thing.repeat(n), kind = type, topic = "count $n")
            }
            "add" -> {
                val a = random.nextInt(1, max)
                val b = random.nextInt(1, max - a + 1)
                Question(
                    "$a + $b", "What is $a plus $b?", choices(a + b, count, 0..max, random), "${a + b}",
                    if (draw) "${thing.repeat(a)} + ${thing.repeat(b)}" else "", kind = type,
                )
            }
            "sub" -> {
                val a = random.nextInt(2, max + 1)
                val b = random.nextInt(1, a)
                Question("$a − $b", "What is $a take away $b?", choices(a - b, count, 0..max, random), "${a - b}", if (draw) thing.repeat(a) else "", kind = type)
            }
            "missing" -> {
                val total = random.nextInt(2, max + 1)
                val a = random.nextInt(1, total)
                Question("$a + _ = $total", "$a plus what makes $total?", choices(total - a, count, 1..max, random), "${total - a}", kind = type)
            }
            "bigger" -> {
                val numbers = (0..max).shuffled(random).take(count)
                Question("", "Which number is the biggest?", numbers.map { "$it" }, "${numbers.max()}", kind = type, topic = "biggest ${numbers.sorted()}")
            }
            else -> {
                val table = setup.tables.filter { it in 1..12 }.ifEmpty { listOf(2) }.random(random)
                val n = random.nextInt(1, 13)
                Question("$n × $table", "What is $n times $table?", choices(n * table, count, 0..144, random), "${n * table}", kind = "times")
            }
        }
    }

    private val TYPES = setOf("count", "add", "sub", "missing", "bigger", "times")

    /** [answer] and [count] - 1 others near it, in a random order. */
    fun choices(answer: Int, count: Int, range: IntRange, random: Random): List<String> {
        val near = (-3..3).map { answer + it }.filter { it != answer && it in range }.shuffled(random)
        // Near the edge of a small range there may not be enough close by.
        val more = (-6..6).map { answer + it }.filter { it != answer && it >= 0 && it !in near }.shuffled(random)
        val others = (near + more).take(count - 1)
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

    /** Words longer than this aren't spelt letter by letter: too many buttons for the screen. */
    const val LONGEST_BUILD = 8

    fun question(setup: SpellingSetup, random: Random): Question {
        val words = buildList {
            if ("short" in setup.lists) addAll(SHORT)
            if ("long" in setup.lists) addAll(LONG)
            if ("mine" in setup.lists) setup.myWords.forEach { add(it.word.lowercase() to it.picture) }
        }.filter { (word, _) -> word.length >= 2 && word.all { it in 'a'..'z' } }.ifEmpty { SHORT }
        val count = setup.choices.coerceIn(2, 4)
        val (word, picture) = words.random(random)
        var type = setup.types.filter { it in TYPES }.ifEmpty { listOf("first") }.random(random)
        if (type == "build" && word.length > LONGEST_BUILD) type = "missing"
        return when (type) {
            "first" -> missing(word, 0, picture, count, random, "Which letter does $word start with?", type)
            // In a three-letter word, the middle one.
            "missing" -> missing(word, if (word.length == 3) 1 else random.nextInt(1, word.length), picture, count, random, "Which letter is missing from $word?", type)
            "pick" -> {
                val wrong = misspellings(word, random).take(count - 1)
                Question("", "Which one spells $word?", (wrong + word).shuffled(random), word, picture, kind = type, topic = word)
            }
            else -> {
                var letters = word.map { "$it" }.shuffled(random)
                // Never already in order: that's no puzzle (unless every letter is the same).
                while (letters.joinToString("") == word && word.toSet().size > 1) letters = letters.shuffled(random)
                Question(word.map { "_" }.joinToString(""), "Spell $word", letters, word, picture, build = true, kind = "build", topic = word)
            }
        }
    }

    private val TYPES = setOf("first", "missing", "pick", "build")

    /** [word] with the letter at [index] hidden, and letters to choose from. */
    private fun missing(word: String, index: Int, picture: String, count: Int, random: Random, spoken: String, kind: String): Question {
        val letter = word[index]
        val pool = if (letter in VOWELS) VOWELS else CONSONANTS
        val others = pool.filter { it != letter }.toList().shuffled(random).take(count - 1)
        val prompt = word.substring(0, index) + "_" + word.substring(index + 1)
        return Question(prompt, "$spoken $word.", (others + letter).shuffled(random).map { "$it" }, "$letter", picture, kind = kind, topic = word)
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
