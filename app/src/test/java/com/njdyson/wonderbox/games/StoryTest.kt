package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Avatars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class StoryTest {
    @Test
    fun everyPictureIsBundled() {
        val bundled = File("src/main/assets/avatars").list().orEmpty().toSet()
        val all = StoryPictures.HEROES + StoryPictures.PLACES + StoryPictures.THINGS + StoryPictures.ENDINGS
        all.forEach { (word, picture) -> assertTrue("$word $picture", Avatars.fileName(picture) in bundled) }
    }

    @Test
    fun turnsGoWhoWhereWhatThenTheEnd() {
        assertEquals(StoryPictures.HEROES, StoryPictures.forTurn(1, 5))
        assertEquals(StoryPictures.PLACES, StoryPictures.forTurn(2, 5))
        assertEquals(StoryPictures.THINGS, StoryPictures.forTurn(3, 5))
        assertEquals(StoryPictures.ENDINGS, StoryPictures.forTurn(5, 5))
        assertEquals(StoryPictures.ENDINGS, StoryPictures.forTurn(3, 3))
    }

    @Test
    fun optionsAreThreeNewPictures() {
        repeat(20) { seed ->
            val picked = listOf(StoryPick("owl", "🦉"), StoryPick("woods", "🌳"))
            val options = StoryPictures.options(3, 5, picked, Random(seed))
            assertEquals(3, options.size)
            assertEquals(3, options.map { it.word }.toSet().size)
            assertTrue(options.none { it.word == "owl" })
        }
    }

    @Test
    fun theSimpleStoryKeepsItsHero() {
        val hero = StoryPart(StoryPick("fox", "🦊"), "Once upon a time, there was a fox.", ai = false)
        val text = SimpleStory.part(3, 5, StoryPick("apple", "🍎"), listOf(hero), "Sam", Random(2))
        assertTrue(text, "fox" in text && "apple" in text)
        assertTrue(SimpleStory.part(5, 5, StoryPick("party", "🎂"), listOf(hero), "Sam").contains("party"))
    }
}
