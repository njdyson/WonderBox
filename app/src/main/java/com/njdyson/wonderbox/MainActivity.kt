package com.njdyson.wonderbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Points
import com.njdyson.wonderbox.data.GameSettings
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.games.HANGMAN
import com.njdyson.wonderbox.games.JIGSAW
import com.njdyson.wonderbox.games.MEMORY
import com.njdyson.wonderbox.games.gamesFor
import com.njdyson.wonderbox.player.PlayerActivity
import com.njdyson.wonderbox.ui.Category
import com.njdyson.wonderbox.ui.GameScreen
import com.njdyson.wonderbox.ui.GamesScreen
import com.njdyson.wonderbox.ui.HomeScreen
import com.njdyson.wonderbox.ui.PhotoGridScreen
import com.njdyson.wonderbox.ui.AppState
import com.njdyson.wonderbox.ui.GrownUpGate
import com.njdyson.wonderbox.ui.HangmanScreen
import com.njdyson.wonderbox.ui.JigsawScreen
import com.njdyson.wonderbox.ui.LinkScreen
import com.njdyson.wonderbox.ui.MemoryScreen
import com.njdyson.wonderbox.ui.PhotosScreen
import com.njdyson.wonderbox.ui.ProfileScreen
import com.njdyson.wonderbox.ui.WonderBoxTheme
import com.njdyson.wonderbox.ui.SettingsScreen
import com.njdyson.wonderbox.ui.VideosScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Profiles : Screen
    /** A child's home page of categories. */
    data class Home(val childId: String) : Screen
    /** Quizzes or Games. */
    data class Games(val childId: String, val category: Category) : Screen
    /** Learning videos, Music, Exercise or the treat Videos. */
    data class Videos(val childId: String, val category: Category) : Screen
    data class PhotoGrid(val childId: String) : Screen
    data class Photos(val childId: String, val start: Int) : Screen
    /** [game] is a [com.njdyson.wonderbox.games.GameEntry] key: "maths", "spelling" or "quiz/<id>". */
    data class Playing(val childId: String, val game: String) : Screen
    data class Jigsaw(val childId: String) : Screen
    data class Hangman(val childId: String) : Screen
    data class Memory(val childId: String) : Screen
    data object Gate : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    // Bumped every time we come back to the foreground, e.g. after a video closes.
    private val resumeCount = mutableIntStateOf(0)
    private var app: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventLog.init(applicationContext)
        if (savedInstanceState == null) EventLog.log("app.start")
        setContent {
            val app = remember { AppState(applicationContext).also { this.app = it } }
            val stack = remember { mutableStateListOf<Screen>(Screen.Profiles) }
            var lastChildId by remember { mutableStateOf<String?>(null) }
            // Each screen keeps what was focused while another is on top of it.
            val saved = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()

            // The parent can change the videos from the phone at any time.
            LaunchedEffect(resumeCount.intValue, app.linked) { app.sync() }
            LaunchedEffect(app.linked) {
                while (app.linked) {
                    delay(60_000)
                    // Not while a video plays on top: the player gets the stick to itself.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) app.sync()
                }
            }

            fun open(screen: Screen) { stack.add(screen) }

            /**
             * Stars [childId] earns from [game]: per right answer for quizzes, per finish for
             * other games; 0 if the phone app says that game doesn't earn.
             */
            fun starsFrom(childId: String, game: String, perAnswer: Boolean): Int {
                val child = app.library.children.firstOrNull { it.id == childId } ?: return 0
                if (!child.games.earnsTreat(game)) return 0
                return if (perAnswer) child.games.points.answer else child.games.points.game
            }
            fun back() { if (stack.size > 1) saved.removeState(stack.removeAt(stack.lastIndex).toString()) }
            // "You need 4 stars" and the like, shown for a moment at the top of the videos.
            var notice by remember { mutableStateOf<String?>(null) }
            fun tell(text: String) {
                notice = text
                app.say(text.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!'" }.trim())
                scope.launch {
                    delay(3_500)
                    if (notice == text) notice = null
                }
            }
            /**
             * Plays [video]. Treat and music videos cost stars (by length, once it's known; at
             * least one to start), learning videos earn them as they play.
             */
            fun play(video: Video, child: Child) {
                val paid = video.isTreat || video.isMusic
                if (paid) {
                    val price = Points.lengthOf(video)?.let { child.games.points.price(it.toDouble()) } ?: 1
                    val have = Points.balance(child.id)
                    if (have < price) {
                        tell("You need $price ⭐ for this one. Earn some in Quizzes!")
                        return
                    }
                }
                app.stopSpeaking()
                val earns = !paid && child.games.earnsTreat(GameSettings.VIDEOS)
                startActivity(PlayerActivity.intent(this@MainActivity, video.youtubeId, video.title, video.channel, child.id, earns = earns, paid = paid))
            }
            // Back from a video: tick it if it was watched all through.
            LaunchedEffect(resumeCount.intValue) { app.reloadFromPlayer() }
            BackHandler(enabled = stack.size > 1) { back() }

            WonderBoxTheme {
                if (!app.linked) {
                    // Unlinked (or just unlinked from the phone): start again from the top once linked.
                    LaunchedEffect(Unit) { stack.retainAll { it == Screen.Profiles } }
                    LinkScreen(app, resumeCount.intValue)
                    return@WonderBoxTheme
                }
                val screen = stack.last()
                saved.SaveableStateProvider(screen.toString()) {
                    when (screen) {
                        Screen.Profiles -> ProfileScreen(
                            app,
                            lastChildId,
                            onPick = {
                                lastChildId = it.id
                                EventLog.log("profile.pick", it.id)
                                open(Screen.Home(it.id))
                            },
                            onGrownUps = { open(Screen.Gate) },
                        )
                        is Screen.Home -> app.library.children.firstOrNull { it.id == screen.childId }.let { child ->
                            if (child == null) {
                                // Removed in the phone app while open.
                                LaunchedEffect(Unit) { back() }
                            } else {
                                HomeScreen(app, child, onOpen = {
                                    EventLog.log("category.open", child.id, subject = it.name.lowercase())
                                    open(
                                        when (it) {
                                            Category.Quizzes, Category.Games -> Screen.Games(child.id, it)
                                            Category.Learning, Category.Music, Category.Exercise, Category.Videos -> Screen.Videos(child.id, it)
                                            Category.Photos -> Screen.PhotoGrid(child.id)
                                        },
                                    )
                                })
                            }
                        }
                        is Screen.Games -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            val games = child?.let { c ->
                                app.library.gamesFor(c, photos = app.photosFor(c.id).isNotEmpty())
                                    .filter { it.isQuiz == (screen.category == Category.Quizzes) }
                            }
                            if (child == null || games.isNullOrEmpty()) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                GamesScreen(app, child, screen.category.title, games, onGame = {
                                    open(
                                        when (it.key) {
                                            JIGSAW -> Screen.Jigsaw(child.id)
                                            HANGMAN -> Screen.Hangman(child.id)
                                            MEMORY -> Screen.Memory(child.id)
                                            else -> Screen.Playing(child.id, it.key)
                                        },
                                    )
                                })
                            }
                        }
                        is Screen.Videos -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val shelves = when (screen.category) {
                                    Category.Music -> app.library.musicShelvesFor(child.id)
                                    Category.Videos -> app.library.treatShelvesFor(child.id)
                                    Category.Exercise -> app.library.exerciseShelvesFor(child.id)
                                    else -> app.library.videoShelvesFor(child.id)
                                }
                                VideosScreen(
                                    app,
                                    child,
                                    screen.category.title,
                                    shelves,
                                    watched = app.watchedFor(child.id),
                                    prices = screen.category == Category.Videos || screen.category == Category.Music,
                                    message = notice,
                                    onPlay = { play(it, child) },
                                )
                            }
                        }
                        is Screen.Playing -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            // The game may have been turned off, or the quiz removed, from the phone.
                            val entry = child?.let { c -> app.library.gamesFor(c).firstOrNull { it.key == screen.game } }
                            if (child == null || entry == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                // Only the games the parent chose earn stars (not jigsaws or pairs, by default).
                                GameScreen(
                                    app,
                                    child,
                                    entry,
                                    starsPerAnswer = starsFrom(child.id, entry.key, perAnswer = true),
                                    onBack = { back() },
                                )
                            }
                        }
                        is Screen.Jigsaw -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val stars = starsFrom(child.id, JIGSAW, perAnswer = false)
                                JigsawScreen(
                                    app,
                                    child,
                                    stars = stars,
                                    onFinished = { Points.earn(child.id, stars, "jigsaw.finish", JIGSAW) },
                                    onBack = { back() },
                                )
                            }
                        }
                        is Screen.Hangman -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null || !child.games.hangman.on) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val stars = starsFrom(child.id, HANGMAN, perAnswer = false)
                                HangmanScreen(
                                    app,
                                    child,
                                    stars = stars,
                                    onFinished = { Points.earn(child.id, stars, "hangman.finish", HANGMAN) },
                                    onBack = { back() },
                                )
                            }
                        }
                        is Screen.Memory -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null || !child.games.memory.on) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val stars = starsFrom(child.id, MEMORY, perAnswer = false)
                                MemoryScreen(
                                    app,
                                    child,
                                    stars = stars,
                                    onFinished = { Points.earn(child.id, stars, "memory.finish", MEMORY) },
                                    onBack = { back() },
                                )
                            }
                        }
                        is Screen.PhotoGrid -> app.library.children.firstOrNull { it.id == screen.childId }.let { child ->
                            if (child == null || app.photosFor(screen.childId).isEmpty()) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                PhotoGridScreen(app, child, onOpen = { open(Screen.Photos(child.id, it)) })
                            }
                        }
                        is Screen.Photos -> PhotosScreen(app, screen.childId, screen.start)
                        Screen.Gate -> GrownUpGate(onPassed = {
                            stack.removeAt(stack.lastIndex)
                            open(Screen.Settings)
                        })
                        Screen.Settings -> SettingsScreen(app, resumeCount.intValue)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
        EventLog.flush()
    }

    override fun onDestroy() {
        app?.close()
        super.onDestroy()
    }
}
