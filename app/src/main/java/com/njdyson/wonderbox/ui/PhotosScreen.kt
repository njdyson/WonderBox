package com.njdyson.wonderbox.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.viewinterop.AndroidView
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
 *
 * Home videos are in among the photos, showing their still with a big ▶ until OK plays them;
 * OK then pauses and carries on. In the slideshow a video plays by itself, to the end, and then
 * the slideshow moves on.
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
    // The video on screen: started, paused, or it wouldn't play. Reset when the photo changes.
    var videoOn by remember(photo.id) { mutableStateOf(false) }
    var videoPaused by remember(photo.id) { mutableStateOf(false) }
    var videoFailed by remember(photo.id) { mutableStateOf(false) }

    fun step(delta: Int) {
        index = (current + delta).mod(photos.size)
    }

    fun playOrPause() {
        when {
            !photo.isVideo -> playing = !playing
            videoFailed -> Unit
            !videoOn -> {
                videoOn = true
                videoPaused = false
            }
            else -> videoPaused = !videoPaused
        }
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
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> playOrPause()
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
        if (photo.isVideo && videoOn) {
            HomeVideoPlayer(
                url = app.photoUrl(photo.video),
                deviceKey = app.deviceKey.orEmpty(),
                paused = videoPaused,
                onEnd = {
                    if (playing && photos.size > 1) step(1) else videoOn = false
                },
                onError = {
                    videoOn = false
                    videoFailed = true
                    pressed++
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (photo.isVideo && (!videoOn || videoPaused)) {
            // The big play button on a video's still (or while it's paused).
            Box(
                Modifier.align(Alignment.Center).size(132.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (videoFailed) "✕" else "▶", color = Color.White, fontSize = 60.sp, modifier = Modifier.padding(start = if (videoFailed) 0.dp else 10.dp))
            }
        }

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
                val hint = when {
                    !photo.isVideo -> if (playing) "OK to stop" else "OK to play"
                    videoFailed -> "This video won't play"
                    !videoOn -> "Video, ${videoLength(photo.duration)}. OK to watch"
                    videoPaused -> "OK to carry on"
                    else -> "OK to pause"
                }
                Text(hint, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    LaunchedEffect(pressed, playing, videoPaused) {
        showOverlay = true
        // It stays while a video is paused, so it's clear how to carry on.
        if (videoPaused) return@LaunchedEffect
        delay(if (playing) 2_500 else 4_000)
        showOverlay = false
    }
    // The slideshow: a key press restarts the timer, so a photo someone just flipped to stays up.
    // A video plays instead, and the slideshow moves on when it ends (or straight away if it won't play).
    LaunchedEffect(playing, current, pressed) {
        if (!playing) return@LaunchedEffect
        if (photo.isVideo && !videoFailed) {
            if (!videoOn) {
                videoOn = true
                videoPaused = false
            }
            return@LaunchedEffect
        }
        delay(if (videoFailed) 2_000 else SLIDE_MS)
        step(1)
    }
    LaunchedEffect(photo.id, videoOn) {
        if (photo.isVideo && videoOn) EventLog.log("homevideo.play", childId, subject = photo.id, value = photo.duration)
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

/** A video's length as 0:42 or 12:05. */
internal fun videoLength(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * A home video playing, fitted to the screen. Android's own VideoView plays it: the server
 * makes every video a plain H.264 MP4, which every Fire Stick can play, and sends it in ranges
 * so it starts quickly. It never takes the remote's focus, so the slideshow's keys still work.
 */
@Composable
private fun HomeVideoPlayer(url: String, deviceKey: String, paused: Boolean, onEnd: () -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val end by rememberUpdatedState(onEnd)
    val error by rememberUpdatedState(onError)
    var view by remember { mutableStateOf<VideoView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                isFocusable = false
                val video = VideoView(context).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnCompletionListener { end() }
                    setOnErrorListener { _, _, _ ->
                        error()
                        true
                    }
                    setVideoURI(Uri.parse(url), mapOf("X-Device-Key" to deviceKey))
                    start()
                }
                addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                view = video
            }
        },
    )
    LaunchedEffect(paused, view) {
        val video = view ?: return@LaunchedEffect
        if (paused) video.pause() else video.start()
    }
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
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

/** A photo's thumbnail. A home video's has a ▶ and its length in the corner. */
@Composable
internal fun PhotoThumb(app: AppState, photo: Photo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(app.photoUrl(photo.thumb.ifBlank { photo.image }))
                .addHeader("X-Device-Key", app.deviceKey.orEmpty())
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.isVideo) {
            Text(
                "▶ ${videoLength(photo.duration)}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
