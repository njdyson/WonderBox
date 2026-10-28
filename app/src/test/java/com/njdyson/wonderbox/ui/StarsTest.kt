package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.Points
import com.njdyson.wonderbox.data.PointsSetup
import com.njdyson.wonderbox.data.Store
import com.njdyson.wonderbox.data.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class StarsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val summer = Child("c1", "Summer", points = 4, games = GameSettings(points = PointsSetup(videoMinutes = 3, videoCap = 5)))

    private fun app(edit: android.content.SharedPreferences.Editor.() -> Unit = {}): AppState {
        context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE).edit().clear()
            .putString("device_key", "k")
            .putString("library", json.encodeToString(Library.serializer(), Library(children = listOf(summer))))
            .apply(edit)
            .commit()
        return AppState(context)
    }

    @Test fun earningAndSpendingChangeTheJarOnTop() {
        val app = app()
        assertEquals(4, app.starsFor("c1"))
        Points.earn("c1", 3, "quiz.answer", "maths")
        assertEquals(7, app.starsFor("c1"))
        assertTrue(Points.spend("c1", 5, "video.play", "yt1"))
        assertEquals(2, app.starsFor("c1"))
        // Not enough: nothing taken.
        assertFalse(Points.spend("c1", 3, "video.play", "yt2"))
        assertEquals(2, app.starsFor("c1"))
    }

    @Test fun theServerCountsChangesSentBeforeItWasAsked() {
        app()
        Points.earn("c1", 3, "quiz.answer", "maths")
        val sentChange = Store(context).pointsChanges.single()
        Points.sent(setOf(sentChange.id))
        shadowOf(Looper.getMainLooper()).idle()
        Points.earn("c1", 2, "quiz.answer", "maths")
        // The server has the first (sent before this library was asked for), not the second.
        Points.fromServer(Library(children = listOf(summer.copy(points = 7))), askedAt = System.currentTimeMillis() + 1)
        assertEquals(9, Points.balance("c1"))
        assertEquals(1, Store(context).pointsChanges.size)
    }

    @Test fun pricesAndLearningStarsGoByLength() {
        val rates = PointsSetup()
        assertEquals(1, rates.price(20.0))
        assertEquals(4, rates.price(3 * 60 + 1.0))
        assertEquals(0, rates.earned(2 * 60.0))
        assertEquals(2, rates.earned(6 * 60.0 + 30))
    }

    @Test fun theServersLengthComesFirst() {
        app()
        val video = com.njdyson.wonderbox.data.Video("v1", "yt1", duration = 200)
        assertEquals(200, Points.lengthOf(video))
        // Learnt on this TV: used only while the server has no length.
        Points.learnDuration("yt2", 90.0)
        assertEquals(90, Points.lengthOf(video.copy(youtubeId = "yt2", duration = 0)))
        assertEquals(null, Points.lengthOf(video.copy(youtubeId = "yt3", duration = 0)))
    }

    @Test fun learningVideosEarnUpToTheDailyCap() {
        app()
        // 12 minutes at one star per 3: 4 stars.
        assertEquals(4, Points.earnFromVideo(summer, 12 * 60.0, "yt1"))
        // Another 12, but only 1 left of today's 5.
        assertEquals(1, Points.earnFromVideo(summer, 12 * 60.0, "yt2"))
        assertEquals(0, Points.earnFromVideo(summer, 12 * 60.0, "yt3"))
        assertEquals(9, Points.balance("c1"))
    }

    @Test fun oldTreatsBecomeStarsOnce() {
        app { putString("saved_treats", """{"c1":["v1","v2"]}""").putString("treats", """{"c1":1}""") }.let {
            assertEquals(4 + 3 * AppState.OLD_TREAT_STARS, it.starsFor("c1"))
        }
        assertEquals(4 + 3 * AppState.OLD_TREAT_STARS, AppState(context).starsFor("c1"))
    }

    // What the player records while it's on top, picked up when the main screen comes back.
    @Test fun aWatchedVideoIsTicked() {
        val app = app()
        assertTrue(Store(context).markWatched("c1", "yt1"))
        assertFalse(Store(context).markWatched("c1", "yt1"))
        app.reloadFromPlayer()
        assertEquals(setOf("yt1"), app.watchedFor("c1"))
    }
}
