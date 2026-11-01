package com.njdyson.wonderbox.ui

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.games.GameEntry
import com.njdyson.wonderbox.games.STORY

/** "My stories" in the Stories section: the child's finished stories from the story maker. */
const val MY_STORIES = "mystories"

/** "Bedtime stories" in the Stories section: the story videos the parent added. */
const val BEDTIME = "bedtime"

/**
 * What's in [child]'s Stories section, in order: Write a story (the story maker, when it's on),
 * My stories (when they've finished one) and Bedtime stories (the story videos). Empty ones are
 * left out; with none at all there's no Stories tile.
 */
fun AppState.storySectionsFor(child: Child): List<GameEntry> = library.storySectionsFor(child, storiesFor(child.id).size)

/** [storySectionsFor], for a child who has finished [stories] stories. */
fun Library.storySectionsFor(child: Child, stories: Int): List<GameEntry> = buildList {
    if (child.games.story.on) add(GameEntry(STORY, "Write a story", "✏️", "Make one with pictures"))
    if (stories > 0) add(GameEntry(MY_STORIES, "My stories", "📚", "$stories ${if (stories == 1) "story" else "stories"} to hear"))
    if (storyShelvesFor(child.id).isNotEmpty()) add(GameEntry(BEDTIME, "Bedtime stories", "🌙", "Stories to watch"))
}
