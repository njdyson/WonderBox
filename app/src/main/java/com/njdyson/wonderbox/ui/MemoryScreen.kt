package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Photo
import com.njdyson.wonderbox.games.MEMORY
import com.njdyson.wonderbox.games.Memory
import com.njdyson.wonderbox.games.MemorySetup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Memory pairs: cards face down in a grid, moved between with the arrows. OK turns one over;
 * turn over two the same and they stay up, otherwise they turn back. Pictures are the bundled
 * animals, the child's family photos, or both ([MemorySetup.pictures]). Nothing is ever lost:
 * the game ends when every pair is found.
 */
@Composable
fun MemoryScreen(
    app: AppState,
    child: Child,
    stars: Int,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    photos: List<Photo> = app.photosFor(child.id),
    random: Random = Random,
) {
    var game by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        if (finished) {
            Finished(child, stars, onPlayAgain = {
                game++
                finished = false
            }, onBack = onBack, app = app)
        } else {
            key(game) {
                Pairs(app, child, photos, random, onDone = {
                    finished = true
                    onFinished()
                })
            }
        }
    }
}

@Composable
private fun Pairs(app: AppState, child: Child, photos: List<Photo>, random: Random, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup = child.games.memory
    val memory = remember { deal(context, setup, photos, random) }
    val byId = remember(photos) { photos.associateBy { it.id } }
    // Memory changes in place; reading this redraws the cards.
    var changes by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val startedAt = remember { System.currentTimeMillis() }
    val first = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        EventLog.log("memory.start", child.id, subject = MEMORY) {
            put("pairs", memory.pairs)
            put("pictures", setup.pictures)
        }
        first.requestFocusWhenReady()
        app.say("Find the pairs! Press OK to turn a card over.")
    }

    fun turn(card: Int) {
        if (done) return
        val result = memory.flip(card)
        changes++
        when (result) {
            Memory.Flip.Ignored -> app.sounds.chime(Chimes.Sound.Bump, child)
            Memory.Flip.First -> app.sounds.chime(Chimes.Sound.Pick, child)
            Memory.Flip.NoPair -> {
                app.sounds.chime(Chimes.Sound.Drop, child)
                val shownAt = memory.flips
                scope.launch {
                    delay(1_500)
                    // Unless the child has already turned another card, which hides these two.
                    if (memory.showingNoPair && memory.flips == shownAt) {
                        memory.hide()
                        changes++
                    }
                }
            }
            Memory.Flip.Pair -> {
                app.sounds.play(Sounds.Effect.Right, child)
                if (memory.done) {
                    done = true
                    app.sounds.play(Sounds.Effect.Finish, child)
                    EventLog.log("memory.finish", child.id, subject = MEMORY, value = memory.pairs.toDouble()) {
                        put("pairs", memory.pairs)
                        put("flips", memory.flips)
                        put("pictures", setup.pictures)
                        put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
                    }
                    scope.launch {
                        delay(1_600)
                        onDone()
                    }
                } else {
                    app.say(listOf("A pair!", "Snap!", "Well done!", "You found them!").random())
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val found = changes.let { memory.matchedPairs }
        PlayHeader(child, "🃏", "Pairs", if (done) "🎉 All found!" else "$found of ${memory.pairs} pairs")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val columns = Memory.columns(memory.size)
            val rows = (memory.size + columns - 1) / columns
            val gap = 18.dp
            val side = min((maxWidth - gap * (columns - 1)) / columns, (maxHeight - gap * (rows - 1)) / rows)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until columns) {
                            val card = row * columns + col
                            if (card >= memory.size) continue
                            changes.let { }
                            Card(
                                face = memory.faces[card],
                                up = memory.isUp(card),
                                matched = memory.isMatched(card),
                                photo = byId[memory.faces[card].removePrefix("photo:")],
                                app = app,
                                color = parseColor(child.color),
                                modifier = Modifier
                                    .size(side)
                                    .then(if (card == 0) Modifier.focusRequester(first) else Modifier)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            if (moved) app.sounds.play(Sounds.Effect.Move, child)
                                            moved = true
                                        }
                                    },
                                onClick = { turn(card) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A new game for [setup]: the pictures it says, each twice, shuffled. */
internal fun deal(context: Context, setup: MemorySetup, photos: List<Photo>, random: Random): Memory {
    val pairs = setup.pairs.coerceIn(MemorySetup.PAIRS.first(), MemorySetup.PAIRS.last())
    val animals = context.assets.list("avatars").orEmpty().sorted().map { "avatar:$it" }.shuffled(random)
    val photoFaces = photos.map { "photo:${it.id}" }.shuffled(random)
    val pictures = when (setup.pictures) {
        // Photos first; animals make up the rest when there aren't enough.
        "photos" -> photoFaces + animals
        "both" -> photoFaces.take((pairs + 1) / 2) + animals
        else -> animals
    }
    return Memory.deal(pictures.take(pairs), pairs, random)
}

/** One card: a coloured back with a star, turning over to its picture. */
@Composable
private fun Card(face: String, up: Boolean, matched: Boolean, photo: Photo?, app: AppState, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (up) 180f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "turn")
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = if (matched) Border(BorderStroke(5.dp, Leaf), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(6.dp, if (matched) Leaf else Tangerine), shape = shape),
        ),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationY = turn
                cameraDistance = 12f * density
            },
            contentAlignment = Alignment.Center,
        ) {
            if (turn < 90f) {
                Box(
                    Modifier.fillMaxSize().clip(shape).background(Brush.linearGradient(listOf(color, Reef))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("★", color = Color.White.copy(alpha = 0.85f), fontSize = 52.sp, fontWeight = FontWeight.Black)
                }
            } else {
                // Drawn mirrored, so it reads the right way round once the card has turned.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(shape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    CardFace(face, photo, app)
                }
            }
        }
    }
}

@Composable
private fun CardFace(face: String, photo: Photo?, app: AppState) {
    val context = LocalContext.current
    if (photo != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(app.photoUrl(photo.thumb.ifBlank { photo.image }))
                .addHeader("X-Device-Key", app.deviceKey.orEmpty())
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        AsyncImage(
            model = "file:///android_asset/avatars/${face.removePrefix("avatar:")}",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(0.78f),
        )
    }
}

/** A game's top line: the child's colour with the game's icon, its name, and how it's going. */
@Composable
internal fun PlayHeader(child: Child, icon: String, title: String, right: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).background(parseColor(child.color), CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 28.sp)
        }
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Box(Modifier.weight(1f))
        Text(right, color = Tangerine, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}
