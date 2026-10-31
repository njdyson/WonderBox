package com.njdyson.wonderbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SurprisesTest {
    @Test
    fun mostRightAnswersChimeWithASurpriseEveryFewAndNeverTwoInARow() {
        for (seed in 0L until 50L) {
            val s = Surprises(Random(seed))
            val kinds = List(200) { s.next() }
            val surprises = kinds.indices.filter { kinds[it] != Surprises.Kind.Chime }
            // The first surprise comes within the first few answers.
            assertTrue(surprises.first() in 3..6)
            // Then a gap of 3 to 6 chimes between surprises.
            surprises.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: $a -> $b", b - a - 1 in 3..6) }
            assertTrue(surprises.size in 200 / 7..200 / 4)
        }
    }

    @Test
    fun surprisesAreMostlyFarts() {
        val s = Surprises(Random(1))
        val kinds = List(5000) { s.next() }.filter { it != Surprises.Kind.Chime }
        val farts = kinds.count { it == Surprises.Kind.Fart }.toDouble() / kinds.size
        assertTrue(farts in 0.65..0.85)
        assertEquals(setOf(Surprises.Kind.Fart, Surprises.Kind.Boing), kinds.toSet())
    }
}
