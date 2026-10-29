package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhotoQuizTest {
    private val questions = listOf(
        PhotoQuizQuestion(1, "What colour was the dress?", "red", listOf("blue", "green")),
        PhotoQuizQuestion(1, "Was there a dog?", "yes", listOf("no")),
        PhotoQuizQuestion(2, "What was the boy holding?", "a kite", listOf("a ball", "a book")),
        PhotoQuizQuestion(2, "Where was the cat?", "on the sofa", listOf("in a box", "under the table")),
        PhotoQuizQuestion(3, "How many windows were there?", "six", listOf("four", "five", "eight")),
        PhotoQuizQuestion(3, "No answers to pick from?", "six", emptyList()),
    )

    @Test
    fun agesGoInThreeLevels() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 3), (3..9).map(PhotoQuiz::levelFor))
    }

    @Test
    fun picksTheChildsOwnLevelFirstThenTheNearest() {
        val five = PhotoQuiz.pick(questions, age = 5, count = 4, random = Random(1))
        assertEquals(listOf(2, 2, 1, 1), five.map { it.level })
        val four = PhotoQuiz.pick(questions, age = 4, count = 3, random = Random(1))
        assertEquals(listOf(1, 1, 2), four.map { it.level })
        // Easier before harder when both are as near.
        val eight = PhotoQuiz.pick(questions, age = 8, count = 5, random = Random(1))
        assertEquals(listOf(3, 2, 2, 1, 1), eight.map { it.level })
        assertFalse("a question without wrong answers is left out", eight.any { it.wrong.isEmpty() })
    }

    @Test
    fun youngerChildrenGetThreeAnswersAndTheyAreReadOut() {
        val q = PhotoQuiz.toQuestion(questions[4], age = 5, random = Random(2))
        assertEquals(3, q.choices.size)
        assertTrue("Six" in q.choices)
        assertEquals("Six", q.choices[q.choices.indexOf(q.answer)])
        assertTrue(q.spoken.startsWith("How many windows were there? "))
        assertTrue(q.spoken.endsWith("?") && " or " in q.spoken)
        assertEquals(4, PhotoQuiz.toQuestion(questions[4], age = 8).choices.size)
        assertEquals(2, PhotoQuiz.toQuestion(questions[1], age = 8).choices.size)
    }

    @Test
    fun colourAnswersGetAPatchOfTheirColour() {
        val q = PhotoQuiz.toQuestion(questions[0], age = 5, random = Random(3))
        assertEquals(q.choices.map { "color:" + PhotoQuiz.colourOf(it) }, q.choicePictures)
        assertEquals("#E53935", PhotoQuiz.colourOf("Red"))
        assertEquals("#81D4FA", PhotoQuiz.colourOf(" light blue "))
        assertNull(PhotoQuiz.colourOf("a kite"))
        // Not every answer a colour: no patches.
        assertTrue(PhotoQuiz.toQuestion(questions[2], age = 5).choicePictures.isEmpty())
    }

    @Test
    fun saysTheAnswersLikeAPerson() {
        assertEquals("red, blue or green", PhotoQuiz.spokenList(listOf("red", "blue", "green")))
        assertEquals("yes or no", PhotoQuiz.spokenList(listOf("yes", "no")))
    }

    @Test
    fun isOffUntilTheParentTurnsItOn() {
        val library = json.decodeFromString(Library.serializer(), """{"children": [{"id": "c1", "name": "Sam"}]}""")
        assertFalse(library.children[0].games.photoQuiz.on)
        assertFalse(library.photoQuiz)
        val on = json.decodeFromString(
            Library.serializer(),
            """{"photoQuiz": true, "children": [{"id": "c1", "name": "Sam", "games": {"photoQuiz": {"on": true, "age": 7, "look": 15, "questions": 5, "pick": "surprise"}}}]}""",
        )
        val setup = on.children[0].games.photoQuiz
        assertTrue(on.photoQuiz && setup.on && setup.surprise)
        assertEquals(15, setup.look)
    }

    @Test
    fun showsOnlyWithPhotosAndAServerThatCanWriteQuestions() {
        val library = json.decodeFromString(
            Library.serializer(),
            """{"photoQuiz": true, "children": [{"id": "c1", "name": "Sam", "games": {"photoQuiz": {"on": true}}}]}""",
        )
        val sam = library.children[0]
        assertTrue(library.gamesFor(sam, photos = true).any { it.key == PHOTO_QUIZ })
        assertFalse(library.gamesFor(sam, photos = false).any { it.key == PHOTO_QUIZ })
        assertFalse(library.copy(photoQuiz = false).gamesFor(sam, photos = true).any { it.key == PHOTO_QUIZ })
        assertFalse(library.gamesFor(sam, photos = true).first { it.key == PHOTO_QUIZ }.isQuiz)
    }
}
