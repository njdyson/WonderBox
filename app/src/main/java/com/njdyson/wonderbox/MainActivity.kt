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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.games.JIGSAW
import com.njdyson.wonderbox.games.gamesFor
import com.njdyson.wonderbox.player.PlayerActivity
import com.njdyson.wonderbox.ui.Category
import com.njdyson.wonderbox.ui.GameScreen
import com.njdyson.wonderbox.ui.GamesScreen
import com.njdyson.wonderbox.ui.HomeScreen
import com.njdyson.wonderbox.ui.PhotoGridScreen
import com.njdyson.wonderbox.ui.AppState
import com.njdyson.wonderbox.ui.GrownUpGate
import com.njdyson.wonderbox.ui.JigsawScreen
import com.njdyson.wonderbox.ui.LinkScreen
import com.njdyson.wonderbox.ui.PhotosScreen
import com.njdyson.wonderbox.ui.ProfileScreen
import com.njdyson.wonderbox.ui.WonderBoxTheme
import com.njdyson.wonderbox.ui.SettingsScreen
import com.njdyson.wonderbox.ui.VideosScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.json.put

private sealed interface Screen {
    data object Profiles : Screen
    /** A child's home page of categories. */
    data class Home(val childId: String) : Screen
    /** Quizzes or Games. */
    data class Games(val childId: String, val category: Category) : Screen
    /** Videos or Music. */
    data class Videos(val childId: String, val category: Category) : Screen
    /** The treats, picked from straight after winning one. */
    data class Treats(val childId: String) : Screen
    data class PhotoGrid(val childId: String) : Screen
    data class Photos(val childId: String, val start: Int) : Screen
    /** [game] is a [com.njdyson.wonderbox.games.GameEntry] key: "maths", "spelling" or "quiz/<id>". */
    data class Playing(val childId: String, val game: String) : Screen
    data class Jigsaw(val childId: String) : Screen
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
            fun back() { if (stack.size > 1) saved.removeState(stack.removeAt(stack.lastIndex).toString()) }
            // Won a treat: the game gives way to the treats, so Back from them goes to the Quizzes or Games.
            fun pickTreat(childId: String) {
                saved.removeState(stack.last().toString())
                stack[stack.lastIndex] = Screen.Treats(childId)
            }
            fun play(video: Video, childId: String) {
                app.stopSpeaking()
                startActivity(PlayerActivity.intent(this@MainActivity, video.youtubeId, video.title, video.channel, childId))
            }
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
                                            Category.Treats -> Screen.Treats(child.id)
                                            Category.Quizzes, Category.Games -> Screen.Games(child.id, it)
                                            Category.Videos, Category.Music -> Screen.Videos(child.id, it)
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
                                    if (it.key == JIGSAW) open(Screen.Jigsaw(child.id)) else open(Screen.Playing(child.id, it.key))
                                })
                            }
                        }
                        is Screen.Videos -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val shelves = if (screen.category == Category.Music) app.library.musicShelvesFor(child.id) else app.library.videoShelvesFor(child.id)
                                VideosScreen(app, child, screen.category.title, shelves, onPlay = { play(it, child.id) })
                            }
                        }
                        is Screen.Treats -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            val shelves = child?.let { app.library.treatShelvesFor(it.id) }.orEmpty()
                            if (child == null || shelves.isEmpty() || app.treatsFor(child.id) <= 0) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                VideosScreen(app, child, "Pick a treat!", shelves, picking = true, onPlay = { video ->
                                    if (app.spendTreat(child.id)) {
                                        EventLog.log("reward.unlock", child.id, subject = video.youtubeId) {
                                            put("reason", "treat.pick")
                                            put("tier", video.tier)
                                            put("title", video.title)
                                        }
                                        play(video, child.id)
                                    }
                                    // That was the last one: after the video they're back where they won it.
                                    if (app.treatsFor(child.id) <= 0) back()
                                })
                            }
                        }
                        is Screen.Playing -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            // The game may have been turned off, or the quiz removed, from the phone.
                            val entry = child?.let { c -> app.library.gamesFor(c).firstOrNull { it.key == screen.game } }
                            if (child == null || entry == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val hasTreats = app.library.shelvesFor(child.id).any { it.treat }
                                GameScreen(
                                    app,
                                    child,
                                    entry,
                                    hasTreats = hasTreats,
                                    onFinished = {
                                        if (hasTreats) {
                                            app.earnTreat(child.id)
                                            // A treat to pick; reward.unlock is logged when it's picked.
                                            EventLog.log("reward.earn", child.id, subject = "treat") {
                                                put("reason", "quiz.finish")
                                                put("game", screen.game)
                                            }
                                        }
                                    },
                                    onPickTreat = { pickTreat(child.id) },
                                    onBack = { back() },
                                )
                            }
                        }
                        is Screen.Jigsaw -> {
                            val child = app.library.children.firstOrNull { it.id == screen.childId }
                            if (child == null) {
                                LaunchedEffect(Unit) { back() }
                            } else {
                                val hasTreats = app.library.shelvesFor(child.id).any { it.treat }
                                JigsawScreen(
                                    app,
                                    child,
                                    hasTreats = hasTreats,
                                    onFinished = {
                                        if (hasTreats) {
                                            app.earnTreat(child.id)
                                            EventLog.log("reward.earn", child.id, subject = "treat") {
                                                put("reason", "jigsaw.finish")
                                                put("game", "jigsaw")
                                            }
                                        }
                                    },
                                    onPickTreat = { pickTreat(child.id) },
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
