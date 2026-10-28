package com.njdyson.wonderbox.player

import android.annotation.SuppressLint
import com.njdyson.wonderbox.BuildConfig
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The WebView holding YouTube's embedded player, made ahead of time so a video starts
 * without the wait for the WebView, YouTube's scripts and the first seconds of video.
 *
 * The videos grid calls [preload] once a video has been focused for a moment: the player loads muted, plays
 * until it starts, then pauses at the start, which has YouTube buffer the opening seconds.
 * [PlayerActivity] then [take]s it and plays from there. A video that isn't watched costs
 * only those few seconds of video.
 *
 * Two things for smooth playback on a Fire TV Stick: YouTube is steered to H.264 video,
 * which every stick decodes in hardware (VP9 and AV1 may be decoded in software, which can't
 * keep up with high quality video), and a preloaded player is given the screen's size, so
 * YouTube picks the right quality while preloading instead of switching up once it's shown.
 */
@SuppressLint("StaticFieldLeak") // Made with the application context, never an activity.
object PlayerWebView {

    /** Player events, as YouTube's states (1 playing, 2 paused, 3 buffering, 0 ended) or an error. */
    interface Listener {
        fun onState(state: Int)
        fun onError(code: Int)
        fun onStats(stats: Stats) {}
    }

    /** How the video is playing, from the page's video element every couple of seconds. */
    data class Stats(
        val width: Int,
        val height: Int,
        val codec: String,
        val dropped: Int,
        val frames: Int,
        // Seconds of video downloaded ahead of where it's playing.
        val ahead: Double,
    )

    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var videoId: String? = null
    private var listener: Listener? = null
    private var preloading = false
    // An error while preloading, e.g. the video doesn't allow embedding; passed on by take().
    private var error: Int? = null

    /** Starts loading [id] in the background, replacing any other preloaded video. */
    fun preload(context: Context, id: String) {
        if (id == videoId) return
        release()
        preloading = true
        load(context, id)
    }

    /**
     * The player for [id] with [listener] attached: the preloaded one if it's for this video
     * (the caller then plays it), else a new one that plays as soon as it loads.
     */
    fun take(context: Context, id: String, listener: Listener): WebView {
        if (id != videoId) {
            release()
            load(context, id)
        }
        preloading = false
        this.listener = listener
        error?.let { code -> main.post { listener.onError(code) } }
        return view!!
    }

    /** Throws away the player, e.g. when leaving the videos grid or closing the player. */
    fun release() {
        view?.apply {
            (parent as? ViewGroup)?.removeView(this)
            loadUrl("about:blank")
            destroy()
        }
        view = null
        videoId = null
        listener = null
        preloading = false
        error = null
        position = 0.0
        duration = 0.0
        stats = null
    }

    /** The latest [Stats] for the current video, if the page has sent any. */
    var stats: Stats? = null
        private set

    /** Whether the H.264 and no-suggestions tweaks are on (they need a WebView that can run scripts in its frames). */
    val playerTweaks get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** The system WebView's version, which is what really plays the video. */
    fun webViewVersion(context: Context): String =
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "unknown"

    /** Where the video was and how long it is (seconds), as of the last state change. */
    var position = 0.0
        private set
    var duration = 0.0
        private set

    fun js(code: String) = view?.evaluateJavascript("if (window.player && player.getPlayerState) { $code }", null)

    @SuppressLint("SetJavaScriptEnabled")
    private fun load(context: Context, id: String) {
        videoId = id
        error = null
        view = WebView(context.applicationContext).apply {
            // The remote is handled by PlayerActivity, not the page.
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Events(), "Android")
            if (playerTweaks) WebViewCompat.addDocumentStartJavaScript(this, FRAME_SCRIPT, YOUTUBE_ORIGINS)
            // Full screen size even before it's on screen (preloading), so YouTube picks the quality it will play at.
            val screen = context.resources.displayMetrics
            measure(
                View.MeasureSpec.makeMeasureSpec(screen.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, screen.widthPixels, screen.heightPixels)
            // YouTube refuses embeds with no referrer, so the page gets our server's address.
            loadDataWithBaseURL("$ORIGIN/", playerPage(id), "text/html", "utf-8", null)
        }
    }

    private class Events {
        @JavascriptInterface
        fun onReady() {
            // Preloading: buffer the opening seconds silently. Otherwise play straight away.
            main.post { js(if (preloading) "player.mute(); player.playVideo();" else "player.playVideo();") }
        }

        @JavascriptInterface
        fun onTime(current: Double, length: Double) {
            main.post {
                position = current
                duration = length
            }
        }

        @JavascriptInterface
        fun onState(value: Int) {
            main.post {
                if (preloading && value == 1) js("player.pauseVideo(); player.seekTo(0, true);")
                listener?.onState(value)
            }
        }

        // Called from inside YouTube's frame, by FRAME_SCRIPT.
        @JavascriptInterface
        fun onStats(width: Int, height: Int, codec: String, dropped: Int, frames: Int, ahead: Double) {
            main.post {
                val latest = Stats(width, height, codec, dropped, frames, ahead)
                stats = latest
                listener?.onStats(latest)
            }
        }

        @JavascriptInterface
        fun onError(code: Int) {
            main.post {
                error = code
                listener?.onError(code)
            }
        }
    }

    private val ORIGIN = BuildConfig.SERVER_URL.trimEnd('/')

    private val YOUTUBE_ORIGINS = setOf("https://www.youtube.com", "https://www.youtube-nocookie.com")

    /**
     * Runs at the start of YouTube's own player frame (our page can't reach into it). It says
     * VP9, AV1 and VP8 video can't be played, so YouTube sends H.264, and every two seconds
     * reports the video's size, codec, dropped frames and buffer to [Events.onStats]. It also
     * hides the suggested videos YouTube puts over the picture: the "end screen" tiles in a
     * video's last seconds, cards, and the end-of-video wall.
     */
    private val FRAME_SCRIPT = """
(function () {
  var blocked = /av01|vp0?9|vp8/i;
  var hide = document.createElement('style');
  hide.textContent = '.ytp-ce-element, .ytp-cards-teaser, .ytp-cards-button, .ytp-endscreen-content, .ytp-videowall-still,' +
    ' .ytp-pause-overlay, .ytp-suggestion-set, .ytp-show-cards-title, .ytp-watermark, .iv-branding { display: none !important; }';
  // There may be no document to add it to yet, this early.
  if (document.documentElement) document.documentElement.appendChild(hide);
  else document.addEventListener('DOMContentLoaded', function () { document.head.appendChild(hide); });
  var codec = '';
  var MS = window.MediaSource;
  if (MS) {
    var supported = MS.isTypeSupported.bind(MS);
    MS.isTypeSupported = function (type) { return blocked.test(type) ? false : supported(type); };
    var add = MS.prototype.addSourceBuffer;
    MS.prototype.addSourceBuffer = function (type) {
      if (/^video/i.test(type)) codec = type;
      return add.call(this, type);
    };
  }
  if (window.SourceBuffer && SourceBuffer.prototype.changeType) {
    var change = SourceBuffer.prototype.changeType;
    SourceBuffer.prototype.changeType = function (type) {
      if (/^video/i.test(type)) codec = type;
      return change.call(this, type);
    };
  }
  var canPlay = HTMLMediaElement.prototype.canPlayType;
  HTMLMediaElement.prototype.canPlayType = function (type) { return blocked.test(type) ? '' : canPlay.call(this, type); };
  var caps = navigator.mediaCapabilities;
  if (caps && caps.decodingInfo) {
    var info = caps.decodingInfo.bind(caps);
    caps.decodingInfo = function (config) {
      var type = config && config.video && config.video.contentType;
      if (type && blocked.test(type)) return Promise.resolve({ supported: false, smooth: false, powerEfficient: false });
      return info(config);
    };
  }
  setInterval(function () {
    var video = document.querySelector('video');
    if (!video || !window.Android || !Android.onStats) return;
    var quality = video.getVideoPlaybackQuality ? video.getVideoPlaybackQuality() : null;
    var ahead = 0;
    for (var i = 0; i < video.buffered.length; i++) {
      if (video.buffered.start(i) <= video.currentTime && video.currentTime <= video.buffered.end(i)) ahead = video.buffered.end(i) - video.currentTime;
    }
    Android.onStats(video.videoWidth, video.videoHeight, codec.replace(/^video\/[^;]*;\s*codecs=/i, '').replace(/"/g, ''),
      quality ? quality.droppedVideoFrames : 0, quality ? quality.totalVideoFrames : 0, ahead);
  }, 2000);
})();
""".trimIndent()

    private fun playerPage(videoId: String) = """
<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html, body { margin: 0; height: 100%; background: #000; overflow: hidden; } #player { width: 100%; height: 100%; }</style>
</head><body>
<div id="player"></div>
<script>
  var player;
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%', height: '100%', videoId: '$videoId',
      playerVars: { controls: 0, disablekb: 1, fs: 0, rel: 0, iv_load_policy: 3, playsinline: 1, modestbranding: 1, origin: '$ORIGIN' },
      events: {
        onReady: function () { Android.onReady(); },
        onStateChange: function (e) { Android.onTime(player.getCurrentTime() || 0, player.getDuration() || 0); Android.onState(e.data); },
        onError: function (e) { Android.onError(e.data); }
      }
    });
  }
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>
""".trimIndent()
}
