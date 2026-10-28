package com.njdyson.wonderbox.ui

import android.content.Context
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.njdyson.wonderbox.games.HangmanSetup
import com.njdyson.wonderbox.games.Jigsaw
import com.njdyson.wonderbox.games.Memory
import com.njdyson.wonderbox.games.MemorySetup
import com.njdyson.wonderbox.games.ScrambleSetup
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
import com.njdyson.wonderbox.data.Points
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.games.MathsSetup
import com.njdyson.wonderbox.games.MyWord
import com.njdyson.wonderbox.games.Quiz
import com.njdyson.wonderbox.games.QuizAnswer
import com.njdyson.wonderbox.games.QuizQuestion
import com.njdyson.wonderbox.games.FlagSetup
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
        points = 12,
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

    // Summer's photos; the last one is Jesse's only, so it never shows on her screens.
    private val photos = (1..5).map { Photo("p$it", "/api/wonder/photos/p$it/image", "/api/wonder/photos/p$it/thumb", 1600, 1200, listOf(summer.id)) } +
        Photo("p6", "/api/wonder/photos/p6/image", "/api/wonder/photos/p6/thumb", 1600, 1200, listOf(jesse.id))

    private fun app(withPhotos: Boolean = false): AppState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (withPhotos) fakePhotos(context)
        val titles = listOf("Peppa Pig: Muddy Puddles", "Numberblocks: Counting to Ten", "Hey Duggee and the Bug Badge", "Bluey: Keepy Uppy", "Alphablocks: Phonics Song", "The Gruffalo")
        val videos = titles.mapIndexed { i, title ->
            Video("v$i", "yt$i", title, thumbnail = "https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg", children = listOf("c1", "c2"), tier = if (i >= 4) TREAT else "free", section = if (i == 3) MUSIC else "videos", duration = listOf(0, 0, 0, 150, 200, 540)[i])
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

    @Test fun home() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { HomeScreen(app, summer, {}) } }
        shoot("home-summer")
    }

    @Test fun quizzes() {
        val app = app(withPhotos = true)
        val games = app.library.gamesFor(summer, photos = true).filter { it.isQuiz }
        compose.setContent { WonderBoxTheme { GamesScreen(app, summer, "Quizzes", games, {}) } }
        shoot("quizzes-summer")
    }

    @Test fun games() {
        val app = app(withPhotos = true)
        val games = app.library.gamesFor(summer, photos = true).filter { !it.isQuiz }
        compose.setContent { WonderBoxTheme { GamesScreen(app, summer, "Games", games, {}) } }
        shoot("games-summer")
    }

    @Test fun videos() {
        val app = app()
        // The first one watched all the way through: ticked and faded.
        val watched = setOf(app.library.videoShelvesFor(summer.id)[0].videos[1].youtubeId)
        compose.setContent { WonderBoxTheme { VideosScreen(app, summer, "Learning", app.library.videoShelvesFor(summer.id), {}, watched = watched) } }
        shoot("videos-summer")
    }

    @Test fun music() {
        val app = app()
        compose.setContent { WonderBoxTheme { VideosScreen(app, summer, "Music", app.library.musicShelvesFor(summer.id), {}, prices = true) } }
        shoot("music-summer")
    }

    // The treat videos, priced by length; not enough stars for the long one.
    @Test fun treatVideos() {
        val app = app()
        compose.setContent {
            WonderBoxTheme {
                VideosScreen(
                    app, summer, "Videos", app.library.treatShelvesFor(summer.id), {},
                    prices = true,
                    message = "You need 9 ⭐ for this one. Earn some in Quizzes!",
                )
            }
        }
        shoot("treat-videos-summer")
    }

    // The end of a quiz: the stars it earned.
    @Test fun finished() {
        val app = app()
        compose.setContent {
            WonderBoxTheme {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
                    Finished(summer, 5, {}, {}, app)
                }
            }
        }
        shoot("finished-stars")
    }

    @Test fun photoGrid() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { PhotoGridScreen(app, summer, {}) } }
        shoot("photos-summer")
    }

    private fun game(child: Child, key: String, name: String) {
        val app = app()
        fakePhotos(ApplicationProvider.getApplicationContext())
        val entry = app.library.gamesFor(child).first { it.key == key }
        compose.setContent { WonderBoxTheme { GameScreen(app, child, entry, starsPerAnswer = 1, onBack = {}) } }
        shoot(name)
    }

    @Test fun game() = game(jesse, "maths", "game-maths")

    @Test fun gameClock() = game(jesse, "clock", "game-clock")

    @Test fun gameFlags() = game(jesse, "flags", "game-flags")

    @Test fun gameFlagsPick() {
        val pick = jesse.copy(games = jesse.games.copy(flagSetup = FlagSetup(regions = listOf("famous"), types = listOf("flag"), choices = 4)))
        game(pick, "flags", "game-flags-pick")
    }

    @Test fun gamePhonics() {
        val phonics = jesse.copy(games = jesse.games.copy(spellingSetup = SpellingSetup(types = listOf("missing"), lists = listOf("phase2"))))
        game(phonics, "spelling", "game-phonics")
    }

    @Test fun gameOwnMix() = game(summer, "maths", "game-maths-own-mix")

    @Test fun gameOwnWords() = game(summer, "spelling", "game-spelling-own-words")

    @Test fun quizPictures() = game(summer, "quiz/q1", "game-quiz-pictures")

    @Test fun quizWords() = game(summer, "quiz/q2", "game-quiz-words")

    // Jesse has one photo of his own; Summer's five don't show here.
    @Test fun jigsawPickPhotoJesse() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, jesse, 3, {}, {}) } }
        shoot("jigsaw-pick-photo-jesse")
    }

    @Test fun jigsawPickPhoto() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, 3, {}, {}) } }
        shoot("jigsaw-pick-photo")
    }

    @Test fun jigsawCarrying() {
        val app = app(withPhotos = true)
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, 3, {}, {}, startPhoto = photos[0], random = Random(3)) } }
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
        compose.setContent { WonderBoxTheme { JigsawScreen(app, summer, 3, {}, {}, startPhoto = photos[0], random = Random(5)) } }
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

    @Test fun hangman() {
        val app = app()
        compose.setContent { WonderBoxTheme { HangmanScreen(app, jesse, 3, {}, {}, random = Random(4), startGuesses = "aei") } }
        settle()
        compose.mainClock.advanceTimeBy(600)
        shoot("hangman")
    }

    @Test fun hangmanAllLetters() {
        val app = app()
        val child = jesse.copy(games = GameSettings(hangman = HangmanSetup(lists = listOf("long"), letters = 26, balloons = 8, say = false)))
        compose.setContent { WonderBoxTheme { HangmanScreen(app, child, 3, {}, {}, random = Random(2), startGuesses = "est") } }
        settle()
        compose.mainClock.advanceTimeBy(600)
        shoot("hangman-all-letters")
    }

    @Test fun wordJumble() {
        val app = app()
        val child = summer.copy(games = summer.games.copy(scramble = ScrambleSetup(lists = listOf("short"))))
        compose.setContent { WonderBoxTheme { WordGameScreen(app, child, 3, {}, {}, random = Random(3)) } }
        settle()
        compose.mainClock.advanceTimeBy(600)
        shoot("word-jumble")
    }

    @Test fun wordJumbleLonger() {
        val app = app()
        val child = jesse.copy(games = GameSettings(scramble = ScrambleSetup(level = 2, lists = listOf("long"))))
        compose.setContent { WonderBoxTheme { WordGameScreen(app, child, 3, {}, {}, random = Random(5), startPicks = listOf(1, 3)) } }
        settle()
        compose.mainClock.advanceTimeBy(600)
        shoot("word-jumble-longer")
    }

    @Test fun makeWords() {
        val app = app()
        val child = jesse.copy(games = GameSettings(scramble = ScrambleSetup(level = 4)))
        compose.setContent { WonderBoxTheme { WordGameScreen(app, child, 3, {}, {}, random = Random(7), startPicks = listOf(0, 2), startFound = 3) } }
        settle()
        compose.mainClock.advanceTimeBy(600)
        shoot("make-words")
    }

    @Test fun memory() = memory(summer, 6, "memory")

    @Test fun memoryPhotos() = memory(
        summer.copy(games = summer.games.copy(memory = MemorySetup(pairs = 8, pictures = "both"))),
        8, "memory-photos", withPhotos = true,
    )

    /** Pairs with one pair found and another card turned over. */
    private fun memory(child: Child, seed: Int, name: String, withPhotos: Boolean = false) {
        val app = app(withPhotos)
        // The same deal as the screen's, to know where a pair is.
        val faces = deal(ApplicationProvider.getApplicationContext(), child.games.memory, app.photosFor(child.id), Random(seed)).faces
        compose.setContent { WonderBoxTheme { MemoryScreen(app, child, 3, {}, {}, random = Random(seed)) } }
        settle()
        val columns = Memory.columns(faces.size)
        val twin = faces.lastIndexOf(faces[0])
        val other = (1 until faces.size).first { it != twin }
        press(listOf(Key.DirectionCenter) + walk(0, twin, columns) + Key.DirectionCenter + walk(twin, other, columns) + Key.DirectionCenter)
        shoot(name)
    }

    /** The arrows from one place to another on a grid [columns] wide (a 3 x 3 jigsaw). */
    private fun walk(from: Int, to: Int, columns: Int = 3): List<Key> = buildList {
        repeat(to % columns - from % columns) { add(Key.DirectionRight) }
        repeat(from % columns - to % columns) { add(Key.DirectionLeft) }
        repeat(to / columns - from / columns) { add(Key.DirectionDown) }
        repeat(from / columns - to / columns) { add(Key.DirectionUp) }
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
                        // The flags are in the repo, as the server serves them.
                        val flag = Regex("/clipart/flags/([a-z-]+)\\.png").find(url)?.groupValues?.get(1)
                        if (flag != null) {
                            val bitmap = BitmapFactory.decodeFile(File("../server/pb_public/clipart/flags/$flag.png").absolutePath)
                            return SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.MEMORY)
                        }
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

    // Jesse has no photos and no quizzes of his own beyond the shared one.
    @Test fun homeJesse() {
        val app = app()
        compose.setContent { WonderBoxTheme { HomeScreen(app, jesse, {}) } }
        shoot("home-jesse")
    }
}
