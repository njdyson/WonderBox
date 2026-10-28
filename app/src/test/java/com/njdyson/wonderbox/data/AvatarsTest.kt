package com.njdyson.wonderbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarsTest {
    @Test
    fun namesFilesByCodePoint() {
        assertEquals("1f98a.webp", Avatars.fileName("🦊"))
        assertEquals("2b50.webp", Avatars.fileName("⭐"))
        // The variation selector some keyboards add is dropped.
        assertEquals("2b50.webp", Avatars.fileName("⭐️"))
        assertNull(Avatars.fileName(" "))
    }

    @Test
    fun readsAPictureFromTheLibrary() {
        val library = json.decodeFromString(
            Library.serializer(),
            """{"children": [{"id": "c1", "name": "Sam", "picture": "/api/files/children/c1/p.webp"}, {"id": "c2", "name": "Jo"}]}""",
        )
        assertEquals("/api/files/children/c1/p.webp", library.children[0].picture)
        assertEquals("", library.children[1].picture)
    }
}
