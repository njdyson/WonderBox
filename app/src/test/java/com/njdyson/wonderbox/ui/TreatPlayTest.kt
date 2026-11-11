package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.TREAT
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.data.json
import com.njdyson.wonderbox.player.PlayerWebView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A saved treat leaves the Treats section as it starts playing, so focus moves to another card
 * behind the player. That used to preload the other treat, throwing away the player that was
 * playing: stuck on "Loading…", then "Oops" for every treat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi", sdk = [34])
class TreatPlayTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val child = Child("c1", "Jesse", "🦖", "#4D96FF", games = GameSettings(season = Season.OFF))
    private val treats = listOf("Bluey", "Gruffalo", "Peppa").mapIndexed { i, title -> Video("t$i", "yt$i", title, children = listOf("c1"), tier = TREAT) }

    @After fun tearDown() = PlayerWebView.release()

    private fun playSavedTreat(title: String) {
        context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE).edit().clear()
            .putString("device_key", "k")
            .putString("library", json.encodeToString(Library.serializer(), Library(children = listOf(child), videos = treats)))
            .commit()
        val app = AppState(context)
        repeat(treats.size) { app.earnTreat("c1") }
        treats.forEach { app.saveTreat("c1", it) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WonderBoxTheme {
                VideosScreen(app, child, "Treats", app.treatsSectionFor("c1"), stars = true, onPlay = { video ->
                    // Taken out of the grid while it plays (the player does that once half of it has played).
                    app.useSavedTreat("c1", video)
                    PlayerWebView.take(context, video.youtubeId, object : PlayerWebView.Listener {
                        override fun onState(state: Int) {}
                        override fun onError(code: Int) {}
                    })
                })
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val card = compose.onNode(hasClickAction() and hasContentDescription(title))
        card.requestFocus()
        compose.mainClock.advanceTimeBy(3_000)
        card.performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(3_000)
    }

    @Test fun aTreatInTheMiddleKeepsItsPlayer() {
        playSavedTreat("Gruffalo")
        assertEquals("yt1", PlayerWebView.current)
    }

    @Test fun theFirstTreatKeepsItsPlayer() {
        playSavedTreat("Bluey")
        assertEquals("yt0", PlayerWebView.current)
    }

    @Test fun theLastTreatKeepsItsPlayer() {
        playSavedTreat("Peppa")
        assertEquals("yt2", PlayerWebView.current)
    }
}
