package com.njdyson.wonderbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    // As GET /api/wonder/library returns it, with a field the app doesn't know about.
    private val response = """
        {"children": [{"id": "c1", "name": "Sam", "avatar": "🦊", "color": "#FF6B9A"}, {"id": "c2", "name": "Jo"}],
         "videos": [
           {"id": "v1", "youtubeId": "aqz-KE-bpKQ", "title": "Big Buck Bunny", "children": ["c1", "c2"], "extra": 1},
           {"id": "v2", "youtubeId": "jNQXAC9IVRw", "title": "Me at the zoo", "children": ["c2"]}
         ]}
    """.trimIndent()

    @Test
    fun parsesTheServersLibrary() {
        val library = json.decodeFromString(Library.serializer(), response)
        assertEquals(listOf("Sam", "Jo"), library.children.map { it.name })
        assertEquals("#FFD23F", library.children[1].color)
    }

    @Test
    fun eachChildSeesOnlyTheirVideosInOrder() {
        val library = json.decodeFromString(Library.serializer(), response)
        assertEquals(listOf("v1"), library.videosFor("c1").map { it.id })
        assertEquals(listOf("v1", "v2"), library.videosFor("c2").map { it.id })
        assertEquals(emptyList<Video>(), library.videosFor("nobody"))
    }

    @Test
    fun shelvesPutAlwaysThereVideosFirstThenTreats() {
        val library = json.decodeFromString(
            Library.serializer(),
            """
            {"children": [{"id": "c1", "name": "Sam", "games": {"maths": 3, "spelling": 2, "roundSize": 5}}],
             "videos": [
               {"id": "v1", "youtubeId": "aaaaaaaaaaa", "children": ["c1"], "tier": "treat"},
               {"id": "v2", "youtubeId": "bbbbbbbbbbb", "children": ["c1"]},
               {"id": "v3", "youtubeId": "ccccccccccc", "children": ["c1"], "section": "music"}
             ],
             "channels": [
               {"id": "ch1", "title": "Numberblocks", "children": ["c1"], "tier": "free",
                "videos": [{"youtubeId": "ddddddddddd", "title": "One"}]},
               {"id": "ch2", "title": "Bluey", "children": ["c1"], "tier": "treat",
                "videos": [{"youtubeId": "eeeeeeeeeee"}, {"youtubeId": "fffffffffff"}]},
               {"id": "ch3", "title": "Empty", "children": ["c1"], "videos": []},
               {"id": "ch4", "title": "Not Sam's", "children": ["c2"], "videos": [{"youtubeId": "ggggggggggg"}]}
             ]}
            """.trimIndent(),
        )
        assertEquals(GameSettings(3, 2, 5), library.children[0].games)
        val shelves = library.shelvesFor("c1")
        assertEquals(listOf("My videos", "Music", "Numberblocks", "Treats", "Bluey"), shelves.map { it.title })
        assertEquals(listOf(false, false, false, true, true), shelves.map { it.treat })
        assertEquals(listOf("ddddddddddd"), shelves[2].videos.map { it.youtubeId })
        // Channel videos get their own IDs, so the same video in two channels doesn't clash.
        assertEquals(listOf("ch2/eeeeeeeeeee", "ch2/fffffffffff"), shelves[4].videos.map { it.id })
        assertTrue(shelves[4].videos.all { it.isTreat })
    }
}
