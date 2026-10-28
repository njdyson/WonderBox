package com.njdyson.wonderbox.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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
    private var ttsErrors = 0
    private var ttsWorked = false
    private var lastError: Int? = null

    /** Ways to speak this engine offers, best first; on an error the next one is tried. */
    private var choices: List<Choice> = emptyList()
    private var choice = -1
    private var lastText = ""
    private var retried = false
    private var utterances = 0

    private class Choice(val label: String, val british: Boolean, val apply: (TextToSpeech) -> Boolean)

    init {
        tts = TextToSpeech(this.context) { result ->
            val t = tts
            if (result == TextToSpeech.SUCCESS && t != null) {
                ttsEngine = t.defaultEngine.orEmpty()
                choices = choicesFor(t)
                if (!useNext(t)) ttsProblem = "this TV's text-to-speech has no language it can use"
                t.setSpeechRate(0.9f)
                ttsReady = true
            } else {
                ttsProblem = if (t?.engines.isNullOrEmpty()) "this TV has no text-to-speech engine" else "this TV's text-to-speech wouldn't start"
            }
            refresh()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            // Only a finished sentence counts: some engines start and then fail.
            override fun onDone(utteranceId: String?) {
                main.post {
                    ttsWorked = true
                    refresh()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { ttsFailed(utteranceId, errorCode) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { ttsFailed(utteranceId, null) }
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

    /**
     * British English first, then American, then any English, then whatever the engine
     * likes best. Fire TV's Ivona engine often has only American English, or says it has
     * a language and then fails to speak it, so every option is worth a go before giving up.
     */
    private fun choicesFor(t: TextToSpeech): List<Choice> {
        val voices = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedBy { if (it.isNetworkConnectionRequired) 1 else 0 }
        fun voicesFor(match: (Locale) -> Boolean, british: Boolean) = voices.filter { match(it.locale) }.take(3).map { v ->
            Choice("${v.locale.displayName} (${v.name})", british) { it.setVoice(v) == TextToSpeech.SUCCESS }
        }
        fun language(locale: Locale, british: Boolean) = Choice(locale.displayName, british) {
            it.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
        val english = { l: Locale -> l.language == "en" }
        val list = buildList {
            addAll(voicesFor({ english(it) && it.country == "GB" }, true))
            add(language(Locale.UK, true))
            addAll(voicesFor({ english(it) && it.country == "US" }, false))
            add(language(Locale.US, false))
            addAll(voicesFor({ english(it) && it.country != "GB" && it.country != "US" }, false))
            add(language(Locale.ENGLISH, false))
            runCatching { t.defaultVoice }.getOrNull()?.let { v ->
                add(Choice("${v.locale.displayName} (${v.name}, this TV's usual voice)", false) { it.setVoice(v) == TextToSpeech.SUCCESS })
            }
            add(language(Locale.getDefault(), false))
        }
        return list.distinctBy { it.label }
    }

    /** Switches to the next way of speaking that the engine accepts; false when there's none left. */
    private fun useNext(t: TextToSpeech): Boolean {
        while (choice + 1 < choices.size) {
            choice++
            if (runCatching { choices[choice].apply(t) }.getOrDefault(false)) return true
        }
        return false
    }

    /** An utterance failed: try the next language with the same words, once per request. */
    private fun ttsFailed(utteranceId: String?, errorCode: Int?) {
        ttsErrors++
        if (errorCode != null) lastError = errorCode
        val t = tts
        if (!ttsWorked && !retried && utteranceId == "wonderbox$utterances" && t != null && useNext(t)) {
            retried = true
            speakHere(lastText)
        }
        refresh()
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
        lastText = text
        retried = false
        speakHere(text)
    }

    private fun speakHere(text: String) {
        if (!ttsReady) return
        utterances++
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "wonderbox$utterances")
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
            ttsProblem != null -> ttsProblem!!
            ttsReady -> buildString {
                append("this TV's voice")
                if (ttsEngine.isNotBlank()) append(" ($ttsEngine)")
                choices.getOrNull(choice)?.let {
                    append(" in ${it.label}")
                    if (!it.british) append(", as it has no British English")
                }
                if (ttsErrors > 0) {
                    append(if (ttsWorked) ", which has failed $ttsErrors times" else ", which hasn't spoken yet: it failed $ttsErrors times")
                    lastError?.let { append(" (${errorName(it)})") }
                    if (!ttsWorked) append(" after trying ${choice + 1} of ${choices.size} languages")
                }
            }
            else -> "this TV's voice is starting"
        }
        status = when {
            useServer -> "Voice: the server's British voice. Fallback: $here."
            serverHasVoice -> "Voice: the server's voice isn't working, so $here."
            else -> "Voice: $here. The server has no voice of its own (see the server's README to add one)."
        }
        silent = !useServer && (ttsProblem != null || (ttsReady && !ttsWorked && ttsErrors >= 3))
    }

    private fun errorName(code: Int) = when (code) {
        TextToSpeech.ERROR_SYNTHESIS -> "synthesis error"
        TextToSpeech.ERROR_SERVICE -> "service error"
        TextToSpeech.ERROR_OUTPUT -> "audio output error"
        TextToSpeech.ERROR_NETWORK -> "network error"
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "network timeout"
        TextToSpeech.ERROR_INVALID_REQUEST -> "invalid request"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "voice data not installed"
        else -> "error $code"
    }

    companion object {
        /** After this many failed clips, the server's voice is left alone until the app restarts. */
        const val MAX_SERVER_FAILURES = 3
    }
}
