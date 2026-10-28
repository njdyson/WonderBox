package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
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
import com.njdyson.wonderbox.games.HANGMAN
import com.njdyson.wonderbox.games.Hangman
import com.njdyson.wonderbox.games.RecentTopics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hangman without the gallows: the child holds a bunch of balloons, and each wrong letter pops
 * one. The word is said aloud and its picture shown (unless the parent turned that off), so
 * it's spelling from the sound. Letters are picked with the arrows and OK from a small set.
 * Run out of balloons and the word is shown, then it's on to another; finding the child's
 * [com.njdyson.wonderbox.games.HangmanSetup.words] words finishes the game.
 */
@Composable
fun HangmanScreen(
    app: AppState,
    child: Child,
    stars: Int,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    random: Random = Random,
    /** Letters to try straight away (for screenshots). */
    startGuesses: String = "",
) {
    val setup = child.games.hangman
    val goal = setup.words.coerceIn(1, 10)
    val words = remember { Hangman.words(setup, child.games.spelling().myWords) }
    val recent = remember { RecentTopics.forGame("${child.id}/$HANGMAN") }
    fun nextWord(): Hangman {
        val (word, picture) = recent.fresh({ words.random(random) }) { it.first }
        return Hangman.new(word, picture, setup, random)
    }

    var game by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(nextWord()) }
    var found by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        if (finished) {
            Finished(child, stars, onPlayAgain = {
                found = 0
                missed = 0
                startedAt = System.currentTimeMillis()
                current = nextWord()
                game++
                finished = false
            }, onBack = onBack, app = app)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { PlayHeader(child, "🎈", "Hangman", "") }
                    Progress(found, goal)
                }
                key(game, current) {
                    Word(app, child, current, setup.say, startGuesses, onOver = { solved ->
                        EventLog.log("hangman.word", child.id, subject = current.word, value = if (solved) 1.0 else 0.0) {
                            put("wrong", current.wrong)
                            put("balloons", current.balloons)
                            put("letters", current.letters.size)
                            put("said", setup.say)
                        }
                        if (solved) found++ else missed++
                        if (found >= goal) {
                            EventLog.log("hangman.finish", child.id, subject = HANGMAN, value = goal.toDouble()) {
                                put("missed", missed)
                                put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                            }
                            app.sounds.play(Sounds.Effect.Finish, child)
                            finished = true
                            onFinished()
                        } else {
                            current = nextWord()
                        }
                    })
                }
            }
        }
    }
}

/** One word: the balloons, the picture and the gaps, and the letters to pick from. */
@Composable
private fun Word(app: AppState, child: Child, game: Hangman, say: Boolean, startGuesses: String, onOver: (solved: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    // Hangman changes in place; reading this redraws.
    var changes by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startGuesses.forEach { game.guess(it) }
        changes++
        first.requestFocusWhenReady()
        delay(250)
        app.say(if (say) "Find the letters for ${game.word}!" else "Guess the word! It has ${game.word.length} letters.")
    }

    fun pick(letter: Char) {
        when (game.guess(letter)) {
            Hangman.Guess.Again -> app.sounds.chime(Chimes.Sound.Bump, child)
            Hangman.Guess.Right -> app.sounds.play(if (game.solved) Sounds.Effect.Right else Sounds.Effect.Piece, child)
            Hangman.Guess.Wrong -> app.sounds.play(Sounds.Effect.Wrong, child)
        }
        changes++
        if (game.solved) {
            app.say(listOf("Yes! ${game.word}!", "Well done! ${game.word}!", "Brilliant! ${game.word}!").random())
            scope.launch {
                delay(2_000)
                onOver(true)
            }
        } else if (game.lost) {
            app.say("Oh no, all the balloons popped! The word was ${game.word}. Let's try another!")
            scope.launch {
                delay(4_500)
                onOver(false)
            }
        }
    }

    changes.let { }
    val solved = game.solved
    val lost = game.lost
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Balloons(game.balloons, game.left, Modifier.width(260.dp).fillMaxHeight())
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                if ((say || game.over) && game.picture.isNotBlank()) QuizPicture(app, game.picture, 100.dp)
                Gaps(game.word, game.shown(), solved, lost)
                Text(
                    when {
                        solved -> "You found it!"
                        lost -> "The word was ${game.word}"
                        else -> "${game.left} balloon${if (game.left == 1) "" else "s"} left"
                    },
                    color = if (lost) Coral else Color.White.copy(alpha = 0.85f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Up to 13 letters a row: the whole alphabet is two rows.
        val rows = (game.letters.size + 12) / 13
        val perRow = (game.letters.size + rows - 1) / rows
        val big = game.letters.size <= 8
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            game.letters.chunked(perRow).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp)) {
                    row.forEachIndexed { c, letter ->
                        val tried = game.tried(letter)
                        LetterButton(
                            letter,
                            size = if (big) 84 else 56,
                            faded = tried,
                            good = tried && letter in game.word,
                            modifier = (if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        if (moved) app.sounds.play(Sounds.Effect.Move, child)
                                        moved = true
                                    }
                                },
                            onClick = { if (!game.over) pick(letter) },
                        )
                    }
                }
            }
        }
    }
}

/** The word's letters, found ones filled in. Once it's over, missing ones show in coral. */
@Composable
private fun Gaps(word: String, shown: String, solved: Boolean, lost: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        word.forEachIndexed { i, letter ->
            val known = shown[i] != '_'
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (known || lost) "$letter" else " ",
                    color = when {
                        solved -> Leaf
                        !known -> Coral
                        else -> Color.White
                    },
                    fontSize = if (word.length > 7) 52.sp else 64.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(if (word.length > 7) 46.dp else 58.dp),
                )
                Box(Modifier.width(if (word.length > 7) 46.dp else 58.dp).height(6.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
            }
        }
    }
}

/** Never disabled, so it can keep the remote's focus; a tried one just does nothing. */
@Composable
private fun LetterButton(letter: Char, size: Int, faded: Boolean, good: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(size.dp).alpha(if (faded && !good) 0.3f else 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (good) Leaf else Reef,
            contentColor = if (good) Ink else Color.White,
            focusedContainerColor = Tangerine,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = shape)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$letter", fontSize = (size * 0.55f).sp, fontWeight = FontWeight.Black)
        }
    }
}

private val BALLOON_COLORS = listOf(
    Color(0xFFFF5C7A), Color(0xFFFFC93D), Color(0xFF4D96FF), Color(0xFF7BD389), Color(0xFFA259FF),
    Color(0xFFFF8A3D), Color(0xFF2EC4B6), Color(0xFFFF9EC7), Color(0xFFC3E84B), Color(0xFF6FD3FF),
)

/** A bunch of [count] balloons on strings; those past [left] have popped (last ones first). */
@Composable
private fun Balloons(count: Int, left: Int, modifier: Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(2_400), RepeatMode.Restart), label = "bob")
    val pops = List(count) { i ->
        animateFloatAsState(if (i >= left) 1f else 0f, tween(450), label = "pop$i").value
    }
    Canvas(modifier) {
        val top = (count + 1) / 2
        val w = size.width
        val r = minOf(w / (top + 0.6f) / 2f, 34.dp.toPx())
        val hand = Offset(w / 2, size.height - 12.dp.toPx())
        for (i in 0 until count) {
            // Two rows, the second tucked in between and below the first.
            val row = if (i < top) 0 else 1
            val inRow = if (row == 0) i else i - top
            val across = if (row == 0) top else count - top
            val x = w / 2 + (inRow - (across - 1) / 2f) * r * 2.1f
            val sway = sin((bob + i * 0.17f) * 2 * PI).toFloat() * 4.dp.toPx()
            val y = r * 1.3f + row * r * 1.5f + sway
            val pop = pops[i]
            val centre = Offset(x, y)
            if (pop < 1f) {
                // The string, from the knot to the hand.
                drawLine(Color.White.copy(alpha = 0.7f * (1 - pop)), Offset(x, y + r * 1.2f), hand, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                balloon(centre, r, BALLOON_COLORS[i % BALLOON_COLORS.size], 1f + pop * 0.5f, 1f - pop)
            }
            if (pop > 0f && pop < 1f) {
                // A burst of lines where it popped.
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    val dir = Offset(kotlin.math.cos(a).toFloat(), sin(a).toFloat())
                    drawLine(Color.White.copy(alpha = 1 - pop), centre + dir * (r * (0.6f + pop)), centre + dir * (r * (1f + pop * 1.4f)), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        drawCircle(Color.White, 7.dp.toPx(), hand)
    }
}

private fun DrawScope.balloon(centre: Offset, r: Float, color: Color, grow: Float, alpha: Float) {
    scale(grow, centre) {
        val size = Size(r * 2, r * 2.4f)
        val topLeft = centre - Offset(r, r * 1.2f)
        drawOval(color.copy(alpha = alpha), topLeft, size)
        // A shine, and the knot at the bottom.
        drawOval(Color.White.copy(alpha = 0.45f * alpha), topLeft + Offset(r * 0.45f, r * 0.35f), Size(r * 0.45f, r * 0.65f))
        drawCircle(color.copy(alpha = alpha), r * 0.16f, centre + Offset(0f, r * 1.25f))
    }
}
