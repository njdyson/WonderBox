package com.njdyson.wonderbox.games

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The photo quiz's [GameEntry.key]; it has its own screen (PhotoQuizScreen). */
const val PHOTO_QUIZ = "photoquiz"

/**
 * A child's photo quiz, set in the phone app: how old they are (which of each photo's
 * questions they get), how many seconds they look at the photo, how many questions follow,
 * and who picks the photo: "child" or "surprise" (the TV does). Off until the parent turns it
 * on, since the server sends the photos to Gemini to write the questions.
 */
@Serializable
data class PhotoQuizSetup(
    val on: Boolean = false,
    val age: Int = 5,
    val look: Int = 10,
    val questions: Int = 4,
    val pick: String = "child",
) {
    val surprise get() = pick == "surprise"
}

/** A question Gemini wrote about a photo: [level] 1 (easy) to 3, the [right] answer and some [wrong] ones. */
@Serializable
data class PhotoQuizQuestion(
    val level: Int = 1,
    val text: String = "",
    val right: String = "",
    val wrong: List<String> = emptyList(),
)

/** A photo that can be quizzed on (its id in the photos list) and its questions. */
@Serializable
data class PhotoQuizPhoto(val photo: String, val questions: List<PhotoQuizQuestion> = emptyList())

@Serializable
data class PhotoQuizPhotos(val photos: List<PhotoQuizPhoto> = emptyList())

object PhotoQuiz {
    /** The questions for a child of [age]: 1 (3 and 4), 2 (5 and 6) or 3 (7 and up), as the server sorts them. */
    fun levelFor(age: Int) = when {
        age <= 4 -> 1
        age <= 6 -> 2
        else -> 3
    }

    /**
     * [count] of [questions] for a child of [age]: their own level's first, then the nearest
     * ones (easier before harder), each lot in a random order.
     */
    fun pick(questions: List<PhotoQuizQuestion>, age: Int, count: Int, random: Random = Random): List<PhotoQuizQuestion> {
        val level = levelFor(age)
        val usable = questions.filter { it.text.isNotBlank() && it.right.isNotBlank() && it.wrong.isNotEmpty() }
        return usable.groupBy { it.level }
            .entries
            .sortedWith(compareBy({ kotlin.math.abs(it.key - level) }, { it.key }))
            .flatMap { it.value.shuffled(random) }
            .take(count.coerceAtLeast(1))
    }

    /** How many answers to choose from: 3 for the youngest, up to 4 for older children. */
    fun choicesFor(age: Int) = if (age <= 6) 3 else 4

    /**
     * [q] as a question for [com.njdyson.wonderbox.ui.GameScreen]'s buttons: its answers in a
     * random order, colour words with a patch of their colour. Read aloud with its answers, for
     * children who can't read yet.
     */
    fun toQuestion(q: PhotoQuizQuestion, age: Int, random: Random = Random): Question {
        val choices = (listOf(q.right) + q.wrong.filter { it != q.right }.shuffled(random).take(choicesFor(age) - 1)).shuffled(random)
        val swatches = choices.map { colourOf(it) }
        val pictures = if (swatches.all { it != null }) swatches.map { "color:$it" } else emptyList()
        return Question(
            prompt = q.text,
            spoken = "${q.text} ${spokenList(choices)}?",
            choices = choices.map { it.replaceFirstChar(Char::uppercaseChar) },
            answer = q.right.replaceFirstChar(Char::uppercaseChar),
            choicePictures = pictures,
            kind = "quiz",
            topic = q.text,
        )
    }

    /** "red, blue or green". */
    fun spokenList(choices: List<String>) = when (choices.size) {
        0 -> ""
        1 -> choices[0]
        else -> choices.dropLast(1).joinToString(", ") + " or " + choices.last()
    }

    // The colours an answer can be, for its patch.
    private val COLOURS = mapOf(
        "red" to "#E53935", "orange" to "#FB8C00", "yellow" to "#FDD835", "green" to "#43A047",
        "blue" to "#1E88E5", "light blue" to "#81D4FA", "dark blue" to "#1A3A8F", "navy" to "#1A3A8F", "purple" to "#8E24AA",
        "pink" to "#F06292", "brown" to "#795548", "black" to "#212121", "white" to "#FFFFFF",
        "grey" to "#9E9E9E", "gray" to "#9E9E9E", "gold" to "#FFC107", "silver" to "#C0C0C0",
        "dark green" to "#1B5E20", "light green" to "#AED581", "cream" to "#FFF3D6", "beige" to "#E8D5B0",
        "turquoise" to "#26C6DA",
    )

    /** The colour (as #RRGGBB) an answer like "red" or "light blue" names, or null. */
    fun colourOf(answer: String): String? = COLOURS[answer.trim().lowercase()]
}
