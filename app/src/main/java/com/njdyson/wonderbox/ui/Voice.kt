package com.njdyson.wonderbox.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLEncoder
import java.util.Locale

/**
 * Reads things aloud. The server's British voice (Piper, when the server has it: see
 * server/README.md) is used first, so every TV sounds the same; this TV's own
 * text-to-speech is the fallback. Some Fire TV sticks have no usable engine for apps, so
 * [status] says what's working, for the Grown-ups screen, and [silent] puts a hint on the games.
 */
class Voice(context: Context, private val server: () -> Pair<String, String>?) {
    private val context = context.applicationContext

    /** What's speaking, or why nothing is, in words for a grown-up. */
    var status by mutableStateOf("Starting the voice…")
        private set

    /** Nothing can speak: the games show a hint. */
    var silent by mutableStateOf(false)
        private set

    /** The server says it has a voice (from the library). */
    var serverHasVoice = false
        set(value) {
            field = value
            refresh()
        }

    private var serverFailures = 0
    private var player: MediaPlayer? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsProblem: String? = null
    private var ttsEngine = ""
    private var ukMissing = false
    private var ttsErrors = 0

    init {
        tts = TextToSpeech(this.context) { result ->
            val t = tts
            if (result == TextToSpeech.SUCCESS && t != null) {
                ttsEngine = t.defaultEngine.orEmpty()
                val language = t.setLanguage(Locale.UK)
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // No British English: any English beats silence.
                    ukMissing = true
                    t.setLanguage(Locale.ENGLISH)
                }
                t.setSpeechRate(0.9f)
                ttsReady = true
            } else {
                ttsProblem = if (t?.engines.isNullOrEmpty()) "this TV has no text-to-speech engine" else "this TV's text-to-speech wouldn't start"
            }
            refresh()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post {
                    ttsErrors++
                    refresh()
                }
            }
        })
        // Some engines never answer at all.
        main.postDelayed({
            if (!ttsReady && ttsProblem == null) {
                ttsProblem = "this TV's text-to-speech didn't start"
                refresh()
            }
        }, 8_000)
    }

    private val main get() = Handler(Looper.getMainLooper())

    private val useServer get() = serverHasVoice && serverFailures < MAX_SERVER_FAILURES

    /** Reads [text] aloud, replacing anything still being said. */
    fun say(text: String) {
        if (text.isBlank()) return
        val address = server()
        if (useServer && address != null) play(text, address) else sayHere(text)
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tts?.stop()
    }

    fun close() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun sayHere(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wonderbox")
    }

    /** A clip from the server's voice; if it fails, this TV's own voice says it instead. */
    private fun play(text: String, address: Pair<String, String>) {
        stop()
        val (baseUrl, key) = address
        val url = baseUrl.trimEnd('/') + "/api/wonder/speech?text=" + URLEncoder.encode(text, "UTF-8")
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            p.setDataSource(context, Uri.parse(url), mapOf("X-Device-Key" to key))
            p.setOnPreparedListener { if (player === it) it.start() }
            p.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            p.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) {
                    player = null
                    failed(text)
                }
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            p.release()
            player = null
            failed(text)
        }
    }

    private fun failed(text: String) {
        serverFailures++
        refresh()
        sayHere(text)
    }

    private fun refresh() {
        val here = when {
            ttsReady -> buildString {
                append("this TV's voice")
                if (ttsEngine.isNotBlank()) append(" ($ttsEngine)")
                if (ukMissing) append(", without British English")
                if (ttsErrors > 0) append(", which has failed $ttsErrors times")
            }
            ttsProblem != null -> ttsProblem!!
            else -> "this TV's voice is starting"
        }
        status = when {
            useServer -> "Voice: the server's British voice. Fallback: $here."
            serverHasVoice -> "Voice: the server's voice isn't working, so $here."
            else -> "Voice: $here. The server has no voice of its own (see the server's README to add one)."
        }
        silent = !useServer && (ttsProblem != null || (ttsReady && ttsErrors >= 3))
    }

    companion object {
        /** After this many failed clips, the server's voice is left alone until the app restarts. */
        const val MAX_SERVER_FAILURES = 3
    }
}
