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
        val sam = Child(
            "sam", "Sam",
            games = GameSettings(maths = 2, spellingSetup = SpellingSetup(on = false), hangman = HangmanSetup(on = false), memory = MemorySetup(on = false)),
        )
        val jo = Child("jo", "Jo")
        val library = Library(children = listOf(sam, jo), quizzes = listOf(quiz, quiz.copy(id = "q2", children = listOf("jo"))))
        assertEquals(listOf("maths", "quiz/q1"), library.gamesFor(sam).map { it.key })
        assertEquals("Level 2", library.gamesFor(sam)[0].subtitle)
        assertEquals("3 questions", library.gamesFor(sam)[1].subtitle)
        assertEquals(listOf("maths", "spelling", "hangman", "memory", "quiz/q2"), library.gamesFor(jo).map { it.key })
        assertEquals(listOf("maths", "spelling", "hangman", "jigsaw", "memory", "quiz/q2"), library.gamesFor(jo, photos = true).map { it.key })
    }

    @Test
    fun easyGamesDoNotWinTreatsUnlessTheParentSaysSo() {
        val games = GameSettings()
        assertEquals(listOf(true, true, true, true, false, false), listOf("maths", "spelling", "hangman", "quiz/q1", "memory", "jigsaw").map(games::earnsTreat))
        assertEquals(true, GameSettings(treatGames = listOf("memory")).earnsTreat("memory"))
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

    @Test
    fun aRoundDoesNotRepeatItself() {
        // Level 1 spelling has 18 words: ten in a row are all different, round after round.
        val child = Child("jesse", "Jesse", games = GameSettings(spelling = 1, roundSize = 10))
        val entry = Library().gamesFor(child).first { it.key == "spelling" }
        repeat(5) { r ->
            val round = entry.newRound(child, Random(r))
            val words = List(10) { round.next().topic }
            assertEquals(words.toString(), 10, words.toSet().size)
        }
    }

    @Test
    fun withFewToChooseFromItTakesTurns() {
        val words = listOf(MyWord("said"), MyWord("was"), MyWord("the"))
        val child = Child("sam", "Sam", games = GameSettings(spellingSetup = SpellingSetup(types = listOf("first"), lists = listOf("mine"), myWords = words)))
        val round = Library().gamesFor(child).first { it.key == "spelling" }.newRound(child, Random(3))
        val asked = List(12) { round.next().topic }
        asked.windowed(3).forEach { assertEquals(asked.toString(), 3, it.toSet().size) }
        // Counting to 5: five different numbers before any comes back.
        val counter = Child("c", "C", games = GameSettings(maths = 1))
        val counting = Library().gamesFor(counter).first().newRound(counter, Random(4))
        val counts = List(15) { counting.next().answer }
        counts.windowed(5).forEach { assertEquals(counts.toString(), 5, it.toSet().size) }
    }
}
