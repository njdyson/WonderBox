package com.njdyson.wonderbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.data.SavedStory
import kotlinx.coroutines.delay

/**
 * A child's finished stories from the story maker, newest first, each with the picture Gemini
 * drew for it (or its hero, until the picture's ready). Picking one reads it aloud page by page,
 * like a picture book.
 */
@Composable
fun StoryLibraryScreen(app: AppState, child: Child) {
    val stories = app.storiesFor(child.id)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val story = stories.firstOrNull { it.id == open }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        if (story != null) {
            StoryReader(app, child, story, onClose = { open = null })
        } else {
            Shelf(app, child, stories, onOpen = {
                EventLog.log("story.read", child.id, subject = it.id)
                open = it.id
            })
        }
    }
}

@Composable
private fun Shelf(app: AppState, child: Child, stories: List<SavedStory>, onOpen: (SavedStory) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(Modifier.fillMaxSize().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = 48.dp)) { PlayHeader(child, "📚", "My stories", "") }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(stories, key = { _, s -> s.id }) { i, s ->
                val shape = RoundedCornerShape(20.dp)
                Surface(
                    onClick = { onOpen(s) },
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Reef,
                        contentColor = Color.White,
                        focusedContainerColor = Tangerine,
                        focusedContentColor = Ink,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = shape)),
                ) {
                    Column {
                        StoryCover(app, s, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Text(
                            s.title.ifBlank { "My story" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A story's picture, or its hero on a page until Gemini has drawn one. */
@Composable
private fun StoryCover(app: AppState, story: SavedStory, modifier: Modifier) {
    if (story.picture.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.photoUrl(story.picture))
                .addHeader("X-Device-Key", app.deviceKey.orEmpty())
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        )
    } else {
        Box(modifier.background(Color(0xFFFFF6E5), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                story.parts.take(3).forEach { EmojiPicture(it.picture.ifBlank { "📖" }, 58.dp) }
            }
        }
    }
}

/** A saved story read aloud page by page; the arrows go back and forward a page. */
@Composable
private fun StoryReader(app: AppState, child: Child, story: SavedStory, onClose: () -> Unit) {
    var page by remember(story.id) { mutableIntStateOf(0) }
    var reading by remember(story.id) { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    val pages = story.parts.size
    DisposableEffect(story.id) { onDispose { app.stopSpeaking() } }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    LaunchedEffect(page, reading) {
        if (!reading) return@LaunchedEffect
        if (page == 0) {
            app.sayAndWait(story.title)
            delay(300)
        }
        app.sayAndWait(story.parts.getOrNull(page)?.text.orEmpty())
        delay(600)
        if (page < pages - 1) page++ else {
            reading = false
            app.say("The end!")
        }
    }
    val part = story.parts.getOrNull(page)
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayHeader(child, "📚", story.title.ifBlank { "My story" }, "") }
            Progress(page + 1, pages)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (story.picture.isNotBlank()) {
                StoryCover(app, story, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)))
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFF6E5), RoundedCornerShape(24.dp)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (part != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (part.picture.isNotBlank()) EmojiPicture(part.picture, 72.dp)
                        val size = if (part.text.length > 260) 22.sp else 26.sp
                        Text(part.text, color = Ink, fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
            BigButton("◀", Modifier) {
                if (page > 0) {
                    page--
                    reading = true
                }
            }
            BigButton(if (reading) "🔊 Reading…" else "🔊 Read it again", Modifier.focusRequester(first)) {
                if (!reading) {
                    page = 0
                    reading = true
                }
            }
            BigButton("▶", Modifier) {
                if (page < pages - 1) {
                    page++
                    reading = true
                }
            }
            BigButton("📚 My stories", Modifier, onClose)
        }
    }
}
