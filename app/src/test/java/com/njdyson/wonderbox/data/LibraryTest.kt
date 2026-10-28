package com.njdyson.wonderbox.data

import org.junit.Assert.assertEquals
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
}
