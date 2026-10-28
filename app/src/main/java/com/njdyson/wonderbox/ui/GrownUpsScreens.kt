package com.njdyson.wonderbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.njdyson.wonderbox.update.UpdateButton
import com.njdyson.wonderbox.update.Updater
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Keeps the children out of the grown-ups' settings: a sum a 6-year-old can't do yet,
 * with four answers to choose from. A wrong answer just asks a new one.
 */
@Composable
fun GrownUpGate(onPassed: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    val sum = remember(round) {
        val a = Random.nextInt(14, 49)
        val b = Random.nextInt(14, 49)
        val answer = a + b
        val choices = (listOf(answer) + generateSequence { answer + Random.nextInt(-12, 13) }
            .filter { it != answer && it > 0 }.distinct().take(3)).shuffled()
        Triple("$a + $b", answer, choices)
    }
    val first = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().background(Lagoon).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text("Grown-ups only", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("What is ${sum.first}?", color = Tangerine, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            sum.third.forEachIndexed { i, choice ->
                OutlinedButton(
                    onClick = {
                        if (choice == sum.second) onPassed() else {
                            wrong = true
                            round++
                        }
                    },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { Text("$choice", fontSize = 26.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
            }
        }
        Text(if (wrong) "Not quite. Try this one." else "Press BACK to go back.", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
    }
    LaunchedEffect(round) { first.requestFocusWhenReady() }
}

/** The grown-ups' settings: updates, reading aloud, and the link to the parent's account. */
@Composable
fun SettingsScreen(app: AppState, resumeCount: Int) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var confirmUnlink by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Lagoon).verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Logo()
        Text("Grown-ups", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        Column(Modifier.focusRequester(first)) { UpdateButton(resumeCount) }
        Text(
            "Build ${Updater.currentVersion} (${Updater.currentSha})",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )

        OutlinedButton(onClick = { app.chooseSpeakTitles(!app.speakTitles) }) {
            Text("Read titles aloud: ${if (app.speakTitles) "On" else "Off"}")
        }

        val children = app.library.children.size
        val videos = app.library.videos.size
        Text(
            "Linked to ${app.serverUrl.removePrefix("https://")}: $children ${if (children == 1) "child" else "children"}, " +
                "$videos ${if (videos == 1) "video" else "videos"}. Add and change them in the phone app.",
            color = Color.White,
            fontSize = 14.sp,
        )
        app.syncError?.let { Text("Last refresh failed: $it", color = Coral, fontSize = 14.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { scope.launch { app.sync() } }) { Text("Refresh now") }
            if (confirmUnlink) {
                Button(onClick = { scope.launch { app.unlink() } }) { Text("Yes, unlink this TV") }
            } else {
                OutlinedButton(onClick = { confirmUnlink = true }) { Text("Unlink this TV") }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
}
