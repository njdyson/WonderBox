package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.games.AutoLevel
import com.njdyson.wonderbox.games.ClockQuestions
import com.njdyson.wonderbox.games.Game
import com.njdyson.wonderbox.games.GameEntry
import com.njdyson.wonderbox.games.Question
import com.njdyson.wonderbox.games.levelFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import kotlin.math.cos
import kotlin.math.sin

val Leaf = Color(0xFF7BD389)

/**
 * A round of [entry]: maths or spelling as the child's setup says (answer [Child.games]
 * roundSize questions right to finish), or every question of a parent's quiz. Finishing earns
 * a treat. A wrong answer just greys out that button and the child tries again; nothing is
 * ever lost.
 */
@Composable
fun GameScreen(
    app: AppState,
    child: Child,
    entry: GameEntry,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    hasTreats: Boolean,
) {
    var round by remember { mutableStateOf(entry.newRound(child)) }
    val goal = round.goal
    val level = round.level
    var right by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(round.next()) }
    var finished by remember { mutableStateOf(false) }
    var wrongAnswers by remember { mutableIntStateOf(0) }
    // Questions got right without a wrong try, and whether this one has had a wrong try yet.
    var firstTry by remember { mutableIntStateOf(0) }
    var slipped by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    // The level an auto-level game moved up to after this round, to cheer about.
    var levelUp by remember { mutableStateOf<Int?>(null) }
    val subject = entry.key
    val color = parseColor(child.color)
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink))),
    ) {
        if (finished) {
            Finished(child, hasTreats, onPickTreat, onPlayAgain = {
                right = 0
                wrongAnswers = 0
                firstTry = 0
                slipped = false
                levelUp = null
                startedAt = System.currentTimeMillis()
                round = entry.newRound(child)
                question = round.next()
                finished = false
            }, onBack = onBack, app = app, levelUp = levelUp)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        QuizPicture(app, entry.icon, 36.dp)
                    }
                    // The title gives way (cut short with "…") before the stars, which must always show.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderTitle(entry.title, Modifier.weight(1f, fill = false))
                        // The mystery quiz: which game this question is from, revealed as it's asked.
                        mysteryGame(question)?.let { game -> MysteryReveal(app, game, question) }
                    }
                    // Nothing can speak on this TV: say so, rather than leave a silent game.
                    if (app.voice.silent) Text("🔇 No voice on this TV: see Grown-ups", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
                    // Up from the answers: the question again, for spelling a word from its sound.
                    if (question.spoken.isNotBlank()) HeaderButton("🔊 Hear it again") { app.say(question.spoken) }
                    Progress(right, goal)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // A new question each time, so each one's state (greyed-out answers) starts fresh.
                    androidx.compose.runtime.key(question) {
                        Asking(app, child, question, onAnswer = { choice, ok ->
                            if (!ok) {
                                wrongAnswers++
                                slipped = true
                            }
                            EventLog.log("quiz.answer", child.id, subject = subject, value = if (ok) 1.0 else 0.0) {
                                put("question", question.prompt.ifBlank { question.spoken })
                                put("answer", choice)
                                put("expected", if (question.answerIndex >= 0) question.describe(question.answerIndex) else question.answer)
                                put("level", level)
                                put("kind", question.kind)
                                if (entry.quiz != null) put("title", entry.title)
                                // A mystery question: which game it's from and the child's level there.
                                mysteryGame(question)?.let { game ->
                                    put("game", game.key)
                                    put("gameLevel", game.levelFor(child))
                                }
                            }
                        }, onRight = {
                            right++
                            if (!slipped) firstTry++
                            slipped = false
                            if (right >= goal) {
                                EventLog.log("quiz.finish", child.id, subject = subject, value = goal.toDouble()) {
                                    put("total", goal + wrongAnswers)
                                    put("wrong", wrongAnswers)
                                    put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                                    put("level", level)
                                    if (entry.quiz != null) put("title", entry.title)
                                }
                                // A parent's quiz has no levels; the rest may be on auto.
                                if (entry.quiz == null) {
                                    val next = app.autoLevel(child.id, subject, AutoLevel.quiz(goal, wrongAnswers, firstTry))
                                    levelUp = next?.takeIf { it > level }
                                }
                                finished = true
                                app.sounds.play(Sounds.Effect.Finish, child)
                                onFinished()
                            } else {
                                question = round.next()
                            }
                        })
                    }
                }
            }
        }
    }
}

/** The game a mystery quiz's question is from, or null for any other question. */
private fun mysteryGame(q: Question): Game? = q.game.takeIf { it.isNotEmpty() }?.let { key -> Game.entries.firstOrNull { it.key == key } }

/** A pill with [game]'s picture and name that pops in for each new question. */
@Composable
private fun MysteryReveal(app: AppState, game: Game, question: Question) {
    val pop = remember(question) { Animatable(0.4f) }
    LaunchedEffect(question) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    Row(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuizPicture(app, game.icon, 30.dp)
        Text(game.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** A game's name at the top of its screen, on one line, cut short with "…" if the header is crowded. */
@Composable
internal fun HeaderTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false, modifier = modifier)
}

/**
 * One star per right answer still to get, filled in as they're won; a count once there are too
 * many to fit. Header rows measure it before the title, so it's never pushed off the screen.
 */
@Composable
internal fun Progress(right: Int, goal: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (goal > 8) 0.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (goal <= 12) {
            val size = if (goal > 8) 21.sp else 26.sp
            repeat(goal) { i -> Text("★", fontSize = size, softWrap = false, color = if (i < right) Tangerine else Color.White.copy(alpha = 0.18f)) }
        } else {
            Text("★ $right / $goal", fontSize = 26.sp, color = Tangerine, fontWeight = FontWeight.Bold)
        }
    }
}

/** One question: its picture and words, and the answers to pick from. The photo quiz asks with it too. */
@Composable
internal fun Asking(app: AppState, child: Child, q: Question, onAnswer: (choice: String, right: Boolean) -> Unit, onRight: () -> Unit) {
    val scope = rememberCoroutineScope()
    val wrong = remember { mutableStateListOf<Int>() }
    // For spelling the whole word: the letters (by button) picked so far.
    val used = remember { mutableStateListOf<Int>() }
    var correct by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250)
        app.say(q.spoken)
        first.requestFocusWhenReady()
    }

    fun oops(index: Int) {
        // A wrong letter when spelling the whole word may be right later, so it isn't greyed out.
        if (!q.build) wrong += index
        app.say("Try again!")
        app.sounds.play(Sounds.Effect.Wrong, child)
        scope.launch {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
        }
    }

    fun yes() {
        correct = true
        app.sounds.play(Sounds.Effect.Right, child)
        app.say(listOf("Yes!", "Well done!", "Brilliant!", "Super!", "That's right!").random())
        scope.launch {
            delay(1_100)
            onRight()
        }
    }

    val shown = if (q.build) {
        q.answer.mapIndexed { i, c -> if (i < used.size) c else '_' }.joinToString("")
    } else if (correct && q.answerIndex < 0) q.prompt.replace("_", q.answer) else q.prompt
    val pictureAnswers = q.choicePictures.any { it.isNotBlank() }
    // The first focus is the screen putting it there, not the child moving it.
    var moved by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (pictureAnswers) 14.dp else 20.dp)) {
        val clock = ClockQuestions.parse(q.picture)
        if (clock != null) {
            ClockFace(clock.first, clock.second, 250.dp)
        } else if (isImagePath(q.picture)) {
            QuizPicture(app, q.picture, if (pictureAnswers) 150.dp else 220.dp)
        } else if (q.picture.isNotEmpty()) {
            PictureLine(q.picture, fontSize = if (q.picture.length > 8) 54.sp else 84.sp, modifier = Modifier.widthIn(max = 820.dp))
        }
        // A phonics word, with its sound buttons: shown once it's right when the question hides it.
        val phonics = if (shown.isEmpty() && correct) q.topic else shown
        if (q.sounds.isNotEmpty() && q.sounds.joinToString("") == q.topic && phonics.length == q.topic.length) {
            SoundButtons(phonics, q.sounds, if (correct) Leaf else Color.White, Modifier.graphicsLayer { translationX = shake.value })
        } else if (shown.isNotEmpty()) {
            Text(
                spaced(shown),
                color = if (correct) Leaf else Color.White,
                fontSize = when {
                    shown.length > 40 -> 34.sp
                    shown.length > 14 -> 46.sp
                    else -> 68.sp
                },
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                letterSpacing = if (shown.contains('_')) 6.sp else 0.sp,
                modifier = Modifier.widthIn(max = 1100.dp).graphicsLayer { translationX = shake.value },
            )
        } else {
            Box(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.graphicsLayer { if (shown.isEmpty()) translationX = shake.value }) {
            q.choices.forEachIndexed { i, choice ->
                val gone = i in wrong || (q.build && i in used)
                val isRightPick = correct && !q.build && q.isRight(i)
                AnswerButton(
                    choice,
                    wide = choice.length > 3,
                    faded = gone || (correct && !isRightPick),
                    highlight = isRightPick,
                    // An equal share of the width each, so the last answer isn't squeezed.
                    modifier = Modifier.weight(1f, fill = false).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    picture = if (pictureAnswers) q.choicePictures.getOrElse(i) { "" } else null,
                    app = app,
                    // Four picture buttons only fit across the screen a little smaller.
                    compact = q.choices.size > 3,
                    onFocus = {
                        // A quiz's answers are read out as they're picked, for children who can't read yet.
                        if (moved && (q.kind == "quiz" || q.kind == "flagname") && !correct) app.say(choice)
                        if (moved) app.sounds.play(Sounds.Effect.Move, child)
                        moved = true
                    },
                    onClick = {
                        if (gone || correct) return@AnswerButton
                        if (q.build) {
                            if (choice == q.answer[used.size].toString()) {
                                used += i
                                app.say(choice)
                                if (used.size == q.answer.length) {
                                    onAnswer(q.answer, true)
                                    yes()
                                }
                            } else {
                                onAnswer(q.answer.take(used.size) + choice, false)
                                oops(i)
                            }
                        } else {
                            onAnswer(q.describe(i), q.isRight(i))
                            if (q.isRight(i)) yes() else oops(i)
                        }
                    },
                )
            }
        }
    }
}

/** A clock face showing [hour]:[minute], with all twelve numbers and both hands. */
@Composable
fun ClockFace(hour: Int, minute: Int, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(Color.White, r)
            drawCircle(Tangerine, r, style = Stroke(width = r * 0.07f))
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90)
                val inner = if (i % 5 == 0) r * 0.84f else r * 0.89f
                val outer = r * 0.93f
                drawLine(
                    Ink.copy(alpha = if (i % 5 == 0) 0.9f else 0.4f),
                    center + Offset(cos(a).toFloat() * inner, sin(a).toFloat() * inner),
                    center + Offset(cos(a).toFloat() * outer, sin(a).toFloat() * outer),
                    strokeWidth = if (i % 5 == 0) r * 0.025f else r * 0.012f,
                )
            }
        }
        val radius = size / 2 * 0.66f
        for (n in 1..12) {
            val a = Math.toRadians(n * 30.0 - 90)
            Text(
                "$n",
                color = Ink,
                fontSize = (size.value * 0.1f).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(x = radius * cos(a).toFloat(), y = radius * sin(a).toFloat()),
            )
        }
        // The hands over the numbers, as on a real clock.
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            fun hand(degrees: Double, length: Float, width: Float, color: Color) {
                val a = Math.toRadians(degrees - 90)
                drawLine(color, center, center + Offset(cos(a).toFloat() * length, sin(a).toFloat() * length), strokeWidth = width, cap = StrokeCap.Round)
            }
            // The hour hand moves on between the hours, as on a real clock.
            hand((hour % 12 + minute / 60.0) * 30, r * 0.48f, r * 0.09f, Ink)
            hand(minute * 6.0, r * 0.76f, r * 0.05f, Reef)
            drawCircle(Ink, r * 0.07f)
        }
    }
}

/**
 * [text] (a word, perhaps with gaps) split into its [sounds], with a dot under each single
 * letter and a line under two letters making one sound, like the school's sound buttons.
 */
@Composable
fun SoundButtons(text: String, sounds: List<String>, color: Color, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = modifier) {
        var at = 0
        for (sound in sounds) {
            val part = text.substring(at, at + sound.length)
            at += sound.length
            Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(part, color = color, fontSize = 68.sp, fontWeight = FontWeight.Black, letterSpacing = if ('_' in part) 6.sp else 0.sp)
                if (sound.length == 1) {
                    Box(Modifier.padding(top = 10.dp).size(16.dp).background(Tangerine, CircleShape))
                } else {
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(7.dp).background(Tangerine, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

/** "c_t" as "c _ t", so the gap is easy to see. Sums ("3 + _ = 5") are spaced already. */
private fun spaced(text: String) = if ('_' in text && ' ' !in text) text.toList().joinToString(" ") else text

/**
 * Never disabled, even once it's been tried: a disabled button can't hold focus, and the
 * remote would be left with nothing focused. [faded] ones just do nothing.
 */
@Composable
private fun AnswerButton(
    text: String,
    wide: Boolean,
    faded: Boolean,
    highlight: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    /** Not null for a quiz with pictures: a big square button, the picture over its words. */
    picture: String? = null,
    app: AppState? = null,
    compact: Boolean = false,
) {
    val focusModifier = modifier.onFocusChanged { if (it.isFocused) onFocus() }
    if (picture != null && app != null) {
        Surface(
            onClick = onClick,
            modifier = focusModifier.size(width = if (compact) 190.dp else 230.dp, height = if (text.isBlank()) 200.dp else 250.dp).alpha(if (faded) 0.3f else 1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (highlight) Leaf else Reef,
                contentColor = if (highlight) Ink else Color.White,
                focusedContainerColor = if (highlight) Leaf else Tangerine,
                focusedContentColor = Ink,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(5.dp, Color.White), shape = RoundedCornerShape(28.dp))),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (picture.isNotBlank()) QuizPicture(app, picture, if (compact) 140.dp else 150.dp)
                if (text.isNotBlank()) {
                    Text(text, fontSize = if (text.length > 12) 22.sp else 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        modifier = focusModifier.height(120.dp).widthIn(min = if (wide) 200.dp else 120.dp, max = 420.dp).alpha(if (faded) 0.3f else 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) Leaf else Reef,
            contentColor = if (highlight) Ink else Color.White,
            focusedContainerColor = if (highlight) Leaf else Tangerine,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(5.dp, Color.White), shape = RoundedCornerShape(28.dp))),
    ) {
        // Shrinks to fit the button, so "half past 12" never wraps over itself or runs out of it.
        FitText(
            text,
            maxSize = if (text.length > 12) 30.sp else if (wide) 44.sp else 60.sp,
            minSize = 18.sp,
            maxLines = 2,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * "You did it!", then a treat to pick (when [hasTreats]), play again or back. Hangman and pairs end
 * with it too. [levelUp] is the level an auto-level game has just moved up to (moving down is quiet).
 */
@Composable
internal fun Finished(child: Child, hasTreats: Boolean, onPickTreat: () -> Unit, onPlayAgain: () -> Unit, onBack: () -> Unit, app: AppState, levelUp: Int? = null) {
    val first = remember { FocusRequester() }
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        val up = if (levelUp != null) " You're going up to level $levelUp!" else ""
        app.say(if (hasTreats) "You did it, ${child.name}!$up Pick a treat!" else "You did it, ${child.name}!$up")
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        EmojiPicture("🎉", 130.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text("You did it, ${child.name}!", color = Tangerine, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text(
            if (hasTreats) "You've won a treat ⭐" else "Brilliant work!",
            color = Color.White,
            fontSize = 28.sp,
        )
        if (levelUp != null) Text("⬆️ Up to level $levelUp next time!", color = Leaf, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (hasTreats) BigButton("⭐ Pick a treat", Modifier.focusRequester(first), onPickTreat)
            BigButton("Play again", if (hasTreats) Modifier else Modifier.focusRequester(first), onPlayAgain)
            if (!hasTreats) BigButton("Back", Modifier, onBack)
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) = BigButton(text, modifier, small = false, onClick)

/** A [BigButton] a little smaller, for a crowded header row (the games' "Hear it again"). */
@Composable
fun HeaderButton(text: String, onClick: () -> Unit) = BigButton(text, Modifier, small = true, onClick)

@Composable
private fun BigButton(text: String, modifier: Modifier, small: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Reef,
            contentColor = Color.White,
            focusedContainerColor = Tangerine,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text(
            text,
            fontSize = if (small) 22.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = if (small) 18.dp else 32.dp, vertical = if (small) 10.dp else 14.dp),
        )
    }
}

/**
 * [text] as big as it can be, up to [maxSize], and still fit where it's put: on one line if it
 * can stay fairly big that way, else on up to [maxLines] lines broken only between words.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    minSize: TextUnit,
    maxLines: Int,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val limit = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        // Measured just as it's drawn: the theme's text style, at each size tried.
        val base = LocalTextStyle.current.merge(TextStyle(fontWeight = fontWeight, textAlign = TextAlign.Center))
        fun style(sp: Float) = base.merge(TextStyle(fontSize = sp.sp, lineHeight = (sp * 1.1f).sp))
        val (size, lines) = remember(text, maxSize, minSize, maxLines, limit, base) {
            fun fits(sp: Float, lines: Int): Boolean {
                val layout = measurer.measure(
                    text,
                    style(sp),
                    maxLines = lines,
                    constraints = limit,
                )
                if (layout.hasVisualOverflow) return false
                // A word broken across lines doesn't fit either.
                return (0 until layout.lineCount - 1).all { i -> text.getOrNull(layout.getLineEnd(i) - 1)?.isWhitespace() == true }
            }
            val sizes = generateSequence(maxSize.value) { it - 2f }.takeWhile { it >= minSize.value }.toList()
            sizes.firstOrNull { it >= maxSize.value * 0.7f && fits(it, 1) }?.let { it to 1 }
                ?: sizes.firstOrNull { fits(it, maxLines) }?.let { it to maxLines }
                ?: (minSize.value to maxLines)
        }
        Text(text, style = style(size), maxLines = lines, softWrap = lines > 1, overflow = TextOverflow.Ellipsis)
    }
}
