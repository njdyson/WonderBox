package com.njdyson.wonderbox.games

import kotlin.random.Random

/** The jigsaw sizes the phone app offers: pieces along each side (2 is 2 x 2 = 4 pieces). 0 turns it off. */
val JIGSAW_SIZES = 2..5

/**
 * A photo cut into [side] x [side] pieces and jumbled up. Played by swapping: pick a piece up,
 * carry it to another place, and the two swap over. A piece in its right place clicks in and
 * stays put. Slots and pieces are numbered left to right, top to bottom; [pieces] says which
 * piece is in each slot, so piece n is right when it's in slot n.
 */
class Jigsaw(val side: Int, val pieces: IntArray) {
    val size get() = pieces.size
    var moves = 0
        private set

    fun isRight(slot: Int) = pieces[slot] == slot
    val solved get() = pieces.indices.all(::isRight)
    val rightCount get() = pieces.indices.count(::isRight)

    /** Swaps the pieces in slots [a] and [b]; answers how many of the two are now right. */
    fun swap(a: Int, b: Int): Int {
        if (a == b || isRight(a) || isRight(b)) return 0
        val held = pieces[a]
        pieces[a] = pieces[b]
        pieces[b] = held
        moves++
        return (if (isRight(a)) 1 else 0) + (if (isRight(b)) 1 else 0)
    }

    /** The slot next to [slot] in a direction, or [slot] itself at the edge. */
    fun step(slot: Int, dx: Int, dy: Int): Int {
        val x = (slot % side + dx).coerceIn(0, side - 1)
        val y = (slot / side + dy).coerceIn(0, side - 1)
        return y * side + x
    }

    companion object {
        /** A new jumbled jigsaw: no piece starts in its right place, so every one is a find. */
        fun jumbled(side: Int, random: Random = Random): Jigsaw {
            val n = side.coerceIn(JIGSAW_SIZES.first, JIGSAW_SIZES.last).let { it * it }
            val pieces = IntArray(n) { it }
            // Sattolo's shuffle: one big cycle, so nothing is left where it was.
            for (i in n - 1 downTo 1) {
                val j = random.nextInt(i)
                val t = pieces[i]
                pieces[i] = pieces[j]
                pieces[j] = t
            }
            return Jigsaw(side.coerceIn(JIGSAW_SIZES.first, JIGSAW_SIZES.last), pieces)
        }
    }
}
