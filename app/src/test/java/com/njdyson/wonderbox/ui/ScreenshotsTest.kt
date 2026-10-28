package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.MUSIC
import com.njdyson.wonderbox.data.TREAT
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.data.json
import com.njdyson.wonderbox.games.Game
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi", sdk = [34])
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    private val summer = Child("c1", "Summer", "🦄", "#FF5C7A")
    private val jesse = Child("c2", "Jesse", "🦖", "#4D96FF")

    private fun app(): AppState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val titles = listOf("Peppa Pig: Muddy Puddles", "Numberblocks: Counting to Ten", "Hey Duggee and the Bug Badge", "Bluey: Keepy Uppy", "Alphablocks: Phonics Song", "The Gruffalo")
        val videos = titles.mapIndexed { i, title ->
            Video("v$i", "aqz-KE-bpKQ", title, children = listOf("c1", "c2"), tier = if (i >= 4) TREAT else "free", section = if (i == 3) MUSIC else "videos")
        }
        context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE).edit()
            .putString("device_key", "k")
            .putString("library", json.encodeToString(Library.serializer(), Library(listOf(summer, jesse), videos)))
            .commit()
        return AppState(context)
    }

    private fun shoot(name: String) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        // Let Coil finish loading pictures on its own threads.
        repeat(30) {
            Thread.sleep(200)
            compose.mainClock.advanceTimeBy(16)
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun profiles() {
        val app = app()
        compose.setContent { WonderBoxTheme { ProfileScreen(app, null, {}, {}) } }
        shoot("profiles")
    }

    @Test fun videos() {
        val app = app()
        compose.setContent { WonderBoxTheme { VideosScreen(app, summer, {}, {}, {}) } }
        shoot("videos-summer")
    }

    @Test fun game() {
        val app = app()
        compose.setContent { WonderBoxTheme { GameScreen(app, summer, Game.Maths, {}, {}, {}, hasTreats = true) } }
        shoot("game-maths")
    }

    @Test fun settings() {
        val app = app()
        compose.setContent { WonderBoxTheme { SettingsScreen(app, 0) } }
        shoot("settings")
    }

    @Test fun gate() {
        compose.setContent { WonderBoxTheme { GrownUpGate(onPassed = {}) } }
        shoot("gate")
    }

    @Test fun videosJesse() {
        val app = app()
        compose.setContent { WonderBoxTheme { VideosScreen(app, jesse, {}, {}, {}) } }
        shoot("videos-jesse")
    }
}
