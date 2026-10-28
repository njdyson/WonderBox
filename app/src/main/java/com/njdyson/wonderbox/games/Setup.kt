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
    /** Any of "count", "add", "sub", "missing" (3 + _ = 5), "bigger", "times". */
    val types: List<String> = listOf("count"),
    /** The biggest number in a sum or an answer. */
    val max: Int = 5,
    val tables: List<Int> = listOf(2, 5, 10),
    /** How many answers to pick from. */
    val choices: Int = 3,
    /** Things to count drawn above small sums. */
    val pictures: Boolean = true,
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
    /** Any of "first" (letter), "missing" (letter), "pick" (which is spelt right), "build" (spell the word). */
    val types: List<String> = listOf("first"),
    /** Which words: "short" (3 letters), "long" (4 and 5), "mine" ([myWords]). */
    val lists: List<String> = listOf("short"),
    val choices: Int = 3,
    val myWords: List<MyWord> = emptyList(),
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
