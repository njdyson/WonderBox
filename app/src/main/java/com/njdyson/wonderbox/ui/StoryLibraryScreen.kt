package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
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
 * The Story maker tile in Games: "Make a new story" first (when the story maker is on), then the child's
 * finished stories, newest first, each with the picture Gemini drew for it (or its pictures,
 * until it's drawn). Picking a story reads it aloud page by page, like a picture book.
 */
@Composable
fun StoryLibraryScreen(app: AppState, child: Child, canMake: Boolean, onNew: () -> Unit) {
    val stories = app.storiesFor(child.id)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val story = stories.firstOrNull { it.id == open }
    // While a picture is being drawn, look for it every 20 seconds.
    val drawing = stories.any { it.drawing }
    LaunchedEffect(drawing) {
        while (drawing) {
            delay(20_000)
            app.refreshStories()
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        if (story != null) {
            StoryReader(app, child, story, onClose = { open = null })
        } else {
            Shelf(app, child, stories, canMake, onNew, onOpen = {
                EventLog.log("story.read", child.id, subject = it.id)
                open = it.id
            })
        }
    }
}

@Composable
private fun Shelf(app: AppState, child: Child, stories: List<SavedStory>, canMake: Boolean, onNew: () -> Unit, onOpen: (SavedStory) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    Column(Modifier.fillMaxSize().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = 48.dp)) { PlayHeader(child, "✏️", "Story maker", "") }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (canMake) {
                item(key = "new") {
                    ShelfCard(Modifier.focusRequester(first), onNew) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFFFFF6E5), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                            contentAlignment = Alignment.Center,
                        ) { EmojiPicture("✏️", 110.dp) }
                        CardTitle("Make a new story")
                    }
                }
            }
            itemsIndexed(stories, key = { _, s -> s.id }) { i, s ->
                ShelfCard(if (i == 0 && !canMake) Modifier.focusRequester(first) else Modifier, { onOpen(s) }) {
                    StoryCover(app, s, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    CardTitle(s.title.ifBlank { "My story" })
                }
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ShelfCard(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
        Column { content() }
    }
}

/** A story's picture, a brush painting while Gemini draws it, or its pictures on a page if it can't. */
@Composable
private fun StoryCover(app: AppState, story: SavedStory, modifier: Modifier) {
    if (story.drawing && story.picture.isBlank()) {
        Painting(modifier.background(Color(0xFFFFF6E5), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
    } else if (story.picture.isNotBlank()) {
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

/** A brush leaving dabs of paint across the page, so it's clear the picture is on its way. */
@Composable
private fun Painting(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "painting").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart), label = "painting",
    )
    val colours = listOf(Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFFBA68C8))
    val dabs = 14
    // Where the brush is at [p] (0 to 1): a gentle zigzag across the page.
    fun at(p: Float) = Offset(0.12f + 0.76f * p, 0.5f + 0.22f * kotlin.math.sin(p * 4 * Math.PI).toFloat())
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0 until (t * dabs).toInt()) {
                val o = at(k / dabs.toFloat())
                drawCircle(colours[k % colours.size].copy(alpha = 0.75f), radius = size.minDimension * 0.07f, center = Offset(o.x * size.width, o.y * size.height))
            }
        }
        val brush = at(t)
        EmojiPicture(
            "🖌️",
            maxWidth * 0.18f,
            Modifier.align(Alignment.TopStart).offset(x = maxWidth * brush.x - maxWidth * 0.05f, y = maxHeight * brush.y - maxWidth * 0.15f),
        )
        Text(
            "Drawing the picture…",
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
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
    // Have the storyteller ready for every page, not just the first.
    LaunchedEffect(story.id) { app.voice.prepare(story.parts.map { it.text }, slow = true) }
    LaunchedEffect(page, reading) {
        if (!reading) return@LaunchedEffect
        if (page == 0) {
            app.sayAndWait(story.title)
            delay(300)
        }
        app.sayAndWait(story.parts.getOrNull(page)?.text.orEmpty(), slow = true)
        delay(600)
        if (page < pages - 1) page++ else {
            reading = false
            app.say("The end!")
        }
    }
    val part = story.parts.getOrNull(page)
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayHeader(child, "📖", story.title.ifBlank { "My story" }, "") }
            Progress(page + 1, pages)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (story.picture.isNotBlank() || story.drawing) {
                StoryCover(app, story, Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(24.dp)))
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFF6E5), RoundedCornerShape(24.dp)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (part != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (app.voice.loading) GettingReady()
                        else if (part.picture.isNotBlank()) EmojiPicture(part.picture, 72.dp)
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
