package com.njdyson.wonderbox.ui

import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Photo
import com.njdyson.wonderbox.games.Jigsaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Jigsaws of the family photos, played with just the arrows and OK. The child picks a photo,
 * then it's cut into the child's size of grid ([com.njdyson.wonderbox.data.GameSettings.jigsaw])
 * and jumbled. OK picks a piece up, the arrows carry it, OK puts it down and it swaps with
 * the piece there. Pieces in the right place click in and stay. Finishing one earns a treat,
 * like a game round. Back puts a carried piece down, or goes back to the child's screen.
 */
@Composable
fun JigsawScreen(
    app: AppState,
    child: Child,
    hasTreats: Boolean,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    /** Start straight on this photo (for screenshots); normally the child picks one. */
    startPhoto: Photo? = null,
    random: Random = Random,
) {
    var photo by remember { mutableStateOf(startPhoto) }
    var lastPhotoId by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        val picked = photo
        if (picked == null) {
            PickPhoto(app, child, lastPhotoId, onPick = { photo = it })
        } else {
            key(picked.id, round) {
                Puzzle(
                    app, child, picked, hasTreats, random, onFinished, onPickTreat, onBack,
                    onAnother = {
                        lastPhotoId = picked.id
                        photo = null
                        round++
                    },
                )
            }
        }
    }
}

/** "Pick a photo": the photos row, starting on the one after the last jigsaw's photo. */
@Composable
private fun PickPhoto(app: AppState, child: Child, lastPhotoId: String?, onPick: (Photo) -> Unit) {
    val photos = app.photos
    val focus = remember { FocusRequester() }
    var focusedId by remember {
        val last = photos.indexOfFirst { it.id == lastPhotoId }
        mutableStateOf(photos.getOrNull((last + 1).mod(photos.size.coerceAtLeast(1)))?.id)
    }
    LaunchedEffect(Unit) {
        app.say("Pick a photo for your jigsaw!")
        focus.requestFocusWhenReady()
    }
    Column(Modifier.fillMaxSize().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Header(child, "Jigsaw", "Pick a photo")
        Spacer(Modifier.weight(0.4f))
        if (photos.isEmpty()) {
            Text("No photos yet. Ask a grown-up to add some!", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PhotosRow(
                app,
                onOpen = { onPick(photos[it]) },
                onFocus = { focusedId = it },
                focusedId = focusedId,
                focusModifier = Modifier.focusRequester(focus),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Header(child: Child, title: String, right: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).background(parseColor(child.color), CircleShape), contentAlignment = Alignment.Center) {
            Text("🧩", fontSize = 28.sp)
        }
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Box(Modifier.weight(1f))
        Text(right, color = Tangerine, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Puzzle(
    app: AppState,
    child: Child,
    photo: Photo,
    hasTreats: Boolean,
    random: Random,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    onAnother: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val side = child.games.jigsaw.coerceIn(2, 5)
    val jigsaw = remember { Jigsaw.jumbled(side, random) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Jigsaw.pieces changes in place; reading this redraws the board.
    var changes by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf<Int?>(null) }
    // A carried piece's right place, shown after a while if it hasn't found it.
    var hint by remember { mutableStateOf<Int?>(null) }
    var done by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    val clicked = remember { mutableStateOf(emptyList<Int>()) }
    val glow = remember { Animatable(0f) }
    val startedAt = remember { System.currentTimeMillis() }
    val board = remember { FocusRequester() }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(photo.id) {
        val request = ImageRequest.Builder(context)
            .data(app.photoUrl(photo.image))
            .addHeader("X-Device-Key", app.deviceKey.orEmpty())
            // Big enough to look sharp on the board, small enough for a Fire Stick's memory.
            .size(1280)
            .allowHardware(false)
            .build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null) {
            failed = true
            return@LaunchedEffect
        }
        image = bitmap.asImageBitmap()
        EventLog.log("jigsaw.start", child.id, subject = photo.id) { put("size", side) }
        board.requestFocusWhenReady()
        app.say("Press OK to pick up a piece, and put it where it goes!")
    }

    LaunchedEffect(held) {
        hint = null
        val carrying = held ?: return@LaunchedEffect
        delay(10_000)
        hint = jigsaw.pieces[carrying]
    }

    fun finish() {
        done = true
        held = null
        Chimes.play(Chimes.Sound.Done)
        EventLog.log("jigsaw.finish", child.id, subject = photo.id, value = jigsaw.size.toDouble()) {
            put("size", side)
            put("moves", jigsaw.moves)
            put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
        }
        onFinished()
        scope.launch {
            delay(700)
            app.say(if (hasTreats) "You did it, ${child.name}! Pick a treat!" else "You did it, ${child.name}!")
            delay(1_300)
            showButtons = true
            firstButton.requestFocusWhenReady()
        }
    }

    fun press() {
        if (done) return
        val carrying = held
        when {
            carrying == null && jigsaw.isRight(cursor) -> Chimes.play(Chimes.Sound.Bump)
            carrying == null -> {
                held = cursor
                Chimes.play(Chimes.Sound.Pick)
            }
            carrying == cursor -> {
                held = null
                Chimes.play(Chimes.Sound.Drop)
            }
            jigsaw.isRight(cursor) -> Chimes.play(Chimes.Sound.Bump)
            else -> {
                val right = jigsaw.swap(carrying, cursor)
                held = null
                changes++
                if (jigsaw.solved) {
                    clicked.value = listOf(carrying, cursor)
                    finish()
                } else if (right > 0) {
                    clicked.value = listOf(carrying, cursor).filter(jigsaw::isRight)
                    Chimes.play(Chimes.Sound.Right)
                    app.say(listOf("Yes!", "Click!", "Well done!", "That's it!", "Brilliant!").random())
                } else {
                    Chimes.play(Chimes.Sound.Drop)
                }
                if (right > 0) scope.launch {
                    glow.snapTo(1f)
                    glow.animateTo(0f, tween(700))
                }
            }
        }
    }

    // Back with a piece in hand just puts it down.
    BackHandler(enabled = held != null) {
        held = null
        Chimes.play(Chimes.Sound.Drop)
    }

    Column(Modifier.fillMaxSize().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val placed = changes.let { jigsaw.rightCount }
        Header(child, "Jigsaw", if (done) "🎉 All done!" else "🧩 $placed of ${jigsaw.size}")
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val picture = image
                when {
                    failed -> Text("That photo won't load right now. Press Back and try another!", color = Color.White, fontSize = 24.sp, textAlign = TextAlign.Center)
                    picture == null -> Text("🧩", fontSize = 80.sp)
                    else -> {
                        val crop = remember(picture) { cropFor(picture) }
                        val aspect = crop.second.width.toFloat() / crop.second.height
                        val width = minOf(maxWidth, maxHeight * aspect)
                        Board(
                            picture, crop, jigsaw, changes, cursor, held, hint, done, clicked.value, glow.value,
                            Modifier
                                .size(width, width / aspect)
                                .focusRequester(board)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown || done) return@onPreviewKeyEvent false
                                    when (event.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> cursor = jigsaw.step(cursor, -1, 0)
                                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> cursor = jigsaw.step(cursor, 1, 0)
                                        AndroidKeyEvent.KEYCODE_DPAD_UP -> cursor = jigsaw.step(cursor, 0, -1)
                                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> cursor = jigsaw.step(cursor, 0, 1)
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> press()
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    true
                                },
                        )
                    }
                }
            }
            Column(
                Modifier.width(250.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val picture = image
                if (done) {
                    Celebrate(child, hasTreats, showButtons, firstButton, onPickTreat, onAnother, onBack)
                } else if (picture != null) {
                    // The picture on the box, to help find where pieces go.
                    val crop = remember(picture) { cropFor(picture) }
                    Text("The picture", color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp)
                    Canvas(
                        Modifier
                            .width(250.dp)
                            .height(250.dp * crop.second.height / crop.second.width)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        drawImage(picture, crop.first, crop.second, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    }
                    Text(
                        if (held == null) "OK to pick up a piece" else "Now find its place!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Celebrate(
    child: Child,
    hasTreats: Boolean,
    showButtons: Boolean,
    firstButton: FocusRequester,
    onPickTreat: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Text("🎉", fontSize = 90.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
    Text("You did it, ${child.name}!", color = Tangerine, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    if (hasTreats) Text("You've won a treat ⭐", color = Color.White, fontSize = 22.sp)
    if (showButtons) {
        if (hasTreats) BigButton("⭐ Pick a treat", Modifier.focusRequester(firstButton), onPickTreat)
        BigButton("🧩 Another", if (hasTreats) Modifier else Modifier.focusRequester(firstButton), onAnother)
        if (!hasTreats) BigButton("Back", Modifier, onBack)
    }
}

/** The middle of the photo to cut up: as it is, unless it's very tall or very wide. */
private fun cropFor(image: ImageBitmap): Pair<IntOffset, IntSize> {
    val aspect = (image.width.toFloat() / image.height).coerceIn(0.75f, 16f / 9f)
    val w = minOf(image.width, (image.height * aspect).toInt())
    val h = minOf(image.height, (image.width / aspect).toInt())
    return IntOffset((image.width - w) / 2, (image.height - h) / 2) to IntSize(w, h)
}

@Composable
private fun Board(
    image: ImageBitmap,
    crop: Pair<IntOffset, IntSize>,
    jigsaw: Jigsaw,
    changes: Int,
    cursor: Int,
    held: Int?,
    hint: Int?,
    done: Boolean,
    clicked: List<Int>,
    glow: Float,
    modifier: Modifier,
) {
    val side = jigsaw.side
    // The cursor glides between places, carrying the piece with it.
    val cx by animateFloatAsState((cursor % side).toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "cx")
    val cy by animateFloatAsState((cursor / side).toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "cy")
    val lift by animateFloatAsState(if (held != null) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lift")
    // Gaps between the pieces close up once it's finished.
    val gapScale by animateFloatAsState(if (done) 0f else 1f, tween(600), label = "gap")
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")

    Canvas(modifier) {
        changes.let { } // Redraw when the pieces move.
        val cell = Size(size.width / side, size.height / side)
        val gap = 3.dp.toPx() * gapScale
        drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = CornerRadius(8.dp.toPx()))

        fun pieceAt(piece: Int, topLeft: Offset, scale: Float = 1f) {
            // Edges from the same sums as the neighbours', so finished pieces meet without a seam.
            fun edge(i: Int, length: Int) = i * length / side
            val px = piece % side
            val py = piece / side
            val src = IntOffset(crop.first.x + edge(px, crop.second.width), crop.first.y + edge(py, crop.second.height))
            val srcSize = IntSize(edge(px + 1, crop.second.width) - edge(px, crop.second.width), edge(py + 1, crop.second.height) - edge(py, crop.second.height))
            val grow = Offset(cell.width * (scale - 1) / 2, cell.height * (scale - 1) / 2)
            val left = (topLeft.x - grow.x + gap / 2).roundToInt()
            val top = (topLeft.y - grow.y + gap / 2).roundToInt()
            val right = (topLeft.x + cell.width + grow.x - gap / 2).roundToInt()
            val bottom = (topLeft.y + cell.height + grow.y - gap / 2).roundToInt()
            drawImage(image, src, srcSize, dstOffset = IntOffset(left, top), dstSize = IntSize(right - left, bottom - top))
        }
        fun slotOffset(slot: Int) = Offset(slot % side * cell.width, slot / side * cell.height)

        for (slot in 0 until jigsaw.size) {
            val at = slotOffset(slot)
            if (slot == held) {
                // Where the carried piece came from: an empty space it can go back to.
                drawRect(Color.White.copy(alpha = 0.12f), at + Offset(gap / 2, gap / 2), Size(cell.width - gap, cell.height - gap))
                continue
            }
            pieceAt(jigsaw.pieces[slot], at)
            if (slot in clicked && glow > 0f) {
                drawRect(Color.White.copy(alpha = 0.6f * glow), at, cell)
            }
        }

        if (hint != null && !done) {
            dashedBox(slotOffset(hint), cell, Tangerine.copy(alpha = pulse), 5.dp.toPx())
        }

        if (!done) {
            val at = Offset(cx * cell.width, cy * cell.height)
            val carrying = held
            if (carrying != null) {
                val scale = 1f + 0.12f * lift
                val grow = Offset(cell.width * (scale - 1) / 2, cell.height * (scale - 1) / 2)
                // A shadow under the lifted piece.
                drawRect(Color.Black.copy(alpha = 0.35f * lift), at - grow + Offset(10f, 12f), Size(cell.width * scale, cell.height * scale))
                pieceAt(jigsaw.pieces[carrying], at, scale)
                drawRect(Tangerine, at - grow, Size(cell.width * scale, cell.height * scale), style = Stroke(7.dp.toPx()))
            } else {
                drawRect(Color.White, at, cell, style = Stroke(6.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.dashedBox(at: Offset, size: Size, color: Color, width: Float) {
    drawRect(color, at + Offset(width / 2, width / 2), Size(size.width - width, size.height - width),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
}
