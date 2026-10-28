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
 * synth chimes) or "silly" (boings, squeaks and, for right answers, farts). A wrong answer is
 * never rewarded with a fart, so there's no point getting things wrong on purpose.
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

    fun play(effect: Effect, child: Child) = play(effect, child.games.sounds)

    fun play(effect: Effect, mode: String) {
        val silly = mode == SILLY
        if (mode == OFF) return
        when (effect) {
            Effect.Move -> clip(move.first(), 0.35f)
            Effect.Right -> if (silly) {
                // About half the time a fart; otherwise a boing, squeak or blip.
                clip(if (Math.random() < 0.5) farts.random() else boings.random())
            } else {
                clip(right.first(), 0.7f)
            }
            Effect.Wrong -> if (silly) clip(sillyWrong.random(), 0.8f) else clip(wrong.first(), 0.6f)
            Effect.Piece -> if (silly) clip(boings.random()) else Chimes.play(Chimes.Sound.Right)
            Effect.Finish -> if (silly) {
                clip(fanfare.first())
                main.postDelayed({ clip(farts[3]) }, 650)
            } else {
                Chimes.play(Chimes.Sound.Done)
            }
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
