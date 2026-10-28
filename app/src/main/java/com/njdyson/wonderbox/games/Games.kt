package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import kotlin.random.Random

/** The photo jigsaw's [GameEntry.key]; it has its own screen (JigsawScreen), not rounds of questions. */
const val JIGSAW = "jigsaw"

/**
 * A game on a child's screen: maths, spelling (unless the parent turned it off), a photo
 * jigsaw or one of the parent's quizzes for them. [key] is also the activity log's subject:
 * "maths", "spelling" or "quiz/<id>" ([JIGSAW] logs its own events).
 */
data class GameEntry(
    val key: String,
    val title: String,
    /** An emoji or a quiz image's path. */
    val icon: String,
    /** "Level 3", "Your own mix", "6 questions": shown under the title. */
    val subtitle: String,
    val game: Game? = null,
    val quiz: Quiz? = null,
) {
    /** A new round to play: [Round.goal] right answers wins it. */
    fun newRound(child: Child, random: Random = Random): Round {
        quiz?.let { return quizRound(it, random) }
        val roundSize = child.games.roundSize.coerceIn(1, 50)
        return if (game == Game.Spelling) {
            val setup = child.games.spelling()
            Round(roundSize, setup.level()) { SpellingQuestions.question(setup, random) }
        } else {
            val setup = child.games.maths()
            Round(roundSize, setup.level()) { MathsQuestions.question(setup, random) }
        }
    }

    private fun quizRound(quiz: Quiz, random: Random): Round {
        val questions = quiz.questions.filter { it.answers.size >= 2 }
        val order = if (quiz.shuffle) questions.shuffled(random) else questions
        var next = 0
        return Round(order.size.coerceAtLeast(1), 0) {
            order[next++ % order.size].toQuestion(random)
        }
    }
}

/** Questions one after another until [goal] are answered right. [level] is 0 for a parent's own setup or quiz. */
class Round(val goal: Int, val level: Int, private val nextQuestion: () -> Question) {
    fun next() = nextQuestion()
}

/** A quiz question for [GameScreen]'s buttons, its answers in a random order. */
fun QuizQuestion.toQuestion(random: Random = Random): Question {
    val order = answers.indices.shuffled(random)
    return Question(
        prompt = text,
        spoken = text,
        choices = order.map { answers[it].text },
        answer = answers.getOrNull(correct)?.text.orEmpty(),
        picture = picture,
        choicePictures = order.map { answers[it].picture },
        answerIndex = order.indexOf(correct.coerceIn(answers.indices)),
        kind = "quiz",
    )
}

/**
 * The games on [child]'s screen, in order: maths, spelling, a jigsaw (when there are
 * [photos] and the child has jigsaws on), then the parent's quizzes.
 */
fun Library.gamesFor(child: Child, photos: Boolean = false): List<GameEntry> = buildList {
    val maths = child.games.maths()
    if (maths.on) add(GameEntry("maths", Game.Maths.title, Game.Maths.icon, subtitle(maths.level()), Game.Maths))
    val spelling = child.games.spelling()
    if (spelling.on) add(GameEntry("spelling", Game.Spelling.title, Game.Spelling.icon, subtitle(spelling.level()), Game.Spelling))
    if (photos && child.games.jigsaw > 0) {
        val size = child.games.jigsaw.coerceIn(JIGSAW_SIZES.first, JIGSAW_SIZES.last)
        add(GameEntry(JIGSAW, "Jigsaw", "🧩", "$size × $size pieces"))
    }
    quizzes.filter { child.id in it.children && it.questions.any { q -> q.answers.size >= 2 } }.forEach { quiz ->
        val count = quiz.questions.size
        add(GameEntry("quiz/${quiz.id}", quiz.title.ifBlank { "Quiz" }, quiz.icon.ifBlank { "❓" }, "$count question${if (count == 1) "" else "s"}", quiz = quiz))
    }
}

private fun subtitle(level: Int) = if (level > 0) "Level $level" else "Your own mix"
