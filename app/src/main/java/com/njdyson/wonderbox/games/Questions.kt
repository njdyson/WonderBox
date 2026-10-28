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
    /** Emoji shown above the prompt (things to count, or what the word is), or a clock: "clock:3:45". */
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

/** The made-up games. Levels are 1-based ready-made setups; the phone app sets each child's. */
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
    Clock(
        "Clock", "🕒",
        listOf(
            "O'clock",
            "O'clock and half past",
            "Quarter past and quarter to",
            "Five minutes",
            "Any minute",
        ),
    ),
    ;

    fun question(level: Int, random: Random = Random): Question = when (this) {
        Maths -> MathsQuestions.question(MathsSetup.preset(level), random)
        Spelling -> SpellingQuestions.question(SpellingSetup.preset(level), random)
        Clock -> ClockQuestions.question(ClockSetup.preset(level), random)
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
            "add3" -> {
                val a = random.nextInt(1, max - 1)
                val b = random.nextInt(1, max - a)
                val c = random.nextInt(1, max - a - b + 1)
                val sum = a + b + c
                Question(
                    "$a + $b + $c", "What is $a plus $b plus $c?", choices(sum, count, 0..max, random), "$sum",
                    if (draw) "${thing.repeat(a)} + ${thing.repeat(b)} + ${thing.repeat(c)}" else "", kind = type,
                )
            }
            "bonds" -> {
                // Number bonds to 5, 10 or 20, and to 100 in tens.
                val target = listOf(5, 10, 20, 100).filter { it <= max && (it >= 10 || max < 10) }.random(random)
                val a = if (target == 100) random.nextInt(1, 10) * 10 else random.nextInt(0, target + 1)
                val range = if (target == 100) 0..100 else 0..target
                val answers = if (target == 100) choicesBy10(target - a, count, random) else choices(target - a, count, range, random)
                Question("$a + _ = $target", "What goes with $a to make $target?", answers, "${target - a}", kind = type)
            }
            "double" -> {
                val n = random.nextInt(1, maxOf(2, max / 2) + 1)
                Question("Double $n", "What is double $n?", choices(n * 2, count, 0..max, random), "${n * 2}", if (draw) "${thing.repeat(n)} ${thing.repeat(n)}" else "", kind = type)
            }
            "half" -> {
                val n = random.nextInt(1, maxOf(2, max / 2) + 1) * 2
                Question("Half of $n", "What is half of $n?", choices(n / 2, count, 0..max, random), "${n / 2}", if (draw) thing.repeat(n) else "", kind = type)
            }
            "more" -> {
                val step = if (max >= 20 && random.nextBoolean()) 10 else 1
                val more = random.nextBoolean()
                val n = if (more) random.nextInt(0, max - step + 1) else random.nextInt(step, max + 1)
                val answer = if (more) n + step else n - step
                val words = if (more) "more" else "less"
                val answers = if (step == 10) choicesBy10(answer, count, random, n) else choices(answer, count, 0..max, random)
                Question("$step $words than $n", "What is ${if (step == 1) "one" else "ten"} $words than $n?", answers, "$answer", kind = type)
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

    private val TYPES = setOf("count", "add", "sub", "add3", "missing", "bonds", "double", "half", "more", "bigger", "times")

    /** [answer] and others off by tens (and ones), for sums in tens; [also] is a likely mistake. */
    private fun choicesBy10(answer: Int, count: Int, random: Random, also: Int? = null): List<String> {
        val tries = (listOfNotNull(also) + listOf(answer + 10, answer - 10, answer + 1, answer - 1, answer + 20, answer - 20, answer + 2, answer - 2))
            .filter { it >= 0 && it != answer }.distinct()
        // The likely mistake first, then the rest in any order.
        val others = (tries.take(if (also != null) 1 else 0) + tries.drop(if (also != null) 1 else 0).shuffled(random)).take(count - 1)
        return (others + answer).shuffled(random).map { "$it" }
    }

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
        if (type == "hearbuild" && word.length > LONGEST_BUILD) type = "hear"
        return when (type) {
            "first" -> missing(word, 0, picture, count, random, "Which letter does $word start with?", type)
            // In a three-letter word, the middle one.
            "missing" -> missing(word, if (word.length == 3) 1 else random.nextInt(1, word.length), picture, count, random, "Which letter is missing from $word?", type)
            "pick", "hear" -> {
                val wrong = misspellings(word, random).take(count - 1)
                // "hear": only said, not shown, so it's spelt from the sound.
                val shown = if (type == "hear") HEAR else picture
                Question("", "Which one spells $word?", (wrong + word).shuffled(random), word, shown, kind = type, topic = word)
            }
            else -> {
                var letters = word.map { "$it" }.shuffled(random)
                // Never already in order: that's no puzzle (unless every letter is the same).
                while (letters.joinToString("") == word && word.toSet().size > 1) letters = letters.shuffled(random)
                val shown = if (type == "hearbuild") HEAR else picture
                Question(word.map { "_" }.joinToString(""), "Spell $word", letters, word, shown, build = true, kind = type, topic = word)
            }
        }
    }

    private val TYPES = setOf("first", "missing", "pick", "build", "hear", "hearbuild")

    /** Shown instead of the word's picture when it's spelt from the sound. */
    const val HEAR = "👂"

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

/** Reading the time on a clock face, said the British way: "quarter past 3", "20 to 5". */
internal object ClockQuestions {
    private val TYPES = setOf("oclock", "half", "quarter", "five", "minute")

    fun question(setup: ClockSetup, random: Random): Question {
        val types = setup.types.filter { it in TYPES }.ifEmpty { listOf("oclock") }
        val count = setup.choices.coerceIn(2, 4)
        val type = types.random(random)
        val hour = random.nextInt(1, 13)
        val minute = minuteFor(type, random)
        val answer = say(hour, minute)
        val others = linkedSetOf<String>()
        // The mistakes children make: the hour next to it, past and to mixed up, the hands swapped.
        val likely = buildList {
            add(hour % 12 + 1 to minute)
            add((hour + 10) % 12 + 1 to minute)
            if (minute != 0 && minute != 30) add(hour to 60 - minute)
            if (minute % 5 == 0 && minute in 5..55 && minute / 5 != hour) add(minute / 5 to hour * 5 % 60)
        }.shuffled(random)
        for ((h, m) in likely) if (others.size < count - 1) others += say(h, m)
        var tries = 0
        while (others.size < count - 1 && tries++ < 50) {
            val other = say(random.nextInt(1, 13), minuteFor(types.random(random), random))
            if (other != answer) others += other
        }
        return Question(
            prompt = "",
            spoken = "What time does the clock say?",
            choices = (others.take(count - 1) + answer).shuffled(random),
            answer = answer,
            picture = "clock:$hour:$minute",
            kind = type,
            topic = "clock $hour:$minute",
        )
    }

    private fun minuteFor(type: String, random: Random) = when (type) {
        "oclock" -> 0
        "half" -> 30
        "quarter" -> if (random.nextBoolean()) 15 else 45
        "five" -> random.nextInt(0, 12) * 5
        else -> random.nextInt(0, 60)
    }

    /** The time in words, as said in Britain. */
    fun say(hour: Int, minute: Int): String {
        val next = hour % 12 + 1
        val minutes = { m: Int -> if (m % 5 == 0) "$m" else if (m == 1) "1 minute" else "$m minutes" }
        return when {
            minute == 0 -> "$hour o'clock"
            minute == 15 -> "quarter past $hour"
            minute == 30 -> "half past $hour"
            minute == 45 -> "quarter to $next"
            minute < 30 -> "${minutes(minute)} past $hour"
            else -> "${minutes(60 - minute)} to $next"
        }
    }

    /** "clock:3:45" as (3, 45), or null if [picture] isn't a clock. */
    fun parse(picture: String): Pair<Int, Int>? {
        val parts = picture.split(":")
        if (parts.size != 3 || parts[0] != "clock") return null
        val h = parts[1].toIntOrNull() ?: return null
        val m = parts[2].toIntOrNull() ?: return null
        return h to m
    }
}
