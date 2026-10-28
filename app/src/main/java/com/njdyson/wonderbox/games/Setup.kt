package com.njdyson.wonderbox.games

import kotlinx.serialization.Serializable

/**
 * What a child's maths game asks, set in the phone app. Each level is one of these
 * ([preset]); the phone app can change any part. The server checks the values, so they're
 * only coerced here, not validated.
 */
@Serializable
data class MathsSetup(
    val on: Boolean = true,
    /**
     * Any of "count", "add", "sub", "add3" (2 + 3 + 4), "missing" (3 + _ = 5), "bonds"
     * (7 + _ = 10), "double", "half", "more" (1 or 10 more or less), "bigger", "times".
     */
    val types: List<String> = listOf("count"),
    /** The biggest number in a sum or an answer. */
    val max: Int = 5,
    val tables: List<Int> = listOf(2, 5, 10),
    /** How many answers to pick from. */
    val choices: Int = 3,
    /** Things to count drawn above small sums. */
    val pictures: Boolean = true,
    /** "Auto" in the phone app: the level moves up and down with how the child does ([AutoLevel]); the setup is always a level's. */
    val auto: Boolean = false,
) {
    companion object {
        // The same levels as the server (pb_hooks/quizzes_lib.js) and the phone app.
        val PRESETS = listOf(
            MathsSetup(types = listOf("count"), max = 5, choices = 3, pictures = true),
            MathsSetup(types = listOf("add"), max = 5, choices = 3, pictures = true),
            MathsSetup(types = listOf("add"), max = 10, choices = 4, pictures = false),
            MathsSetup(types = listOf("add", "sub"), max = 10, choices = 4, pictures = false),
            MathsSetup(types = listOf("add", "sub"), max = 20, choices = 4, pictures = false),
            MathsSetup(types = listOf("times"), max = 10, tables = listOf(2, 5, 10), choices = 4, pictures = false),
        )

        fun preset(level: Int) = PRESETS[level.coerceIn(1, PRESETS.size) - 1]
    }

    /** The level this is, or 0 if the parent has changed it from every level. */
    fun level(): Int {
        fun MathsSetup.key() = listOf(types, max, if ("times" in types) tables else emptyList(), choices, pictures)
        return PRESETS.indexOfFirst { it.key() == key() } + 1
    }
}

/** What a child's spelling game asks, and the parent's own words for it. */
@Serializable
data class SpellingSetup(
    val on: Boolean = true,
    /**
     * Any of "first" (letter), "missing" (letter), "pick" (which is spelt right), "build" (spell
     * the word), and "hear" and "hearbuild": pick or spell a word that's only said, not shown.
     */
    val types: List<String> = listOf("first"),
    /** Which words: "short" (3 letters), "long" (4 and 5), "big" (6 to 8), "phase2" (phonics, with sound buttons), "mine" ([myWords]). */
    val lists: List<String> = listOf("short"),
    val choices: Int = 3,
    val myWords: List<MyWord> = emptyList(),
    /** "Auto" in the phone app: the level moves up and down with how the child does ([AutoLevel]); the setup is always a level's. */
    val auto: Boolean = false,
) {
    companion object {
        val PRESETS = listOf(
            SpellingSetup(types = listOf("first"), lists = listOf("short"), choices = 3),
            SpellingSetup(types = listOf("missing"), lists = listOf("short"), choices = 3),
            SpellingSetup(types = listOf("missing"), lists = listOf("long"), choices = 4),
            SpellingSetup(types = listOf("pick"), lists = listOf("short", "long"), choices = 3),
            SpellingSetup(types = listOf("build"), lists = listOf("long"), choices = 4),
        )

        fun preset(level: Int) = PRESETS[level.coerceIn(1, PRESETS.size) - 1]
    }

    fun level(): Int {
        if ("mine" in lists) return 0
        fun SpellingSetup.key() = listOf(types, lists, if (types.all { it == "build" }) 0 else choices)
        return PRESETS.indexOfFirst { it.key() == key() } + 1
    }
}

/** What a child's clock game asks: which times to read, and how many answers to pick from. */
@Serializable
data class ClockSetup(
    val on: Boolean = true,
    /** Any of "oclock", "half" (past), "quarter" (past and to), "five" (minutes), "minute" (any). */
    val types: List<String> = listOf("oclock"),
    val choices: Int = 3,
    /** "Auto" in the phone app: the level moves up and down with how the child does ([AutoLevel]); the setup is always a level's. */
    val auto: Boolean = false,
) {
    companion object {
        val PRESETS = listOf(
            ClockSetup(types = listOf("oclock"), choices = 3),
            ClockSetup(types = listOf("oclock", "half"), choices = 3),
            ClockSetup(types = listOf("oclock", "half", "quarter"), choices = 4),
            ClockSetup(types = listOf("five"), choices = 4),
            ClockSetup(types = listOf("minute"), choices = 4),
        )

        fun preset(level: Int) = PRESETS[level.coerceIn(1, PRESETS.size) - 1]
    }

    fun level() = PRESETS.indexOfFirst { it.types == types && it.choices == choices } + 1
}

/** What a child's flag game asks: which flags, which way round, and how many answers. */
@Serializable
data class FlagSetup(
    val on: Boolean = true,
    /** Any of "uk" (the UK's nations and Ireland), "famous", "europe", "asia", "africa", "americas", "oceania". */
    val regions: List<String> = listOf("uk"),
    /** "name" (see a flag, pick the country) and "flag" (hear a country, pick its flag). */
    val types: List<String> = listOf("name"),
    val choices: Int = 3,
    /** "Auto" in the phone app: the level moves up and down with how the child does ([AutoLevel]); the setup is always a level's. */
    val auto: Boolean = false,
) {
    companion object {
        val PRESETS = listOf(
            FlagSetup(regions = listOf("uk"), types = listOf("name"), choices = 3),
            FlagSetup(regions = listOf("uk", "famous"), types = listOf("name"), choices = 3),
            FlagSetup(regions = listOf("uk", "famous"), types = listOf("name", "flag"), choices = 4),
            FlagSetup(regions = listOf("europe"), types = listOf("name", "flag"), choices = 4),
            FlagSetup(regions = listOf("europe", "asia", "africa", "americas", "oceania"), types = listOf("name", "flag"), choices = 4),
        )

        fun preset(level: Int) = PRESETS[level.coerceIn(1, PRESETS.size) - 1]
    }

    fun level() = PRESETS.indexOfFirst { it.regions == regions && it.types == types && it.choices == choices } + 1
}

/** One of the parent's spelling words. [picture] is an emoji or a quiz image's path, or empty. */
@Serializable
data class MyWord(val word: String, val picture: String = "")

/** A quiz the parent wrote in the phone app. */
@Serializable
data class Quiz(
    val id: String,
    val title: String = "",
    /** An emoji or a quiz image's path. */
    val icon: String = "",
    val children: List<String> = emptyList(),
    /** Ask the questions in a different order each time. */
    val shuffle: Boolean = true,
    val questions: List<QuizQuestion> = emptyList(),
)

@Serializable
data class QuizQuestion(
    val text: String = "",
    val picture: String = "",
    val answers: List<QuizAnswer> = emptyList(),
    /** Which of [answers] is right. */
    val correct: Int = 0,
)

@Serializable
data class QuizAnswer(val text: String = "", val picture: String = "")
