package com.njdyson.wonderbox.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Photo
import com.njdyson.wonderbox.games.PHOTO_QUIZ
import com.njdyson.wonderbox.games.PhotoQuiz
import com.njdyson.wonderbox.games.PhotoQuizPhoto
import com.njdyson.wonderbox.games.Question
import kotlinx.coroutines.delay
import kotlinx.serialization.json.put
import kotlin.random.Random

/** The photo quiz's icon, drawn from its bundled Fluent picture. */
const val MAGNIFIER = "🔍"

/** Where a round of the photo quiz is up to. */
private sealed interface Stage {
    data object Pick : Stage
    /** Looking at [photo] before it's hidden. */
    data class Look(val photo: Photo, val questions: List<Question>) : Stage
    /** Answering [questions] about [photo], from memory. */
    data class Ask(val photo: Photo, val questions: List<Question>) : Stage
    /** All answered: the photo again, and how many they remembered first time. */
    data class Done(val photo: Photo, val remembered: Int, val total: Int) : Stage
}

/**
 * The photo quiz: the child picks one of the family photos (or the TV picks, as the parent
 * chose), looks at it for a few seconds, then it's hidden and they answer questions about it
 * from memory, read aloud, with the answers to pick from. Gemini wrote the questions on the
 * server ahead of time. A wrong answer greys out and they try again, as in every quiz; at the
 * end the photo comes back so they can see what they remembered. Finishing earns a treat, if
 * the parent chose that.
 */
@Composable
fun PhotoQuizScreen(
    app: AppState,
    child: Child,
    hasTreats: Boolean,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    /** The photos and questions to use (for screenshots); normally fetched from the server. */
    startQuizzes: List<PhotoQuizPhoto>? = null,
    /** Start straight on this photo (for screenshots). */
    startPhoto: Photo? = null,
    random: Random = Random,
) {
    val setup = child.games.photoQuiz
    var quizzes by remember { mutableStateOf(startQuizzes) }
    var failed by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<Stage?>(null) }
    var lastPhotoId by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    // The child's photos that have questions, newest first.
    val photos = quizzes?.let { list ->
        val ready = list.filter { q -> PhotoQuiz.pick(q.questions, setup.age, 1).isNotEmpty() }.map { it.photo }.toSet()
        app.stillsFor(child.id).filter { it.id in ready }
    }

    fun start(photo: Photo) {
        val questions = quizzes?.firstOrNull { it.photo == photo.id }?.questions.orEmpty()
        val asked = PhotoQuiz.pick(questions, setup.age, setup.questions, random).map { PhotoQuiz.toQuestion(it, setup.age, random) }
        if (asked.isEmpty()) return
        lastPhotoId = photo.id
        round++
        EventLog.log("photoquiz.start", child.id, subject = photo.id) {
            put("look", setup.look)
            put("questions", asked.size)
        }
        stage = Stage.Look(photo, asked)
    }

    /** The TV picks: any photo but the last one, when there's a choice. */
    fun surprise() {
        val list = photos.orEmpty()
        val choice = list.filter { it.id != lastPhotoId }.ifEmpty { list }.randomOrNull(random) ?: return
        start(choice)
    }

    LaunchedEffect(Unit) {
        if (quizzes == null) {
            app.say("Let's get your photos ready!")
            val fetched = app.photoQuiz(child.id)
            if (fetched == null) failed = true else quizzes = fetched
        }
        val first = startPhoto
        if (first != null) start(first) else stage = Stage.Pick
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        val list = photos
        when (val now = stage) {
            null -> Waiting(child, if (failed) "The photo quiz can't reach the server right now. Try again in a little while!" else "Getting your photos ready…", onBack.takeIf { failed })
            Stage.Pick -> when {
                list == null -> Waiting(child, "The photo quiz can't reach the server right now. Try again in a little while!", onBack)
                list.isEmpty() -> Waiting(child, "No photos to play with yet. Ask a grown-up to add some photos, or to look at the Photo quiz in the phone app.", onBack)
                setup.surprise -> LaunchedEffect(round) { surprise() }
                else -> PickPhoto(app, child, list, lastPhotoId, onPick = ::start)
            }
            is Stage.Look -> key(round) {
                Look(app, child, now.photo, setup.look, onHidden = { stage = Stage.Ask(now.photo, now.questions) })
            }
            is Stage.Ask -> key(round) {
                Answering(app, child, now.photo, now.questions, onDone = { remembered ->
                    app.sounds.play(Sounds.Effect.Finish, child)
                    onFinished()
                    stage = Stage.Done(now.photo, remembered, now.questions.size)
                })
            }
            is Stage.Done -> LookAgain(
                app, child, now.photo, now.remembered, now.total, hasTreats, onPickTreat, onBack,
                onAnother = {
                    round++
                    stage = Stage.Pick
                },
            )
        }
    }
}

@Composable
private fun Header(child: Child, right: String, rightContent: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).background(parseColor(child.color), CircleShape), contentAlignment = Alignment.Center) {
            EmojiPicture(MAGNIFIER, 38.dp)
        }
        Text("Photo quiz", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Box(Modifier.weight(1f))
        rightContent()
        if (right.isNotEmpty()) Text(right, color = Tangerine, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/** Loading, or a message when there's nothing to play (with a way [onBack]). */
@Composable
private fun Waiting(child: Child, message: String, onBack: (() -> Unit)?) {
    val back = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(child, "")
        Spacer(Modifier.weight(1f))
        EmojiPicture(MAGNIFIER, 140.dp)
        Text(message, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp).padding(top = 24.dp))
        if (onBack != null) {
            BigButton("Back", Modifier.padding(top = 28.dp).focusRequester(back), onBack)
            LaunchedEffect(Unit) { back.requestFocusWhenReady() }
        }
        Spacer(Modifier.weight(1.3f))
    }
}

/** "Pick a photo": the photos row, starting on the one after the last round's photo. */
@Composable
private fun PickPhoto(app: AppState, child: Child, photos: List<Photo>, lastPhotoId: String?, onPick: (Photo) -> Unit) {
    val focus = remember { FocusRequester() }
    var focusedId by remember {
        val last = photos.indexOfFirst { it.id == lastPhotoId }
        mutableStateOf(photos.getOrNull((last + 1).mod(photos.size))?.id)
    }
    LaunchedEffect(Unit) {
        app.say("Pick a photo. Look carefully, then I'll ask you about it!")
        focus.requestFocusWhenReady()
    }
    Column(Modifier.fillMaxSize().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Header(child, "Pick a photo")
        Spacer(Modifier.weight(0.4f))
        Text(
            "Look carefully at a photo, then see what you remember!",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        PhotosRow(
            app,
            photos,
            onOpen = { onPick(photos[it]) },
            onFocus = { focusedId = it },
            focusedId = focusedId,
            focusModifier = Modifier.focusRequester(focus),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** The photo, fitted into [modifier], with this TV's key. [onLoaded] once it's showing. */
@Composable
private fun BigPhoto(app: AppState, photo: Photo, modifier: Modifier, onLoaded: () -> Unit = {}) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(app.photoUrl(photo.image))
            .addHeader("X-Device-Key", app.deviceKey.orEmpty())
            .size(1280)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { onLoaded() },
        onError = { onLoaded() },
        modifier = modifier,
    )
}

/**
 * The photo for [seconds], with a bar running down, then it's hidden. OK hides it sooner
 * ("I'm ready!"). The time starts once the photo has loaded.
 */
@Composable
private fun Look(app: AppState, child: Child, photo: Photo, seconds: Int, onHidden: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }
    val left = remember { Animatable(1f) }
    val cover = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocusWhenReady()
        // Shown at once if it doesn't load in time, so a slow network doesn't stop the game.
        delay(6_000)
        loaded = true
    }
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        app.say("Look carefully, ${child.name}! Try to remember everything you can see.")
        left.animateTo(0f, tween(seconds * 1000, easing = LinearEasing))
        hiding = true
    }
    LaunchedEffect(hiding) {
        if (!hiding) return@LaunchedEffect
        left.stop()
        app.say("Now, what can you remember?")
        cover.animateTo(1f, tween(700))
        delay(1_300)
        onHidden()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (loaded) hiding = true
                        true
                    }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val secondsLeft = kotlin.math.ceil(left.value * seconds).toInt()
        Header(child, if (hiding) "" else if (loaded) "👀 $secondsLeft" else "")
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
            BigPhoto(app, photo, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), onLoaded = { loaded = true })
            if (cover.value > 0f) {
                // The photo hides behind a card, which says it's gone.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = cover.value }.background(Reef, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmojiPicture(MAGNIFIER, 130.dp)
                        Text("What can you remember?", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        // The time left to look.
        Box(Modifier.width(900.dp).height(18.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(left.value).background(Tangerine, CircleShape))
        }
        Text(
            if (hiding) "Hidden!" else "Look carefully! Press OK when you're ready.",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The questions about the hidden photo, one after another. [onDone] with how many were
 * answered right first time.
 */
@Composable
private fun Answering(app: AppState, child: Child, photo: Photo, questions: List<Question>, onDone: (remembered: Int) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableIntStateOf(0) }
    var wrongHere by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    val startedAt = remember { System.currentTimeMillis() }
    val question = questions[index.coerceAtMost(questions.lastIndex)]

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).background(parseColor(child.color), CircleShape), contentAlignment = Alignment.Center) {
                EmojiPicture(MAGNIFIER, 38.dp)
            }
            Text("Photo quiz", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Box(Modifier.weight(1f))
            if (app.voice.silent) Text("🔇 No voice on this TV: see Grown-ups", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
            BigButton("🔊 Hear it again", Modifier) { app.say(question.spoken) }
            Progress(index, questions.size)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            key(index) {
                Asking(app, child, question, onAnswer = { choice, ok ->
                    if (ok && wrongHere == 0) remembered++
                    if (!ok) {
                        wrongHere++
                        wrong++
                    }
                    EventLog.log("quiz.answer", child.id, subject = PHOTO_QUIZ, value = if (ok) 1.0 else 0.0) {
                        put("question", question.prompt)
                        put("answer", choice)
                        put("expected", question.answer)
                        put("kind", "photo")
                        put("photo", photo.id)
                    }
                }, onRight = {
                    wrongHere = 0
                    if (index + 1 >= questions.size) {
                        EventLog.log("quiz.finish", child.id, subject = PHOTO_QUIZ, value = questions.size.toDouble()) {
                            put("total", questions.size + wrong)
                            put("wrong", wrong)
                            put("remembered", remembered)
                            put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                            put("photo", photo.id)
                        }
                        onDone(remembered)
                    } else {
                        index++
                    }
                })
            }
        }
    }
}

/** The photo again, so they can see what they remembered, and what next. */
@Composable
private fun LookAgain(
    app: AppState,
    child: Child,
    photo: Photo,
    remembered: Int,
    total: Int,
    hasTreats: Boolean,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    onAnother: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val pop = remember { Animatable(0.3f) }
    val cheer = when {
        remembered == total -> "You remembered everything, ${child.name}!"
        remembered * 2 >= total -> "You remembered $remembered of $total, ${child.name}!"
        else -> "Well done, ${child.name}!"
    }
    LaunchedEffect(Unit) {
        app.say("$cheer Here's the photo again.${if (hasTreats) " Pick a treat!" else ""}")
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(Modifier.fillMaxSize().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header(child, "🎉 All done!")
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            BigPhoto(
                app, photo,
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)),
            )
            Column(
                Modifier.width(400.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎉", fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                Text(cheer, color = Tangerine, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(total) { i -> Text("★", fontSize = 34.sp, color = if (i < remembered) Tangerine else Color.White.copy(alpha = 0.25f)) }
                }
                Text(
                    if (hasTreats) "You've won a treat ⭐" else "Right first time: $remembered of $total",
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                if (hasTreats) BigButton("⭐ Pick a treat", Modifier.focusRequester(first), onPickTreat)
                BigButton("Another photo", if (hasTreats) Modifier else Modifier.focusRequester(first), onAnother)
                if (!hasTreats) BigButton("Back", Modifier, onBack)
            }
        }
    }
}
