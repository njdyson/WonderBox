package com.njdyson.wonderbox.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A quiet tune looping behind the menus, played like a music box. The tunes are written out
 * here and made on the TV (like [Chimes]), so there are no music files to license: two of our
 * own and Jingle Bells (James Lord Pierpont, 1857, public domain) for Christmas.
 *
 * [play] a tune, or null for none: it fades in, and fades out before stopping or changing.
 * While [ducked] says something's being read aloud it goes quieter. Never throws: a TV
 * without sound just stays silent.
 */
class Music(private val ducked: () -> Boolean) {

    enum class Style { Waltz, Tiptoe, March }

    /**
     * [melody] is notes like "E5:2" (E above middle C for two eighths), "r:2" for a rest, bars
     * split by "|". [chords] are one per bar, or several with their lengths ("Bb:4 C:2"). [bells]
     * plays it twice, with sleigh bells the second time.
     */
    enum class Tune(val bpm: Int, val bar: Int, val style: Style, val melody: String, val chords: String, val bells: Boolean = false) {
        /** Our own lullaby, a waltz in F. */
        Calm(
            84, 6, Style.Waltz,
            "A4:2 C5:2 F5:2 | E5:3 D5:1 C5:2 | D5:2 F5:2 A5:2 | F5:2 D5:2 Bb4:2 | A4:2 C5:2 F5:2 | G5:3 F5:1 E5:2 | D5:2 E5:2 C5:2 | F5:6 |" +
                "F5:2 G5:2 A5:2 | C6:4 A5:2 | Bb5:2 A5:2 G5:2 | E5:4 C5:2 | A4:2 C5:2 F5:2 | A5:3 G5:1 F5:2 | D5:2 G5:2 E5:2 | F5:6",
            "F | C | Dm | Bb | F | C | Bb:4 C:2 | F | Bb | F | Gm | C | F | Dm | Bb:4 C:2 | F",
        ),

        /** Our own tiptoeing tune in D minor: spooky, but not scary. */
        Spooky(
            100, 8, Style.Tiptoe,
            "D5:1 r:1 F5:1 r:1 A5:2 G#5:1 A5:1 | Bb5:2 A5:2 F5:2 D5:2 | G5:1 r:1 Bb5:1 r:1 D6:2 C#6:1 D6:1 | E6:2 C#6:2 A5:4 |" +
                "D5:1 r:1 F5:1 r:1 A5:2 G#5:1 A5:1 | D6:2 Bb5:2 F5:2 D5:2 | E5:1 F5:1 G5:1 A5:1 C#5:2 E5:2 | D5:6 r:2 |" +
                "A5:1 r:1 A5:1 r:1 C6:2 A5:2 | G5:1 r:1 G5:1 r:1 E5:4 | F5:1 G5:1 A5:1 Bb5:1 A5:2 F5:2 | E5:2 G5:2 C#5:4 |" +
                "D5:1 F5:1 A5:1 D6:1 C#6:2 A5:2 | Bb5:2 G5:2 D5:2 Bb4:2 | A4:2 C#5:2 E5:2 G5:2 | D5:4 r:4",
            "Dm | Dm | Gm | A7 | Dm | Bb | A7 | Dm | F | C | Bb | A7 | Dm | Gm | A7 | Dm",
        ),

        /** Jingle Bells' chorus, gently. */
        Jingle(
            104, 8, Style.March,
            "E5:2 E5:2 E5:4 | E5:2 E5:2 E5:4 | E5:2 G5:2 C5:3 D5:1 | E5:8 | F5:2 F5:2 F5:3 F5:1 | F5:2 E5:2 E5:2 E5:1 E5:1 | E5:2 D5:2 D5:2 E5:2 | D5:4 G5:4 |" +
                "E5:2 E5:2 E5:4 | E5:2 E5:2 E5:4 | E5:2 G5:2 C5:3 D5:1 | E5:8 | F5:2 F5:2 F5:3 F5:1 | F5:2 E5:2 E5:2 E5:1 E5:1 | G5:2 G5:2 F5:2 D5:2 | C5:8",
            "C | C | C | C | F | C | D7 | G7 | C | C | C | C | F | C | G7 | C",
            bells = true,
        );

        companion object {
            /** Each season's tune: its own at Halloween and Christmas, otherwise the lullaby. */
            fun of(season: Season?) = when (season) {
                Season.Halloween -> Spooky
                Season.Christmas -> Jingle
                else -> Calm
            }
        }
    }

    private val lock = Object()
    @Volatile private var wanted: Tune? = null
    @Volatile private var closed = false
    private var thread: Thread? = null

    /** Plays [tune] round and round, or fades out to silence for null. */
    fun play(tune: Tune?) {
        if (closed) return
        synchronized(lock) {
            wanted = tune
            if (tune != null && thread == null) thread = Thread(::run, "music").apply { isDaemon = true; start() }
            lock.notifyAll()
        }
    }

    fun close() {
        synchronized(lock) {
            wanted = null
            closed = true
            lock.notifyAll()
        }
    }

    /** Writes the tune to the speaker a little at a time, easing the volume towards where it should be. */
    private fun run() {
        runCatching {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            @Suppress("DEPRECATION") // AudioTrack.Builder needs API 23; Fire OS 5 is 22.
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE / 5 * 2), AudioTrack.MODE_STREAM,
            )
            val chunk = ShortArray(CHUNK)
            var current: Tune? = null
            var pcm = FloatArray(0)
            var at = 0
            var gain = 0f
            try {
                while (!closed) {
                    val tune = wanted
                    if (current != tune && gain <= 0f) {
                        // Faded out: stop, or start the next tune from its beginning.
                        if (tune == null) {
                            track.pause()
                            track.flush()
                            current = null
                            synchronized(lock) { while (wanted == null && !closed) lock.wait() }
                            continue
                        }
                        pcm = render(tune)
                        current = tune
                        at = 0
                        track.play()
                    }
                    val target = when {
                        current != tune -> 0f
                        ducked() -> VOLUME * DUCK
                        else -> VOLUME
                    }
                    for (i in 0 until CHUNK) {
                        gain = if (gain < target) minOf(target, gain + VOLUME / (RATE * FADE_IN)) else maxOf(target, gain - VOLUME / (RATE * FADE_OUT))
                        chunk[i] = (pcm[at] * gain * Short.MAX_VALUE).toInt().toShort()
                        at = (at + 1) % pcm.size
                    }
                    track.write(chunk, 0, CHUNK)
                }
            } finally {
                track.release()
            }
        }
        synchronized(lock) { thread = null }
    }

    companion object {
        private const val RATE = 22_050
        private const val CHUNK = 1024
        /** As loud as it gets: well under a video, so it's background. */
        private const val VOLUME = 0.25f
        /** How much of that while something's read aloud. */
        private const val DUCK = 0.3f
        private const val FADE_IN = 1.5f
        private const val FADE_OUT = 0.6f

        private val cache = HashMap<Tune, FloatArray>()

        /** The whole tune as one loop, -1 to 1; notes still ringing at the end ring on into the start. */
        fun render(tune: Tune): FloatArray = synchronized(cache) {
            cache.getOrPut(tune) {
                val eighth = RATE * 30.0 / tune.bpm
                val bars = tune.melody.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val chords = tune.chords.split("|").map { it.trim() }
                val passes = if (tune.bells) 2 else 1
                val length = (bars.size * tune.bar * eighth * passes).toInt()
                val out = FloatArray(length)
                for (pass in 0 until passes) {
                    val passStart = pass * bars.size * tune.bar
                    bars.forEachIndexed { b, bar ->
                        var e = (passStart + b * tune.bar).toDouble()
                        for (token in bar.split(" ").filter { it.isNotBlank() }) {
                            val (name, len) = token.split(":")
                            if (name != "r") pluck(out, (e * eighth).toInt(), freq(name), MELODY)
                            e += len.toDouble()
                        }
                        accompany(out, tune, chords.getOrElse(b) { "" }, passStart + b * tune.bar, eighth)
                        if (tune.bells && pass == 1) {
                            for (beat in 0 until tune.bar step 2) jingle(out, ((passStart + b * tune.bar + beat) * eighth).toInt(), beat)
                        }
                    }
                }
                val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
                for (i in out.indices) out[i] = out[i] / peak * 0.9f
                out
            }
        }

        /** The bass and chords under one bar, starting [start] eighths in. */
        private fun accompany(out: FloatArray, tune: Tune, bar: String, start: Int, eighth: Double) {
            var e = start
            val parts = bar.split(" ").filter { it.isNotBlank() }
            for (part in parts) {
                val name = part.substringBefore(":")
                val len = part.substringAfter(":", "").toIntOrNull() ?: tune.bar
                val notes = chord(name)
                for (step in 0 until len step 2) {
                    val at = ((e + step) * eighth).toInt()
                    val first = step == 0
                    when (tune.style) {
                        Style.Waltz ->
                            if (first) pluck(out, at, notes[0] / 2, BASS) else notes.drop(1).forEach { pluck(out, at, it, CHORD) }
                        Style.Tiptoe -> {
                            // Bass creeping root, fifth, root, fifth on every beat, a soft chord on the off-beats.
                            pluck(out, at, if (step % 4 == 0) notes[0] / 2 else notes[2] / 4, TIPTOE)
                            if (step % 4 == 2) notes.drop(1).forEach { pluck(out, at, it * 2, CHORD) }
                        }
                        Style.March ->
                            if (step % 4 == 0) pluck(out, at, (if (step == 0) notes[0] else notes[2] / 2) / 2, BASS)
                            else notes.drop(1).forEach { pluck(out, at, it, CHORD) }
                    }
                }
                e += len
            }
        }

        /** A note's pitch in Hz from its name: "C5" is the C above middle C, "Bb4", "C#6". */
        private fun freq(name: String): Double {
            val letter = "C D EF G A B".indexOf(name[0].uppercaseChar())
            var i = 1
            var semis = letter
            while (i < name.length && (name[i] == '#' || name[i] == 'b')) {
                semis += if (name[i] == '#') 1 else -1
                i++
            }
            val octave = name.substring(i).toInt()
            val midi = 12 * (octave + 1) + semis
            return 440.0 * 2.0.pow((midi - 69) / 12.0)
        }

        /** A chord's notes from its name ("F", "Dm", "G7"), root first, around middle C. */
        private fun chord(name: String): List<Double> {
            if (name.isBlank()) return listOf(freq("C4"), freq("E4"), freq("G4"))
            val minor = name.endsWith("m")
            val seventh = name.endsWith("7")
            val rootName = name.removeSuffix("m").removeSuffix("7")
            var root = freq(rootName + "3")
            if (root < freq("F3")) root *= 2
            val third = root * 2.0.pow((if (minor) 3 else 4) / 12.0)
            val fifth = root * 2.0.pow(7 / 12.0)
            return buildList {
                add(root)
                add(third * 2)
                add(fifth * 2)
                if (seventh) add(root * 2.0.pow(10 / 12.0) * 2)
            }
        }

        /** How a note sounds: how loud, how quickly it fades, how long it rings, and how bright. */
        private class Voice(val volume: Float, val fade: Double, val ring: Double, val bright: Float)

        private val MELODY = Voice(1f, 2.4, 1.8, 0.3f)
        private val BASS = Voice(0.42f, 2.6, 1.2, 0.12f)
        private val TIPTOE = Voice(0.42f, 7.0, 0.5, 0.12f)
        private val CHORD = Voice(0.16f, 4.5, 0.8, 0.1f)

        private const val TABLE = 4096
        private val sine = FloatArray(TABLE) { sin(2 * PI * it / TABLE).toFloat() }

        /** A music-box note: a soft start, then fading, with a quieter octave above and a quick tinkle. */
        private fun pluck(out: FloatArray, start: Int, hz: Double, voice: Voice) {
            val n = (voice.ring * RATE).toInt()
            val step = hz * TABLE / RATE
            val fade = exp(-voice.fade / RATE).toFloat()
            val fast = exp(-voice.fade * 2.5 / RATE).toFloat()
            val faster = exp(-40.0 / RATE).toFloat()
            var env = 1f
            var env2 = voice.bright
            var env3 = 0.12f
            for (i in 0 until n) {
                val p = i * step
                val attack = if (i < 70) i / 70f else 1f
                // Ease out over the last tenth so it never clicks off.
                val tail = if (i > n * 0.9) (n - i) / (n * 0.1f) else 1f
                val s = sine[(p.toLong() and (TABLE - 1).toLong()).toInt()] * env +
                    sine[((p * 2).toLong() and (TABLE - 1).toLong()).toInt()] * env2 +
                    sine[((p * 5.4).toLong() and (TABLE - 1).toLong()).toInt()] * env3
                val index = (start + i) % out.size
                out[index] += s * voice.volume * attack * tail
                env *= fade
                env2 *= fast
                env3 *= faster
            }
        }

        private val noise = Random(7)

        /** A shake of sleigh bells: a short, bright hiss with a shimmer; the off-beats softer. */
        private fun jingle(out: FloatArray, start: Int, beat: Int) {
            val n = (0.18 * RATE).toInt()
            val loud = if (beat % 4 == 0) 0.22f else 0.14f
            val decay = exp(-22.0 / RATE).toFloat()
            var env = loud
            var last = 0f
            for (i in 0 until n) {
                val white = noise.nextFloat() * 2 - 1
                val bright = white - last
                last = white
                val shimmer = sine[((i * 5200.0 * TABLE / RATE).toLong() and (TABLE - 1).toLong()).toInt()] * 0.3f +
                    sine[((i * 6900.0 * TABLE / RATE).toLong() and (TABLE - 1).toLong()).toInt()] * 0.2f
                out[(start + i) % out.size] += (bright * 0.6f + shimmer) * env
                env *= decay
            }
        }
    }
}
