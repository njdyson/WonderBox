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
import com.njdyson.wonderbox.games.Game
import com.njdyson.wonderbox.games.Question
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Leaf = Color(0xFF7BD389)

/**
 * A round of [game] at the child's level: answer [Child.games] roundSize questions right to
 * finish, which earns a treat. A wrong answer just greys out that button and the child tries
 * again; nothing is ever lost.
 */
@Composable
fun GameScreen(
    app: AppState,
    child: Child,
    game: Game,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    hasTreats: Boolean,
) {
    val level = if (game == Game.Maths) child.games.maths else child.games.spelling
    val goal = child.games.roundSize.coerceIn(1, 50)
    var right by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(game.question(level)) }
    var finished by remember { mutableStateOf(false) }
    val color = parseColor(child.color)
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Deep))),
    ) {
        if (finished) {
            Finished(child, hasTreats, onPickTreat, onPlayAgain = {
                right = 0
                question = game.question(level)
                finished = false
            }, onBack = onBack, app = app)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        Text(game.icon, fontSize = 28.sp)
                    }
                    Text(game.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.weight(1f))
                    Progress(right, goal)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // A new question each time, so each one's state (greyed-out answers) starts fresh.
                    androidx.compose.runtime.key(question) {
                        Asking(app, question, onRight = {
                            right++
                            if (right >= goal) {
                                // TODO log quiz.finish (score, level) once the activity log (PR #2) is in.
                                finished = true
                                onFinished()
                            } else {
                                question = game.question(level)
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
            repeat(goal) { i -> Text("★", fontSize = 26.sp, color = if (i < right) Sunshine else Color.White.copy(alpha = 0.18f)) }
        } else {
            Text("★ $right / $goal", fontSize = 26.sp, color = Sunshine, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Asking(app: AppState, q: Question, onRight: () -> Unit) {
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
    } else if (correct) q.prompt.replace("_", q.answer) else q.prompt

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (q.picture.isNotEmpty()) {
            Text(q.picture, fontSize = if (q.picture.length > 8) 54.sp else 84.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 820.dp))
        }
        if (shown.isNotEmpty()) {
            Text(
                spaced(shown),
                color = if (correct) Leaf else Color.White,
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = if (shown.contains('_')) 6.sp else 0.sp,
                modifier = Modifier.graphicsLayer { translationX = shake.value },
            )
        } else {
            Box(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.graphicsLayer { if (shown.isEmpty()) translationX = shake.value }) {
            q.choices.forEachIndexed { i, choice ->
                val gone = i in wrong || (q.build && i in used)
                val isRightPick = correct && !q.build && choice == q.answer
                AnswerButton(
                    choice,
                    wide = choice.length > 3,
                    faded = gone || (correct && !isRightPick),
                    highlight = isRightPick,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = {
                        if (gone || correct) return@AnswerButton
                        if (q.build) {
                            if (choice == q.answer[used.size].toString()) {
                                used += i
                                app.say(choice)
                                if (used.size == q.answer.length) yes()
                            } else oops(i)
                        } else if (choice == q.answer) yes() else oops(i)
                        // TODO log quiz.answer (value 1 right, 0 wrong) once the activity log (PR #2) is in.
                    },
                )
            }
        }
    }
}

/** "c_t" as "c _ t", so the gap is easy to see. */
private fun spaced(text: String) = if ('_' in text) text.toList().joinToString(" ") else text

/**
 * Never disabled, even once it's been tried: a disabled button can't hold focus, and the
 * remote would be left with nothing focused. [faded] ones just do nothing.
 */
@Composable
private fun AnswerButton(text: String, wide: Boolean, faded: Boolean, highlight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(120.dp).widthIn(min = if (wide) 200.dp else 120.dp).alpha(if (faded) 0.3f else 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) Leaf else Plum,
            contentColor = if (highlight) Deep else Color.White,
            focusedContainerColor = if (highlight) Leaf else Sunshine,
            focusedContentColor = Deep,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(5.dp, Color.White), shape = RoundedCornerShape(28.dp))),
    ) {
        Text(
            text,
            fontSize = if (wide) 44.sp else 60.sp,
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
        Text("You did it, ${child.name}!", color = Sunshine, fontSize = 48.sp, fontWeight = FontWeight.Black)
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
            containerColor = Plum,
            contentColor = Color.White,
            focusedContainerColor = Sunshine,
            focusedContentColor = Deep,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
    }
}
