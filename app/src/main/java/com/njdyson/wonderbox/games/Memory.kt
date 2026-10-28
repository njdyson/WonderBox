package com.njdyson.wonderbox.games

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Memory pairs' [GameEntry.key]; it has its own screen (MemoryScreen). */
const val MEMORY = "memory"

/** A child's memory pairs, set in the phone app. */
@Serializable
data class MemorySetup(
    val on: Boolean = true,
    /** Pairs of cards: 3 is a 3 x 2 grid, 8 a 4 x 4 one. */
    val pairs: Int = 4,
    /** "clipart" (the bundled animals), "photos" (the child's family photos) or "both". */
    val pictures: String = "clipart",
) {
    companion object {
        val PAIRS = listOf(3, 4, 6, 8, 10)
    }
}

/**
 * Cards face down in a grid; turn two over and they stay up if they match. [faces] are what's
 * on each card (a picture's key), every one twice.
 */
class Memory(val faces: List<String>) {
    private val matched = mutableSetOf<Int>()
    private val up = mutableListOf<Int>()
    var flips = 0
        private set

    enum class Flip { First, Pair, NoPair, Ignored }

    val size get() = faces.size
    val pairs get() = faces.size / 2
    val matchedPairs get() = matched.size / 2
    val done get() = matched.size == faces.size

    /** Shown face up: matched, or one of the (up to two) just turned over. */
    fun isUp(card: Int) = card in matched || card in up
    fun isMatched(card: Int) = card in matched

    /** Two that don't match are showing; [hide] turns them back before the next go. */
    val showingNoPair get() = up.size == 2

    fun flip(card: Int): Flip {
        if (card !in faces.indices || isUp(card)) return Flip.Ignored
        if (showingNoPair) hide()
        flips++
        up += card
        if (up.size == 1) return Flip.First
        val (a, b) = up
        return if (faces[a] == faces[b]) {
            matched += up
            up.clear()
            Flip.Pair
        } else {
            Flip.NoPair
        }
    }

    fun hide() = up.clear()

    companion object {
        /** Columns for a grid of [cards], wider than tall to fit the TV. */
        fun columns(cards: Int) = when {
            cards <= 6 -> 3
            cards <= 16 -> 4
            else -> 5
        }

        /** [pairs] of different pictures from [pictures], each twice, shuffled. */
        fun deal(pictures: List<String>, pairs: Int, random: Random = Random): Memory {
            val chosen = pictures.distinct().shuffled(random).take(pairs)
            return Memory((chosen + chosen).shuffled(random))
        }
    }
}
