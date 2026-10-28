package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.data.Library
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    private val quiz = Quiz(
        "q1", "Animals", "🐮", listOf("sam"), shuffle = false,
        questions = listOf(
            QuizQuestion("One", answers = listOf(QuizAnswer("a"), QuizAnswer("b"))),
            QuizQuestion("Two", answers = listOf(QuizAnswer("c"), QuizAnswer("d")), correct = 1),
            QuizQuestion("Broken", answers = listOf(QuizAnswer("only one"))),
        ),
    )

    @Test
    fun gamesOnAChildsScreen() {
        val sam = Child("sam", "Sam", games = GameSettings(maths = 2, spellingSetup = SpellingSetup(on = false)))
        val jo = Child("jo", "Jo")
        val library = Library(children = listOf(sam, jo), quizzes = listOf(quiz, quiz.copy(id = "q2", children = listOf("jo"))))
        assertEquals(listOf("maths", "quiz/q1"), library.gamesFor(sam).map { it.key })
        assertEquals("Level 2", library.gamesFor(sam)[0].subtitle)
        assertEquals("3 questions", library.gamesFor(sam)[1].subtitle)
        assertEquals(listOf("maths", "spelling", "quiz/q2"), library.gamesFor(jo).map { it.key })
    }

    @Test
    fun aQuizRoundAsksEveryQuestionInOrder() {
        val entry = Library(quizzes = listOf(quiz)).gamesFor(Child("sam", "Sam")).last()
        val round = entry.newRound(Child("sam", "Sam"), Random(1))
        assertEquals(2, round.goal)
        assertEquals(listOf("One", "Two", "One"), List(3) { round.next().prompt })
    }

    @Test
    fun mathsRoundsFollowTheSetup() {
        val child = Child("sam", "Sam", games = GameSettings(roundSize = 4, mathsSetup = MathsSetup(types = listOf("times"), tables = listOf(7))))
        val round = Library().gamesFor(child).first().newRound(child, Random(2))
        assertEquals(4, round.goal)
        assertEquals(0, round.level)
        repeat(20) { assertEquals("7", round.next().prompt.split(" ")[2]) }
    }
}
