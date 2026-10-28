package com.njdyson.wonderbox.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JigsawTest {
    @Test fun jumbledStartsWithNothingRight() {
        for (side in JIGSAW_SIZES) {
            repeat(50) { seed ->
                val j = Jigsaw.jumbled(side, Random(seed))
                assertEquals(side * side, j.size)
                assertEquals((0 until side * side).toList(), j.pieces.sorted())
                assertEquals(0, j.rightCount)
                assertFalse(j.solved)
            }
        }
    }

    @Test fun sizesAreKeptInRange() {
        assertEquals(2, Jigsaw.jumbled(1).side)
        assertEquals(5, Jigsaw.jumbled(9).side)
    }

    @Test fun swappingSolvesIt() {
        val j = Jigsaw.jumbled(3, Random(7))
        while (!j.solved) {
            val slot = j.pieces.indices.first { !j.isRight(it) }
            val home = j.pieces.indexOf(slot)
            assertTrue(j.swap(home, slot) >= 1)
        }
        assertTrue(j.moves <= 8)
    }

    @Test fun rightPiecesStayPut() {
        val j = Jigsaw(2, intArrayOf(0, 2, 1, 3))
        assertEquals(0, j.swap(0, 1))
        assertEquals(intArrayOf(0, 2, 1, 3).toList(), j.pieces.toList())
        assertEquals(2, j.swap(1, 2))
        assertTrue(j.solved)
        assertEquals(1, j.moves)
    }

    @Test fun stepsStopAtTheEdges() {
        val j = Jigsaw(3, IntArray(9) { it })
        assertEquals(0, j.step(0, -1, 0))
        assertEquals(1, j.step(0, 1, 0))
        assertEquals(3, j.step(0, 0, 1))
        assertEquals(8, j.step(8, 1, 1))
        assertEquals(5, j.step(8, 0, -1))
    }
}
