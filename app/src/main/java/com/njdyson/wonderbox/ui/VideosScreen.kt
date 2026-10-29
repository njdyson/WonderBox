package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.Shelf
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.games.Game
import com.njdyson.wonderbox.games.GameEntry
import com.njdyson.wonderbox.games.JIGSAW
import com.njdyson.wonderbox.games.gamesFor
import com.njdyson.wonderbox.player.PlayerWebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/**
 * One child's videos under a [title], four across in a grid that scrolls up and down: their
 * Learning, Music, Exercise or Stories videos, or their treats (with [stars] on). The focused video's title is
 * read aloud, and after a moment its player starts loading in the background so it starts
 * quickly when chosen.
 */
@Composable
fun VideosScreen(
    app: AppState,
    child: Child,
    title: String,
    shelves: List<Shelf>,
    onPlay: (Video) -> Unit,
    /** What to say when there are no [shelves]. */
    empty: String = "No videos yet. Ask a grown-up to add some!",
    /** Said once when the screen opens, e.g. "Pick a treat!". */
    greeting: String? = null,
    /** Stars on the cards, for treats. */
    stars: Boolean = false,
    /** YouTube IDs they've watched all the way through: ticked and faded. */
    watched: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    val ink = parseColor(child.color).onColor()
    // The focused video, so focus comes back to it after a video; the first one to start with.
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    val firstKey = shelves.firstOrNull()?.videos?.firstOrNull()?.id
    val restoreKey = focusedKey?.takeIf { key -> shelves.any { s -> s.videos.any { it.id == key } } } ?: firstKey
    val restore = remember { FocusRequester() }

    ChildScreen(app, child, title) {
        // A grid that scrolls up and down, a heading over each shelf (their own videos, then
        // each channel), rather than one long row per shelf.
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            shelves.forEach { shelf ->
                // Only worth a heading when there's more than one shelf.
                if (shelves.size > 1) {
                    item(key = "title-${shelf.key}", span = { GridItemSpan(maxLineSpan) }) {
                        RowTitle(shelf.title, ink, shelf.picture)
                    }
                }
                itemsIndexed(shelf.videos, key = { _, v -> v.id }) { i, video ->
                    VideoCard(
                        video,
                        star = stars,
                        watched = video.youtubeId in watched,
                        ink = ink,
                        tilt = if (i % 2 == 0) -2f else 2f,
                        onClick = { onPlay(video) },
                        onFocus = {
                            focusedKey = video.id
                            focusedVideo = video
                        },
                        modifier = if (video.id == restoreKey) Modifier.focusRequester(restore) else Modifier,
                    )
                }
            }
            if (shelves.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        empty,
                        color = ink,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(top = 30.dp),
                    )
                }
            }
        }
    }

    LaunchedEffect(shelves.size) {
        if (greeting != null && focusedKey == null) app.say(greeting)
        if (firstKey != null) restore.requestFocusWhenReady()
    }
    // Preload the player once focus settles there. Titles aren't read aloud: hearing every one
    // while scrolling past was annoying (menus and game answers still are).
    LaunchedEffect(focusedVideo?.id) {
        val video = focusedVideo ?: return@LaunchedEffect
        delay(1_500)
        PlayerWebView.preload(context, video.youtubeId)
    }
    DisposableEffect(Unit) {
        onDispose {
            app.stopSpeaking()
            PlayerWebView.release()
        }
    }
}

@Composable
private fun RowTitle(text: String, ink: Color, picture: String = "") {
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (picture.isNotBlank()) {
            AsyncImage(model = picture, contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape))
        }
        Text(text, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VideoCard(
    video: Video,
    star: Boolean,
    watched: Boolean,
    ink: Color,
    tilt: Float,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Tilts a little when focused, like a sticker being picked up.
    val rotation by animateFloatAsState(
        if (focused) tilt else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tilt",
    )
    Column(Modifier.fillMaxWidth().graphicsLayer { rotationZ = rotation }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged {
                if (it.isFocused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Ink, focusedContainerColor = Ink),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            // A white edge like a sticker: thin normally, thick when chosen.
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.7f)), shape = RoundedCornerShape(20.dp)),
                focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = RoundedCornerShape(20.dp)),
            ),
        ) {
            // hqdefault is 4:3 with black bars; cropping to 16:9 removes them.
            AsyncImage(
                model = video.thumbnail.ifBlank { "https://i.ytimg.com/vi/${video.youtubeId}/hqdefault.jpg" },
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                // Watched ones fade back, so new ones stand out; full colour again when focused.
                alpha = if (watched && !focused) 0.45f else 1f,
                modifier = Modifier.fillMaxSize(),
            )
            if (watched) {
                Text(
                    "✓",
                    color = Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Lime, CircleShape).padding(horizontal = 10.dp, vertical = 0.dp),
                )
            }
            if (star) {
                Text(
                    "⭐",
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Tangerine, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        // Fixed height, so rows line up whatever the title length.
        Text(
            video.title,
            color = if (focused) ink else ink.copy(alpha = 0.8f),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp).padding(horizontal = 4.dp),
        )
    }
}

/** Videos across the screen: about two and a half rows show at once. */
private const val COLUMNS = 4
