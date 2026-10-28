package com.njdyson.wonderbox.ui

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Photo
import kotlinx.coroutines.delay

/** How long each photo stays up when the slideshow plays by itself. */
private const val SLIDE_MS = 6_000L

/**
 * The family photos, one at a time, filling the screen, starting from [start]. Left and right
 * flip through them (wrapping round at the ends), OK starts or stops the slideshow, Back goes
 * back to the Photos screen. The next and previous photos load in the background so flipping
 * is instant. Only [childId]'s photos are shown.
 */
@Composable
fun PhotosScreen(app: AppState, childId: String, start: Int = 0) {
    val context = LocalContext.current
    val photos = app.photosFor(childId)
    var index by rememberSaveable { mutableStateOf(start) }
    var playing by rememberSaveable { mutableStateOf(false) }
    // The counter and hint show for a few seconds after each key press, then get out of the way.
    var pressed by remember { mutableStateOf(0) }
    var showOverlay by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }

    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Lagoon), contentAlignment = Alignment.Center) {
            Text("No photos yet. Ask a grown-up to add some!", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp)
        }
        return
    }
    val current = index.mod(photos.size)
    val photo = photos[current]

    fun step(delta: Int) {
        index = (current + delta).mod(photos.size)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> step(-1)
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> step(1)
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> playing = !playing
                    // Up and down do nothing, rather than moving focus off the photo.
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> Unit
                    else -> return@onPreviewKeyEvent false
                }
                pressed++
                true
            },
    ) {
        AsyncImage(
            model = photoRequest(context, app, photo, crossfade = true),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◀", color = Tangerine, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("${current + 1} of ${photos.size}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("▶", color = Tangerine, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(if (playing) "OK to stop" else "OK to play", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    LaunchedEffect(pressed, playing) {
        showOverlay = true
        delay(if (playing) 2_500 else 4_000)
        showOverlay = false
    }
    // The slideshow: a key press restarts the timer, so a photo someone just flipped to stays up.
    LaunchedEffect(playing, current, pressed) {
        if (!playing) return@LaunchedEffect
        delay(SLIDE_MS)
        step(1)
    }
    // The activity log: how long each photo stayed up, logged as it's left.
    DisposableEffect(photo.id) {
        val shownAt = SystemClock.elapsedRealtime()
        onDispose {
            val seconds = (SystemClock.elapsedRealtime() - shownAt) / 1000.0
            if (seconds >= 1) EventLog.log("photo.view", childId, subject = photo.id, value = seconds)
        }
    }
    // Load the neighbours ahead of time.
    LaunchedEffect(current, photos.size) {
        for (delta in listOf(1, -1, 2)) {
            val next = photos[(current + delta).mod(photos.size)]
            context.imageLoader.enqueue(photoRequest(context, app, next, crossfade = false))
        }
    }
}

/** The protected photo file, with this TV's key so the server hands it over. */
private fun photoRequest(context: Context, app: AppState, photo: Photo, crossfade: Boolean): ImageRequest =
    ImageRequest.Builder(context)
        .data(app.photoUrl(photo.image))
        .addHeader("X-Device-Key", app.deviceKey.orEmpty())
        .crossfade(if (crossfade) 400 else 0)
        .build()

/**
 * "Photos": a row of [photos] thumbnails (a child's own, newest first) for a child's screen. OK on one opens the
 * slideshow there ([onOpen] gets its index). [onFocus] gets the focused photo's ID, and
 * [focusModifier] goes on the photo [focusedId], so focus can come back to it.
 * Shows nothing until there are photos.
 */
@Composable
fun PhotosRow(
    app: AppState,
    photos: List<Photo>,
    onOpen: (Int) -> Unit,
    onFocus: (String) -> Unit = {},
    focusedId: String? = null,
    focusModifier: Modifier = Modifier,
    ink: Color = Color.White,
) {
    if (photos.isEmpty()) return
    Column {
        Text(
            "📷 Photos",
            color = ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            itemsIndexed(photos, key = { _, p -> p.id }) { i, photo ->
                Surface(
                    onClick = { onOpen(i) },
                    modifier = (if (photo.id == focusedId) focusModifier else Modifier)
                        .width(256.dp)
                        .aspectRatio(16f / 9f)
                        .onFocusChanged { if (it.isFocused) onFocus(photo.id) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Ink, focusedContainerColor = Ink),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    // White sticker edges, like the video cards.
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.7f)), shape = RoundedCornerShape(20.dp)),
                        focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = RoundedCornerShape(20.dp)),
                    ),
                ) {
                    PhotoThumb(app, photo, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
internal fun PhotoThumb(app: AppState, photo: Photo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(app.photoUrl(photo.thumb.ifBlank { photo.image }))
            .addHeader("X-Device-Key", app.deviceKey.orEmpty())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
