package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.njdyson.wonderbox.games.GameEntry
import com.njdyson.wonderbox.games.Question
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

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
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
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
                startedAt = System.currentTimeMillis()
                round = entry.newRound(child)
                question = round.next()
                finished = false
            }, onBack = onBack, app = app)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        QuizPicture(app, entry.icon, 36.dp)
                    }
                    Text(entry.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.weight(1f))
                    Progress(right, goal)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // A new question each time, so each one's state (greyed-out answers) starts fresh.
                    androidx.compose.runtime.key(question) {
                        Asking(app, question, onAnswer = { choice, ok ->
                            if (!ok) wrongAnswers++
                            EventLog.log("quiz.answer", child.id, subject = subject, value = if (ok) 1.0 else 0.0) {
                                put("question", question.prompt.ifBlank { question.spoken })
                                put("answer", choice)
                                put("expected", if (question.answerIndex >= 0) question.describe(question.answerIndex) else question.answer)
                                put("level", level)
                                put("kind", question.kind)
                                if (entry.quiz != null) put("title", entry.title)
                            }
                        }, onRight = {
                            right++
                            if (right >= goal) {
                                EventLog.log("quiz.finish", child.id, subject = subject, value = goal.toDouble()) {
                                    put("total", goal + wrongAnswers)
                                    put("wrong", wrongAnswers)
                                    put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                                    put("level", level)
                                    if (entry.quiz != null) put("title", entry.title)
                                }
                                finished = true
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

/** One star per right answer still to get, filled in as they're won. */
@Composable
private fun Progress(right: Int, goal: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (goal <= 15) {
            repeat(goal) { i -> Text("★", fontSize = 26.sp, color = if (i < right) Tangerine else Color.White.copy(alpha = 0.18f)) }
        } else {
            Text("★ $right / $goal", fontSize = 26.sp, color = Tangerine, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Asking(app: AppState, q: Question, onAnswer: (choice: String, right: Boolean) -> Unit, onRight: () -> Unit) {
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
        scope.launch {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
        }
    }

    fun yes() {
        correct = true
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
        if (isImagePath(q.picture)) {
            QuizPicture(app, q.picture, if (pictureAnswers) 150.dp else 220.dp)
        } else if (q.picture.isNotEmpty()) {
            Text(q.picture, fontSize = if (q.picture.length > 8) 54.sp else 84.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 820.dp))
        }
        if (shown.isNotEmpty()) {
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
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    picture = if (pictureAnswers) q.choicePictures.getOrElse(i) { "" } else null,
                    app = app,
                    onFocus = {
                        // A quiz's answers are read out as they're picked, for children who can't read yet.
                        if (moved && q.kind == "quiz" && !correct) app.say(choice)
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
) {
    val focusModifier = modifier.onFocusChanged { if (it.isFocused) onFocus() }
    if (picture != null && app != null) {
        Surface(
            onClick = onClick,
            modifier = focusModifier.size(width = 230.dp, height = if (text.isBlank()) 200.dp else 250.dp).alpha(if (faded) 0.3f else 1f),
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
                if (picture.isNotBlank()) QuizPicture(app, picture, 150.dp)
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
        Text(
            text,
            fontSize = if (text.length > 12) 30.sp else if (wide) 44.sp else 60.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun Finished(child: Child, hasTreats: Boolean, onPickTreat: () -> Unit, onPlayAgain: () -> Unit, onBack: () -> Unit, app: AppState) {
    val first = remember { FocusRequester() }
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        app.say(if (hasTreats) "You did it, ${child.name}! Pick a treat!" else "You did it, ${child.name}!")
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Text("🎉", fontSize = 110.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text("You did it, ${child.name}!", color = Tangerine, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text(
            if (hasTreats) "You've won a treat ⭐" else "Brilliant work!",
            color = Color.White,
            fontSize = 28.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (hasTreats) BigButton("⭐ Pick a treat", Modifier.focusRequester(first), onPickTreat)
            BigButton("Play again", if (hasTreats) Modifier else Modifier.focusRequester(first), onPlayAgain)
            if (!hasTreats) BigButton("Back", Modifier, onBack)
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
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
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
    }
}
