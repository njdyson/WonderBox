package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryTest {
    @Test
    fun everyPictureIsDealtTwice() {
        val memory = Memory.deal(listOf("a", "b", "c", "d", "e", "f"), 4, Random(7))
        assertEquals(8, memory.size)
        assertTrue(memory.faces.groupingBy { it }.eachCount().values.all { it == 2 })
    }

    @Test
    fun matchingCardsStayUp() {
        val memory = Memory(listOf("a", "b", "a", "b"))
        assertEquals(Memory.Flip.First, memory.flip(0))
        assertEquals(Memory.Flip.Ignored, memory.flip(0))
        assertEquals(Memory.Flip.NoPair, memory.flip(1))
        assertTrue(memory.showingNoPair)
        // Turning another card over hides the two that didn't match.
        assertEquals(Memory.Flip.First, memory.flip(2))
        assertFalse(memory.isUp(0))
        assertFalse(memory.isUp(1))
        assertEquals(Memory.Flip.Pair, memory.flip(0))
        assertTrue(memory.isMatched(0) && memory.isMatched(2))
        assertEquals(Memory.Flip.Ignored, memory.flip(2))
        memory.flip(1)
        assertEquals(Memory.Flip.Pair, memory.flip(3))
        assertTrue(memory.done)
        assertEquals(6, memory.flips)
    }

    @Test
    fun gridsAreWiderThanTall() {
        assertEquals(listOf(3, 4, 4, 4, 5), MemorySetup.PAIRS.map { Memory.columns(it * 2) })
    }
}
