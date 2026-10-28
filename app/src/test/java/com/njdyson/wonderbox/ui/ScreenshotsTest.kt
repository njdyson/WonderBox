package com.njdyson.wonderbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.njdyson.wonderbox.data.Photo
import com.njdyson.wonderbox.data.Photos
import com.njdyson.wonderbox.games.Jigsaw
import kotlin.random.Random
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
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.games.MathsSetup
import com.njdyson.wonderbox.games.MyWord
import com.njdyson.wonderbox.games.Quiz
import com.njdyson.wonderbox.games.QuizAnswer
import com.njdyson.wonderbox.games.QuizQuestion
import com.njdyson.wonderbox.games.SpellingSetup
import com.njdyson.wonderbox.games.gamesFor
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

    private val summer = Child(
        "c1", "Summer", "🦄", "#FF5C7A",
        games = GameSettings(
            mathsSetup = MathsSetup(types = listOf("missing", "add"), max = 20, choices = 4),
            spellingSetup = SpellingSetup(types = listOf("first"), lists = listOf("mine"), myWords = listOf(MyWord("friend", "👫"))),
        ),
    )
    private val quizzes = listOf(
        Quiz(
            "q1", "Animal sounds", "🐮", listOf("c1", "c2"), shuffle = false,
            questions = listOf(
                QuizQuestion("Which animal says moo?", answers = listOf(QuizAnswer("Cow", "🐮"), QuizAnswer("Dog", "🐶"), QuizAnswer("Sheep", "🐑")), correct = 0),
            ),
        ),
        Quiz(
            "q2", "Colours", "🎨", listOf("c1"),
            questions = listOf(QuizQuestion("What colour is a banana?", "🍌", listOf(QuizAnswer("Yellow"), QuizAnswer("Blue")))),
        ),
    )
    private val jesse = Child("c2", "Jesse", "🦖", "#4D96FF")

    private val photos = (1..5).map { Photo("p$it", "/api/wonder/photos/p$it/image", "/api/wonder/photos/p$it/thumb", 1600, 1200) }

    private fun app(withPhotos: Boolean = false): AppState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (withPhotos) fakePhotos(context)
        val titles = listOf("Peppa Pig: Muddy Puddles", "Numberblocks: Counting to Ten", "Hey Duggee and the Bug Badge", "Bluey: Keepy Uppy", "Alphablocks: Phonics Song", "The Gruffalo")
        val videos = titles.mapIndexed { i, title ->
            Video("v$i", "aqz-KE-bpKQ", title, children = listOf("c1", "c2"), tier = if (i >= 4) TREAT else "free", section = if (i == 3) MUSIC else "videos")
        }
        context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE).edit()
            .putString("device_key", "k")
            .putString("library", json.encodeToString(Library.serializer(), Library(children = listOf(summer, jesse), quizzes = quizzes, videos = videos)))
            .putString("photos", json.encodeToString(Photos.serializer(), Photos(if (withPhotos) photos else emptyList())))
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

    private fun game(child: Child, key: String, name: String) {
        val app = app()
        val entry = app.library.gamesFor(child).first { it.key == key }
        compose.setContent { WonderBoxTheme { GameScreen(app, child, entry, {}, {}, {}, hasTreats = true) } }
        shoot(name)
    }

    @Test fun game() = game(jesse, "maths", "game-maths")

    @Test fun gameOwnMix() = game(summer, "maths", "game-maths-own-mix")

    @Test fun gameOwnWords() = game(summer, "spelling", "game-spelling-own-words")

    @Test fun quizPictures() = game(summer, "quiz/q1", "game-quiz-pictures")

    @Test fun quizWords() = game(summer, "quiz/q2", "game-quiz-words")

    @Test fun videosWithJigsaw() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { VideosScreen(app, summer, {}, {}, {}) } }
        shoot("videos-jigsaw-tile")
    }

    @Test fun jigsawPickPhoto() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, true, {}, {}, {}) } }
        shoot("jigsaw-pick-photo")
    }

    @Test fun jigsawCarrying() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, true, {}, {}, {}, startPhoto = photos[0], random = Random(3)) } }
        settle()
        // Put two pieces in their places, then carry a third one step along.
        val j = Jigsaw.jumbled(3, Random(3))
        var cursor = 0
        repeat(2) {
            val slot = j.pieces.indices.first { !j.isRight(it) }
            val from = j.pieces.indexOf(slot)
            press(walk(cursor, from) + Key.DirectionCenter + walk(from, slot) + Key.DirectionCenter)
            cursor = slot
            j.swap(from, slot)
        }
        val next = j.pieces.indices.first { !j.isRight(it) }
        press(walk(cursor, next) + Key.DirectionCenter + (if (next % 3 < 2) Key.DirectionRight else Key.DirectionLeft))
        compose.mainClock.advanceTimeBy(500)
        shoot("jigsaw-carrying")
    }

    @Test fun jigsawDone() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, true, {}, {}, {}, startPhoto = photos[0], random = Random(5)) } }
        settle()
        val j = Jigsaw.jumbled(3, Random(5))
        var cursor = 0
        while (!j.solved) {
            val slot = j.pieces.indices.first { !j.isRight(it) }
            val from = j.pieces.indexOf(slot)
            press(walk(cursor, from) + Key.DirectionCenter + walk(from, slot) + Key.DirectionCenter)
            cursor = slot
            j.swap(from, slot)
        }
        compose.mainClock.advanceTimeBy(3_000)
        shoot("jigsaw-done")
    }

    /** The arrows from one place to another on a 3 x 3 jigsaw. */
    private fun walk(from: Int, to: Int): List<Key> = buildList {
        repeat(to % 3 - from % 3) { add(Key.DirectionRight) }
        repeat(from % 3 - to % 3) { add(Key.DirectionLeft) }
        repeat(to / 3 - from / 3) { add(Key.DirectionDown) }
        repeat(from / 3 - to / 3) { add(Key.DirectionUp) }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun press(keys: List<Key>) {
        keys.forEach { key ->
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.mainClock.advanceTimeBy(50)
        }
    }

    private fun settle() {
        compose.mainClock.autoAdvance = false
        repeat(30) {
            Thread.sleep(100)
            compose.mainClock.advanceTimeBy(100)
        }
    }

    /** Coil hands out made-up photos: a sunny day, in a different colour for each. */
    private fun fakePhotos(context: Context) {
        Coil.setImageLoader(
            ImageLoader.Builder(context).components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val url = chain.request.data.toString()
                        if ("/api/wonder/photos/" !in url) return chain.proceed(chain.request)
                        val n = Regex("p(\\d)").find(url)?.groupValues?.get(1)?.toInt() ?: 1
                        return SuccessResult(BitmapDrawable(context.resources, picture(n)), chain.request, DataSource.MEMORY)
                    }
                })
            }.build(),
        )
    }

    private fun picture(n: Int): Bitmap {
        val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val skies = listOf(0xFF4FC3F7, 0xFFFFB74D, 0xFF9575CD, 0xFF4DB6AC, 0xFFF06292).map { it.toInt() }
        p.shader = LinearGradient(0f, 0f, 0f, 800f, skies[(n - 1) % 5], 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1600f, 1200f, p)
        p.shader = null
        // A rainbow.
        listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA).forEachIndexed { i, col ->
            p.color = col.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 40f
            val r = 620f - i * 40f
            c.drawArc(RectF(800f - r, 900f - r, 800f + r, 900f + r), 180f, 180f, false, p)
        }
        p.style = Paint.Style.FILL
        p.color = 0xFFFFEB3B.toInt(); c.drawCircle(1350f, 230f, 130f, p)
        p.color = 0xFF66BB6A.toInt(); c.drawOval(RectF(-400f, 780f, 1000f, 1500f), p)
        p.color = 0xFF43A047.toInt(); c.drawOval(RectF(600f, 820f, 2000f, 1500f), p)
        // A little house.
        p.color = 0xFFFF7043.toInt(); c.drawRect(260f, 640f, 600f, 900f, p)
        p.color = 0xFF6D4C41.toInt()
        c.drawPath(android.graphics.Path().apply { moveTo(220f, 650f); lineTo(430f, 470f); lineTo(640f, 650f); close() }, p)
        p.color = 0xFFFFF59D.toInt(); c.drawRect(310f, 700f, 400f, 780f, p)
        p.color = 0xFF5D4037.toInt(); c.drawRect(460f, 760f, 540f, 900f, p)
        return bmp
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
