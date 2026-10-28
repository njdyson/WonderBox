package com.njdyson.wonderbox.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Plays a YouTube video full screen, using the embedded player in [PlayerWebView]
 * (preloaded by the videos grid when it can be). It's a plain activity with the WebView as
 * its content, not a Compose screen: inside Compose, Fire TV played the sound but showed no
 * picture.
 *
 * The remote is handled here rather than by the page: OK pauses, left and right skip 10
 * seconds, Back closes. Nothing ever leads out to YouTube: when paused or finished, a cover
 * hides YouTube's suggestions, and a video that won't play gets a friendly screen, not the
 * YouTube app.
 */
class PlayerActivity : Activity(), PlayerWebView.Listener {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var hintBar: View
    private lateinit var hint: TextView
    private lateinit var cover: View
    private lateinit var coverIcon: TextView
    private lateinit var coverTitle: TextView
    private lateinit var coverHint: TextView

    // -1 until the player loads; then YouTube's player states (1 playing, 2 paused, 3 buffering, 0 ended).
    private var state = -1
    // Once it has finished or failed, the cover stays and any key closes the player.
    private var done = false

    private val hideHint = Runnable { if (state == 1) hintBar.visibility = View.GONE }

    // A video that hasn't started within 20 seconds isn't going to.
    private val giveUp = Runnable { if (state == -1 || state == 3) cantPlay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val dp = resources.displayMetrics.density

        val webView = PlayerWebView.take(this, videoId, this)
        // A preloaded player is paused (and muted) at the start: play it. A new one plays once it loads.
        js("player.unMute(); player.playVideo();")

        hint = TextView(this).apply {
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 18f
        }
        hintBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(217, 0, 0, 0)))
            setPadding((48 * dp).toInt(), (28 * dp).toInt(), (48 * dp).toInt(), (28 * dp).toInt())
            addView(TextView(context).apply {
                text = title
                setTextColor(SUNSHINE)
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(hint)
        }

        // Covers the whole player when paused, finished or broken, so YouTube's own
        // "more videos" panels can never be seen, let alone chosen.
        coverIcon = TextView(this).apply { textSize = 96f; gravity = Gravity.CENTER }
        coverTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        coverHint = TextView(this).apply {
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 20f
            gravity = Gravity.CENTER
        }
        cover = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(240, 0x24, 0x16, 0x4F))
            val gap = (16 * dp).toInt()
            addView(coverIcon)
            addView(coverTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = gap })
            addView(coverHint, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = gap })
            visibility = View.GONE
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(hintBar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
            addView(cover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
        showHint()
        main.postDelayed(giveUp, 20_000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (done) {
                if (event.keyCode in OK_KEYS) finish()
                return true
            }
            when (event.keyCode) {
                in OK_KEYS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                    js("if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo();")
                KeyEvent.KEYCODE_MEDIA_PLAY -> js("player.playVideo();")
                KeyEvent.KEYCODE_MEDIA_PAUSE -> js("player.pauseVideo();")
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> js("player.seekTo(Math.max(0, player.getCurrentTime() - 10), true);")
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> js("player.seekTo(player.getCurrentTime() + 10, true);")
                else -> return super.dispatchKeyEvent(event)
            }
            showHint()
        }
        return true
    }

    // Pause when the TV goes to the home screen or sleeps, so the video isn't heard in the background.
    override fun onStop() {
        super.onStop()
        js("player.pauseVideo();")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        PlayerWebView.release()
        super.onDestroy()
    }

    private fun js(code: String) = PlayerWebView.js(code)

    private fun showHint() {
        hint.text = when (state) {
            -1, 3 -> "Loading…"
            else -> "OK  ⏯    ◀ ▶  skip    BACK  close"
        }
        hintBar.visibility = if (done || state == 2) View.GONE else View.VISIBLE
        main.removeCallbacks(hideHint)
        if (state == 1) main.postDelayed(hideHint, 3_000)
    }

    private fun showCover(icon: String, title: String, hint: String) {
        coverIcon.text = icon
        coverTitle.text = title
        coverHint.text = hint
        cover.visibility = View.VISIBLE
        hintBar.visibility = View.GONE
    }

    /** Embedding blocked, an error, or it never started. Never the YouTube app: that's a way out. */
    private fun cantPlay() {
        if (done) return
        done = true
        js("player.stopVideo();")
        showCover("🙈", "Oops! This one won't play.", "Press OK to choose another")
    }

    private fun finished() {
        done = true
        showCover("⭐", "All done!", "Press OK to choose another")
        // Back to the videos by itself if nobody presses anything.
        main.postDelayed({ finish() }, 8_000)
    }

    override fun onState(state: Int) {
        this.state = state
        if (done) return
        when (state) {
            0 -> finished()
            2 -> showCover("⏸", "Paused", "Press OK to keep watching")
            else -> {
                cover.visibility = View.GONE
                showHint()
            }
        }
    }

    override fun onError(code: Int) = cantPlay()

    companion object {
        private const val EXTRA_VIDEO_ID = "videoId"
        private const val EXTRA_TITLE = "title"
        private val SUNSHINE = Color.rgb(0xFF, 0xD2, 0x3F)
        private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

        fun intent(context: Context, videoId: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_TITLE, title)
    }
}
