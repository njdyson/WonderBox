package com.njdyson.wonderbox.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Soft little sounds for the games, made on the fly from sine waves (so there are no sound
 * files to license or ship): a bell-like note fading out, a few in a row for a tune. Quiet on
 * purpose; the voice does the cheering. Never throws: a TV without sound just stays silent.
 */
object Chimes {
    private const val RATE = 22_050
    private val player = Executors.newSingleThreadExecutor { Thread(it, "chimes").apply { isDaemon = true } }
    private val cache = HashMap<Sound, ShortArray>()

    enum class Sound(val notes: List<Double>, val noteMs: Int, val volume: Double) {
        /** A piece is picked up. */
        Pick(listOf(784.0), 140, 0.22),
        /** A piece is put down somewhere, not yet right. */
        Drop(listOf(523.0), 150, 0.2),
        /** A piece clicks into its right place. */
        Right(listOf(659.0, 988.0), 120, 0.3),
        /** Nothing to do (that piece is already right). */
        Bump(listOf(330.0), 110, 0.15),
        /** The whole thing is done: up the scale. */
        Done(listOf(523.0, 659.0, 784.0, 1047.0, 1319.0), 130, 0.3),
    }

    fun play(sound: Sound) {
        runCatching {
            player.execute {
                runCatching {
                    val pcm = synchronized(cache) { cache.getOrPut(sound) { render(sound) } }
                    @Suppress("DEPRECATION") // AudioTrack.Builder needs API 23; Fire OS 5 is 22.
                    val track = AudioTrack(
                        AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, pcm.size * 2, AudioTrack.MODE_STATIC,
                    )
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    Thread.sleep(pcm.size * 1000L / RATE + 50)
                    track.release()
                }
            }
        }
    }

    /** Each note rings on (a bell, fading) while the next starts, so a tune sounds smooth. */
    private fun render(sound: Sound): ShortArray {
        val step = RATE * sound.noteMs / 1000
        val ring = RATE * 600 / 1000
        val out = DoubleArray(step * (sound.notes.size - 1) + ring)
        sound.notes.forEachIndexed { n, freq ->
            for (i in 0 until ring) {
                val t = i.toDouble() / RATE
                // A soft start (no click), then an exponential fade; a quiet octave above makes it a bell.
                val envelope = minOf(1.0, i / (RATE * 0.004)) * exp(-t * 7)
                out[n * step + i] += envelope * (sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t))
            }
        }
        return ShortArray(out.size) { (out[it] * sound.volume * Short.MAX_VALUE).coerceIn(-32767.0, 32767.0).toInt().toShort() }
    }
}
