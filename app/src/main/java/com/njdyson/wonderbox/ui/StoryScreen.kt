package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.games.STORY
import com.njdyson.wonderbox.games.SimpleStory
import com.njdyson.wonderbox.games.StoryPart
import com.njdyson.wonderbox.games.StoryPick
import com.njdyson.wonderbox.games.StoryPictures
import com.njdyson.wonderbox.games.StorySetup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.random.Random

/** How long a child waits for the server's writing before the TV tells that part itself. */
private const val WAIT_MS = 20_000L

/** The pencil always writes for a moment after a pick, even when the part is ready, so the pick feels heard. */
private const val MIN_WRITING_MS = 900L

private val Paper = Color(0xFFFFF6E5)

/**
 * The story maker: the child picks one of three pictures for each part of a story (who it's
 * about, where they go, what happens, how it ends) and the story is written as they go, read
 * aloud and shown on a page. The server (Gemini) writes the three possible next parts as soon
 * as the pictures are shown, so the chosen one is usually ready; while it isn't, a pencil
 * writes on the page. Without the server, the TV tells a simple story of its own.
 * At the end the whole story can be heard again.
 */
@Composable
fun StoryScreen(
    app: AppState,
    child: Child,
    hasTreats: Boolean,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    random: Random = Random,
    /** A story already under way (for screenshots). */
    startParts: List<StoryPart> = emptyList(),
    /** Show the pencil writing (for screenshots). */
    startWriting: Boolean = false,
) {
    val setup = child.games.story
    val turns = setup.turns.coerceIn(StorySetup.TURNS.first, StorySetup.TURNS.last)
    val scope = rememberCoroutineScope()

    var game by remember { mutableIntStateOf(0) }
    var story by remember { mutableStateOf(startParts) }
    var writing by remember { mutableStateOf(startWriting) }
    /** The part being read again at the end, or null. */
    var replaying by remember { mutableStateOf<Int?>(null) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val turn = story.size + 1
    val finished = story.size >= turns
    val options = remember(game, turn) { if (finished) emptyList() else StoryPictures.options(turn, turns, story.map { it.pick }, random) }
    // The server's three possible parts for these pictures, asked for as soon as they're shown.
    val drafts = remember(game, turn) { CompletableDeferred<List<String>?>() }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    LaunchedEffect(game) {
        if (story.isEmpty()) {
            EventLog.log("story.start", child.id, subject = STORY) {
                put("turns", turns)
                put("ai", app.library.stories)
            }
            delay(300)
            app.say("Let's make a story, ${child.name}! ${StoryPictures.question(1, turns)}")
        }
    }
    LaunchedEffect(drafts) {
        if (options.isEmpty()) return@LaunchedEffect
        val parts = withTimeoutOrNull(WAIT_MS) { app.storyParts(child.id, story, options) }
        drafts.complete(parts)
        // Have the server's voice ready for whichever is picked.
        parts?.let { app.voice.prepare(it, slow = true) }
    }

    fun pick(index: Int) {
        if (writing || finished) return
        val pick = options.getOrNull(index) ?: return
        val partTurn = turn
        writing = true
        app.stopSpeaking()
        app.say(pick.word)
        val waitFrom = System.currentTimeMillis()
        scope.launch {
            delay(MIN_WRITING_MS)
            val written = withTimeoutOrNull(WAIT_MS) { drafts.await() }?.getOrNull(index).orEmpty()
            val text = written.ifBlank { SimpleStory.part(partTurn, turns, pick, story, child.name, random) }
            val part = StoryPart(pick, text, ai = written.isNotBlank())
            story = story + part
            writing = false
            EventLog.log("story.pick", child.id, subject = pick.word, value = partTurn.toDouble()) {
                put("waited", System.currentTimeMillis() - waitFrom)
                put("ai", part.ai)
            }
            if (story.size >= turns) {
                EventLog.log("story.finish", child.id, subject = STORY, value = story.size.toDouble()) {
                    put("words", JsonArray(story.map { JsonPrimitive(it.pick.word) }))
                    put("text", story.joinToString("\n") { it.text })
                    put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                }
                // Into the child's library; the server draws its picture.
                val finishedStory = story
                scope.launch { app.saveStory(child.id, finishedStory) }
                app.say(text, slow = true, onDone = {
                    app.sounds.play(Sounds.Effect.Finish, child)
                    app.say(if (hasTreats) "The end! What a brilliant story, ${child.name}! Pick a treat!" else "The end! What a brilliant story, ${child.name}!")
                })
                onFinished()
            } else {
                app.say(text, slow = true, onDone = { app.say(StoryPictures.question(partTurn + 1, turns)) })
            }
        }
    }

    fun hearAgain() {
        scope.launch {
            app.stopSpeaking()
            story.forEachIndexed { i, part ->
                replaying = i
                app.sayAndWait(part.text, slow = true)
                delay(400)
            }
            replaying = null
            app.say("The end!")
        }
    }

    fun newStory() {
        app.stopSpeaking()
        story = emptyList()
        replaying = null
        writing = false
        startedAt = System.currentTimeMillis()
        game++
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PlayHeader(child, "📖", "Story maker", "") }
                Progress(story.size, turns)
            }
            val shown = replaying?.let { story.getOrNull(it) } ?: story.lastOrNull()
            Page(app, Modifier.weight(1f).fillMaxWidth()) {
                when {
                    writing -> Writing()
                    shown == null -> Intro(child)
                    else -> PartText(app, shown)
                }
            }
            if (finished && !writing) {
                TheEnd(app, story, hasTreats, replaying != null, onHearAgain = ::hearAgain, onNewStory = ::newStory, onPickTreat = onPickTreat, onBack = onBack)
            } else {
                Choices(app, child, StoryPictures.question(turn, turns), options, enabled = !writing, key = game * 10 + turn, onPick = ::pick)
            }
        }
    }
}

/** The story's page: paper, so it looks like a book. */
@Composable
private fun Page(app: AppState, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Paper, RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun Intro(child: Child) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EmojiPicture("📖", 84.dp)
        Text("Let's make a story, ${child.name}!", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text("Pick a picture, and I'll write what happens.", color = Ink.copy(alpha = 0.7f), fontSize = 24.sp, textAlign = TextAlign.Center)
    }
}

/** One part of the story, with the picture that was picked for it. */
@Composable
private fun PartText(app: AppState, part: StoryPart) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        QuizPicture(app, part.pick.picture, 120.dp)
        val size = when {
            part.text.length > 320 -> 22.sp
            part.text.length > 220 -> 25.sp
            else -> 29.sp
        }
        Text(part.text, color = Ink, fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** A pencil writing lines on the page, while the story's next part is being written. */
@Composable
private fun Writing() {
    val t by rememberInfiniteTransition(label = "writing").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart), label = "writing",
    )
    val lines = 4
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Box(Modifier.width(560.dp).height(150.dp)) {
            val lineGap = 34.dp
            // Each line is written in turn; the pencil follows the end of the one being written.
            val progress = t * lines
            val current = progress.toInt().coerceAtMost(lines - 1)
            val along = (progress - current).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0 until lines) {
                    val length = when {
                        k < current -> 1f
                        k == current -> along
                        else -> 0f
                    } * (if (k == lines - 1) 0.6f else 1f)
                    if (length <= 0f) continue
                    val y = 24.dp.toPx() + k * lineGap.toPx()
                    // A wavy line, like joined-up writing.
                    var x = 0f
                    val end = size.width * length
                    var last = Offset(0f, y)
                    while (x < end) {
                        x = (x + 6.dp.toPx()).coerceAtMost(end)
                        val next = Offset(x, y + kotlin.math.sin(x / 9.dp.toPx()) * 5.dp.toPx())
                        drawLine(Ink.copy(alpha = 0.55f), last, next, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        last = next
                    }
                }
            }
            val shortLast = if (current == lines - 1) 0.6f else 1f
            EmojiPicture(
                "✏️",
                56.dp,
                Modifier
                    .offset(x = (560 * along * shortLast).dp - 8.dp, y = (current * 34 - 30).dp)
                    .rotate(kotlin.math.sin(t * 60f) * 8f),
            )
        }
        Text("Writing your story…", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** The question and the three pictures to pick from. */
@Composable
private fun Choices(app: AppState, child: Child, question: String, options: List<StoryPick>, enabled: Boolean, key: Int, onPick: (Int) -> Unit) {
    val first = remember(key) { FocusRequester() }
    LaunchedEffect(key, enabled) { if (enabled) first.requestFocusWhenReady() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            options.forEachIndexed { i, option ->
                PictureCard(
                    app,
                    option,
                    enabled,
                    (if (i == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged {
                        // No sound effects here: just the word, so the story stays calm.
                        if (it.isFocused && enabled) app.speak(option.word)
                    },
                ) { onPick(i) }
            }
        }
    }
}

@Composable
private fun PictureCard(app: AppState, option: StoryPick, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(210.dp).height(150.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) Reef else Reef.copy(alpha = 0.4f),
            contentColor = Color.White,
            focusedContainerColor = if (enabled) Tangerine else Reef.copy(alpha = 0.4f),
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (enabled) 1.1f else 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = shape)),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            QuizPicture(app, option.picture, 88.dp)
            Text(option.word, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The end: the pictures the child picked, and buttons to hear it again, make another, or pick a treat. */
@Composable
private fun TheEnd(
    app: AppState,
    story: List<StoryPart>,
    hasTreats: Boolean,
    replaying: Boolean,
    onHearAgain: () -> Unit,
    onNewStory: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The End", color = Tangerine, fontSize = 34.sp, fontWeight = FontWeight.Black)
            story.forEach { QuizPicture(app, it.pick.picture, 44.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (hasTreats) BigButton("⭐ Pick a treat", Modifier.focusRequester(first), onPickTreat)
            BigButton(if (replaying) "🔊 Reading…" else "🔊 Hear it again", if (hasTreats) Modifier else Modifier.focusRequester(first)) { if (!replaying) onHearAgain() }
            BigButton("📖 New story", Modifier, onNewStory)
            if (!hasTreats) BigButton("Back", Modifier, onBack)
        }
    }
}

/**
 * Says [text] and returns once it's been said, or after about as long as it takes to say
 * (some TVs never say when they've finished, or can't speak at all).
 */
internal suspend fun AppState.sayAndWait(text: String, slow: Boolean = false) {
    // Stories allow longer: the storyteller voice may still be recording a part the first time.
    val estimate = if (slow) 15_000L + text.split(' ').size * 900L else 3_000L + text.split(' ').size * 700L
    withTimeoutOrNull(estimate) {
        suspendCancellableCoroutine { c -> say(text, onDone = { if (c.isActive) c.resume(Unit) }, slow = slow) }
    }
}
