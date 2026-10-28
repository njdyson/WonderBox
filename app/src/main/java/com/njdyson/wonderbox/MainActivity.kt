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
import androidx.compose.runtime.setValue
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.games.Game
import com.njdyson.wonderbox.player.PlayerActivity
import com.njdyson.wonderbox.ui.GameScreen
import com.njdyson.wonderbox.ui.AppState
import com.njdyson.wonderbox.ui.GrownUpGate
import com.njdyson.wonderbox.ui.LinkScreen
import com.njdyson.wonderbox.ui.ProfileScreen
import com.njdyson.wonderbox.ui.WonderBoxTheme
import com.njdyson.wonderbox.ui.SettingsScreen
import com.njdyson.wonderbox.ui.VideosScreen
import kotlinx.coroutines.delay

private sealed interface Screen {
    data object Profiles : Screen
    data class Videos(val childId: String) : Screen
    data class Playing(val childId: String, val game: Game) : Screen
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
            // Just won a treat: the videos screen goes straight to the treats.
            var pickingTreat by remember { mutableStateOf(false) }

            // The parent can change the videos from the phone at any time.
            LaunchedEffect(resumeCount.intValue, app.linked) { app.sync() }
            LaunchedEffect(app.linked) {
                while (app.linked) {
                    delay(60_000)
                    app.sync()
                }
            }

            fun open(screen: Screen) { stack.add(screen) }
            fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            BackHandler(enabled = stack.size > 1) { back() }

            WonderBoxTheme {
                if (!app.linked) {
                    // Unlinked (or just unlinked from the phone): start again from the top once linked.
                    LaunchedEffect(Unit) { stack.retainAll { it == Screen.Profiles } }
                    LinkScreen(app, resumeCount.intValue)
                    return@WonderBoxTheme
                }
                when (val screen = stack.last()) {
                    Screen.Profiles -> ProfileScreen(
                        app,
                        lastChildId,
                        onPick = {
                            lastChildId = it.id
                            EventLog.log("profile.pick", it.id)
                            open(Screen.Videos(it.id))
                        },
                        onGrownUps = { open(Screen.Gate) },
                    )
                    is Screen.Videos -> {
                        val child = app.library.children.firstOrNull { it.id == screen.childId }
                        if (child == null) {
                            // Removed in the phone app while open.
                            LaunchedEffect(Unit) { back() }
                        } else {
                            VideosScreen(
                                app,
                                child,
                                onPlay = {
                                    app.stopSpeaking()
                                    startActivity(PlayerActivity.intent(this@MainActivity, it.youtubeId, it.title, it.channel, child.id))
                                },
                                onGame = { open(Screen.Playing(child.id, it)) },
                                focusTreats = pickingTreat,
                                onTreatsFocused = { pickingTreat = false },
                            )
                        }
                    }
                    is Screen.Playing -> {
                        val child = app.library.children.firstOrNull { it.id == screen.childId }
                        if (child == null) {
                            LaunchedEffect(Unit) { back() }
                        } else {
                            val hasTreats = app.library.shelvesFor(child.id).any { it.treat }
                            GameScreen(
                                app,
                                child,
                                screen.game,
                                hasTreats = hasTreats,
                                onFinished = { if (hasTreats) app.earnTreat(child.id) },
                                onPickTreat = {
                                    pickingTreat = true
                                    back()
                                },
                                onBack = { back() },
                            )
                        }
                    }
                    Screen.Gate -> GrownUpGate(onPassed = {
                        stack.removeAt(stack.lastIndex)
                        open(Screen.Settings)
                    })
                    Screen.Settings -> SettingsScreen(app, resumeCount.intValue)
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
