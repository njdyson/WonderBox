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
               {"id": "v3", "youtubeId": "ccccccccccc", "children": ["c1"], "section": "music"},
               {"id": "v4", "youtubeId": "hhhhhhhhhhh", "children": ["c1"], "section": "exercise"}
             ],
             "channels": [
               {"id": "ch1", "title": "Numberblocks", "children": ["c1"], "tier": "free",
                "videos": [{"youtubeId": "ddddddddddd", "title": "One"}]},
               {"id": "ch2", "title": "Bluey", "children": ["c1"], "tier": "treat",
                "videos": [{"youtubeId": "eeeeeeeeeee"}, {"youtubeId": "fffffffffff"}]},
               {"id": "ch3", "title": "Empty", "children": ["c1"], "videos": []},
               {"id": "ch5", "title": "Cosmic Kids Yoga", "children": ["c1"], "section": "exercise",
                "videos": [{"youtubeId": "iiiiiiiiiii"}]},
               {"id": "ch4", "title": "Not Sam's", "children": ["c2"], "videos": [{"youtubeId": "ggggggggggg"}]}
             ]}
            """.trimIndent(),
        )
        assertEquals(GameSettings(3, 2, 5), library.children[0].games)
        val shelves = library.shelvesFor("c1")
        assertEquals(listOf("My videos", "Music", "Exercise", "Numberblocks", "Cosmic Kids Yoga", "Treats", "Bluey"), shelves.map { it.title })
        assertEquals(listOf(false, false, false, false, false, true, true), shelves.map { it.treat })
        assertEquals(listOf("ddddddddddd"), shelves[3].videos.map { it.youtubeId })
        // Channel videos get their own IDs, so the same video in two channels doesn't clash.
        assertEquals(listOf("ch2/eeeeeeeeeee", "ch2/fffffffffff"), shelves[6].videos.map { it.id })
        assertTrue(shelves[6].videos.all { it.isTreat })
        // Each screen gets its own rows.
        assertEquals(listOf("My videos", "Numberblocks"), library.videoShelvesFor("c1").map { it.title })
        assertEquals(listOf("Music"), library.musicShelvesFor("c1").map { it.title })
        assertEquals(listOf("Exercise", "Cosmic Kids Yoga"), library.exerciseShelvesFor("c1").map { it.title })
    }
    @Test
    fun treatsToPickAreOneMixedShelfInANewOrderEachTime() {
        val channels = (1..3).joinToString(",") { c ->
            """{"id": "ch$c", "title": "Channel $c", "children": ["c1"], "tier": "treat",
                "videos": [${(1..6).joinToString(",") { v -> """{"youtubeId": "c${c}v$v", "title": "C$c V$v"}""" }}]}"""
        }
        val library = json.decodeFromString(
            Library.serializer(),
            """{"children": [{"id": "c1", "name": "Sam"}],
                "videos": [{"id": "v1", "youtubeId": "c1v1", "children": ["c1"], "tier": "treat"}],
                "channels": [$channels]}""",
        )
        val mixed = library.mixedTreatsFor("c1", seed = 1)
        assertEquals(1, mixed.size)
        assertTrue(mixed[0].treat)
        val order = mixed[0].videos.map { it.youtubeId }
        // Every treat once (the same video in two places shows once), not channel by channel.
        assertEquals(library.treatShelvesFor("c1").flatMap { it.videos }.map { it.youtubeId }.toSet(), order.toSet())
        assertEquals(order.size, order.toSet().size)
        assertTrue(order != order.sortedBy { it.take(2) })
        // Same seed, same order; another seed, another order.
        assertEquals(order, library.mixedTreatsFor("c1", seed = 1)[0].videos.map { it.youtubeId })
        assertTrue(order != library.mixedTreatsFor("c1", seed = 2)[0].videos.map { it.youtubeId })
        assertEquals(emptyList<Shelf>(), library.mixedTreatsFor("nobody", seed = 1))
    }
}
