package com.njdyson.wonderbox.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.njdyson.wonderbox.R
import com.njdyson.wonderbox.data.Child

/**
 * Sound effects for the games, from small CC0 clips in res/raw (see tools/sounds/README.md).
 * Each child has a sound setting from the phone app: "off", "calm" (soft blips and the
 * synth chimes) or "silly". Silly right answers are mostly a chime too, with a fart (or now and
 * then a boing or squeak) every few answers as a surprise: a fart every time stops being funny.
 * A wrong answer is never rewarded with a fart, so there's no point getting things wrong on purpose.
 * Never throws: a TV without sound just stays silent.
 */
class Sounds(private val context: Context) {
    enum class Effect {
        /** The focus moves to another answer. */
        Move,
        /** A right answer. */
        Right,
        /** A wrong answer: try again. */
        Wrong,
        /** A jigsaw piece clicks into place. */
        Piece,
        /** A game or jigsaw is finished. */
        Finish,
    }

    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    }.getOrNull()
    private val main = Handler(Looper.getMainLooper())

    private fun load(vararg ids: Int): List<Int> = ids.map { id -> runCatching { pool?.load(context, id, 1) ?: 0 }.getOrDefault(0) }

    private val move = load(R.raw.sfx_move)
    private val right = load(R.raw.sfx_right)
    private val wrong = load(R.raw.sfx_wrong)
    private val farts = load(R.raw.sfx_fart_1, R.raw.sfx_fart_2, R.raw.sfx_fart_3, R.raw.sfx_fart_4, R.raw.sfx_fart_5)
    private val boings = load(
        R.raw.sfx_boing_1, R.raw.sfx_boing_2, R.raw.sfx_squeak_1, R.raw.sfx_squeak_2, R.raw.sfx_squeak_3,
        R.raw.sfx_silly_1, R.raw.sfx_silly_2, R.raw.sfx_silly_3, R.raw.sfx_silly_4,
    )
    private val sillyWrong = load(R.raw.sfx_wrong_silly_1, R.raw.sfx_wrong_silly_2, R.raw.sfx_wrong_silly_3)
    private val fanfare = load(R.raw.sfx_finish_silly)

    /** When the next silly surprise comes, shared by right answers and jigsaw pieces. */
    private val surprises = Surprises()

    fun play(effect: Effect, child: Child) = play(effect, child.games.sounds)

    fun play(effect: Effect, mode: String) {
        val silly = mode == SILLY
        if (mode == OFF) return
        when (effect) {
            Effect.Move -> clip(move.first(), 0.35f)
            Effect.Right -> if (silly) surprise() else clip(right.first(), 0.7f)
            Effect.Wrong -> if (silly) clip(sillyWrong.random(), 0.8f) else clip(wrong.first(), 0.6f)
            Effect.Piece -> if (silly) surprise() else Chimes.play(Chimes.Sound.Right)
            Effect.Finish -> if (silly) {
                clip(fanfare.first())
                main.postDelayed({ clip(farts[3]) }, 650)
            } else {
                Chimes.play(Chimes.Sound.Done)
            }
        }
    }

    /** A chime, or every few times a silly surprise: mostly a fart, sometimes a boing or squeak. */
    private fun surprise() {
        when (surprises.next()) {
            Surprises.Kind.Chime -> Chimes.play(Chimes.Sound.Right)
            Surprises.Kind.Fart -> clip(farts.random())
            Surprises.Kind.Boing -> clip(boings.random())
        }
    }

    /** The jigsaw's soft synth sounds (picking up and putting down pieces), unless sound is off. */
    fun chime(sound: Chimes.Sound, child: Child) {
        if (child.games.sounds != OFF) Chimes.play(sound)
    }

    private fun clip(id: Int, volume: Float = 1f) {
        if (id != 0) runCatching { pool?.play(id, volume, volume, 1, 0, 1f) }
    }

    fun close() {
        runCatching { pool?.release() }
    }

    companion object {
        const val OFF = "off"
        const val CALM = "calm"
        const val SILLY = "silly"
    }
}

/**
 * Decides when a silly right answer gets a surprise instead of the usual chime: after a gap of
 * [minGap] to [maxGap] chimes (so the first comes within the first few answers, and never two in
 * a row). A surprise is a fart [fartShare] of the time, otherwise a boing or squeak.
 */
class Surprises(
    private val random: java.util.Random = java.util.Random(),
    private val minGap: Int = 3,
    private val maxGap: Int = 6,
    private val fartShare: Double = 0.75,
) {
    enum class Kind { Chime, Fart, Boing }

    private var wait = gap()

    private fun gap() = minGap + random.nextInt(maxGap - minGap + 1)

    fun next(): Kind {
        if (wait > 0) {
            wait--
            return Kind.Chime
        }
        wait = gap()
        return if (random.nextDouble() < fartShare) Kind.Fart else Kind.Boing
    }
}
