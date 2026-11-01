package com.njdyson.wonderbox.ui

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.STORIES
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.games.STORY
import com.njdyson.wonderbox.games.StorySetup
import com.njdyson.wonderbox.games.gamesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoriesTest {
    private val jo = Child("jo", "Jo")
    private val noMaker = jo.copy(games = GameSettings(story = StorySetup(on = false)))
    private val bedtime = Library(children = listOf(jo), videos = listOf(Video("v1", "yt1", "Gruffalo", children = listOf("jo"), section = STORIES)))

    @Test
    fun storiesSectionHasWriteMineAndBedtime() {
        assertEquals(listOf(STORY, MY_STORIES, BEDTIME), bedtime.storySectionsFor(jo, stories = 2).map { it.key })
        assertEquals("2 stories to hear", bedtime.storySectionsFor(jo, stories = 2)[1].subtitle)
        assertEquals("1 story to hear", bedtime.storySectionsFor(jo, stories = 1)[1].subtitle)
    }

    @Test
    fun emptySectionsAreLeftOut() {
        assertEquals(listOf(STORY), Library().storySectionsFor(jo, stories = 0).map { it.key })
        assertEquals(listOf(MY_STORIES), Library().storySectionsFor(noMaker, stories = 3).map { it.key })
        assertEquals(listOf(BEDTIME), bedtime.storySectionsFor(noMaker, stories = 0).map { it.key })
        assertTrue(Library().storySectionsFor(noMaker, stories = 0).isEmpty())
    }

    @Test
    fun storyMakerIsNotInGames() {
        assertTrue(Library().gamesFor(jo).none { it.key == STORY })
    }
}
