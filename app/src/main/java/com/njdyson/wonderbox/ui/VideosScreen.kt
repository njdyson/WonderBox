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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.player.PlayerWebView
import kotlinx.coroutines.delay

/**
 * One child's videos, on a screen of their own colour: a grid of big thumbnails, three across,
 * so about six fit on screen. The focused one tilts and grows like a sticker being picked up.
 * The focused one's title is read aloud, and after a moment its player starts loading in
 * the background so it starts quickly when chosen.
 */
@Composable
fun VideosScreen(app: AppState, child: Child, onPlay: (Video) -> Unit) {
    val context = LocalContext.current
    val videos = app.library.videosFor(child.id)
    val color = parseColor(child.color)
    val ink = color.onColor()
    var focusedIndex by rememberSaveable(child.id) { mutableStateOf(0) }
    val grid = rememberLazyGridState()
    val first = remember { FocusRequester() }

    PlayBackground(color) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(64.dp).background(color, CircleShape).border(4.dp, Color.White, CircleShape)) {
                    ChildFace(child, app.serverUrl, textSize = 32.sp, shape = CircleShape)
                }
                Text("${child.name}'s videos", color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f))
                Logo(boxColor = if (ink == Ink) Ink else Color.White)
            }

            if (videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No videos yet. Ask a grown-up to add some!", color = ink, fontSize = 26.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = grid,
                    // Room around each card for it to grow when focused without touching its neighbours.
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(videos, key = { _, v -> v.id }) { i, video ->
                        VideoCard(
                            video,
                            ink = ink,
                            tilt = if (i % 2 == 0) -2f else 2f,
                            onClick = { onPlay(video) },
                            onFocus = { focusedIndex = i },
                            modifier = if (i == focusedIndex.coerceAtMost(videos.lastIndex)) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(videos.size) {
        if (videos.isNotEmpty()) {
            grid.scrollToItem(focusedIndex.coerceAtMost(videos.lastIndex))
            first.requestFocusWhenReady()
        }
    }
    // Read the title once focus settles, then preload its player if it stays there.
    val focusedVideo = videos.getOrNull(focusedIndex)
    LaunchedEffect(focusedVideo?.id) {
        val video = focusedVideo ?: return@LaunchedEffect
        delay(400)
        app.speak(video.title)
        delay(1_100)
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
private fun VideoCard(video: Video, ink: Color, tilt: Float, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (focused) tilt else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tilt",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged {
                if (it.isFocused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Ink, focusedContainerColor = Ink),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            // A white edge like a sticker: thin normally, thick when chosen.
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.7f)), shape = RoundedCornerShape(22.dp)),
                focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = RoundedCornerShape(22.dp)),
            ),
        ) {
            // hqdefault is 4:3 with black bars; cropping to 16:9 removes them.
            AsyncImage(
                model = video.thumbnail.ifBlank { "https://i.ytimg.com/vi/${video.youtubeId}/hqdefault.jpg" },
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Fixed height, so rows line up whatever the title length.
        Text(
            video.title,
            color = if (focused) ink else ink.copy(alpha = 0.8f),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(46.dp).padding(horizontal = 4.dp),
        )
    }
}
