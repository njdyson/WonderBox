package com.njdyson.wonderbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.njdyson.wonderbox.player.PlayerWebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/**
 * One child's screen: the games first, then rows of videos (their own, their music, each
 * channel), then the family photos, then the treats. Treats are locked until a game is finished; each finished game
 * unlocks one. The focused video's title is read aloud, and after a moment its player starts
 * loading in the background so it starts quickly when chosen.
 */
@Composable
fun VideosScreen(
    app: AppState,
    child: Child,
    onPlay: (Video) -> Unit,
    onGame: (Game) -> Unit,
    onPhoto: (Int) -> Unit,
    focusTreats: Boolean = false,
    onTreatsFocused: () -> Unit = {},
) {
    val context = LocalContext.current
    val shelves = app.library.shelvesFor(child.id)
    val treatsLeft = app.treatsFor(child.id)
    val hasTreats = shelves.any { it.treat }
    val color = parseColor(child.color)
    // The focused card ("games/Maths", "photos/<id>" or a video's ID), so focus comes back to it after a video.
    var focusedKey by rememberSaveable(child.id) { mutableStateOf<String?>(null) }
    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val restore = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Rows below the games row, in order; the first treat row's index in the column.
    val firstTreatRow = shelves.indexOfFirst { it.treat }.let { if (it < 0) -1 else it + 1 }
    // The photos row (when there are photos) sits just before the treats, pushing them down one.
    val hasPhotos = app.photos.isNotEmpty()
    val treatsIndex = firstTreatRow + if (hasPhotos && firstTreatRow > 0) 1 else 0
    val photosRow: LazyListScope.() -> Unit = {
        if (hasPhotos) {
            item(key = "photos") {
                PhotosRow(
                    app,
                    onOpen = onPhoto,
                    onFocus = {
                        focusedKey = "photos/$it"
                        focusedVideo = null
                    },
                    focusedId = focusedKey?.removePrefix("photos/").takeIf { focusedKey?.startsWith("photos/") == true && !focusTreats },
                    focusModifier = Modifier.focusRequester(restore),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Night)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Text(child.avatar.ifBlank { child.name.take(1).uppercase() }, fontSize = 28.sp, color = Deep, fontWeight = FontWeight.Black)
            }
            Text(child.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            if (hasTreats) TreatCount(treatsLeft)
            Box(Modifier.weight(1f))
            message?.let { Text(it, color = Sunshine, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Logo()
        }

        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "games") {
                RowTitle(if (hasTreats) "Play a game to win a treat" else "Games")
                Row(Modifier.padding(start = 48.dp, top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Game.entries.forEach { game ->
                        val key = "games/${game.name}"
                        GameTile(
                            game,
                            level = if (game == Game.Maths) child.games.maths else child.games.spelling,
                            onClick = { onGame(game) },
                            onFocus = {
                                focusedKey = key
                                focusedVideo = null
                            },
                            modifier = if (key == (focusedKey ?: "games/${Game.Maths.name}")) Modifier.focusRequester(restore) else Modifier,
                        )
                    }
                }
            }
            shelves.forEachIndexed { row, shelf ->
                if (row + 1 == firstTreatRow) photosRow()
                item(key = shelf.key) {
                    val locked = shelf.treat && treatsLeft == 0
                    Column {
                        RowTitle(if (shelf.treat) "⭐ ${shelf.title}" else shelf.title, shelf.picture)
                        LazyRow(
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(26.dp),
                        ) {
                            itemsIndexed(shelf.videos, key = { _, v -> v.id }) { i, video ->
                                val treatFocus = focusTreats && row + 1 == firstTreatRow && i == 0
                                VideoCard(
                                    video,
                                    locked = locked,
                                    onClick = {
                                        when {
                                            !video.isTreat -> onPlay(video)
                                            app.spendTreat(child.id) -> {
                                                EventLog.log("reward.unlock", child.id, subject = video.youtubeId) {
                                                    put("reason", "quiz.finish")
                                                    put("tier", video.tier)
                                                    put("title", video.title)
                                                }
                                                onPlay(video)
                                            }
                                            else -> {
                                                app.say("Play a game to unlock a treat!")
                                                message = "🔒 Play a game to unlock a treat!"
                                                scope.launch {
                                                    focusedKey = "games/${Game.Maths.name}"
                                                    list.animateScrollToItem(0)
                                                    restore.requestFocusWhenReady()
                                                    delay(3_000)
                                                    message = null
                                                }
                                            }
                                        }
                                    },
                                    onFocus = {
                                        focusedKey = video.id
                                        focusedVideo = video
                                    },
                                    modifier = if (treatFocus || (!focusTreats && video.id == focusedKey)) Modifier.focusRequester(restore) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
            if (firstTreatRow < 0) photosRow()
            if (shelves.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No videos yet. Ask a grown-up to add some!",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 22.sp,
                        modifier = Modifier.padding(start = 48.dp, top = 30.dp),
                    )
                }
            }
        }
    }

    LaunchedEffect(shelves.size, focusTreats) {
        if (focusTreats && firstTreatRow > 0) {
            list.scrollToItem(treatsIndex)
            restore.requestFocusWhenReady()
            app.say("Pick a treat!")
            onTreatsFocused()
        } else {
            restore.requestFocusWhenReady()
        }
    }
    // Read the title once focus settles, then preload its player if it stays there.
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
private fun RowTitle(text: String, picture: String = "") {
    Row(
        Modifier.padding(start = 48.dp, end = 48.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (picture.isNotBlank()) {
            AsyncImage(model = picture, contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape))
        }
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** "⭐ 2" when they've treats to pick, a lock when they haven't. */
@Composable
private fun TreatCount(count: Int) {
    Text(
        if (count > 0) "⭐ $count" else "🔒",
        color = if (count > 0) Deep else Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .background(if (count > 0) Sunshine else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun GameTile(game: Game, level: Int, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (game == Game.Maths) listOf(Sky, Color(0xFF2C7DA0)) else listOf(Bubblegum, Color(0xFFC2185B))
    Surface(
        onClick = onClick,
        modifier = modifier.width(280.dp).height(104.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(6.dp, Sunshine), shape = RoundedCornerShape(24.dp))),
    ) {
        Row(
            Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(game.icon, fontSize = 48.sp)
            Column {
                Text(game.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("Level $level", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun VideoCard(video: Video, locked: Boolean, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(256.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged {
                if (it.isFocused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Deep, focusedContainerColor = Deep),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(6.dp, Sunshine), shape = RoundedCornerShape(18.dp)),
            ),
        ) {
            // hqdefault is 4:3 with black bars; cropping to 16:9 removes them.
            AsyncImage(
                model = video.thumbnail.ifBlank { "https://i.ytimg.com/vi/${video.youtubeId}/hqdefault.jpg" },
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (locked) {
                Box(Modifier.fillMaxSize().background(Deep.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
                    Text("🔒", fontSize = 44.sp)
                }
            } else if (video.isTreat) {
                Text(
                    "⭐",
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Sunshine, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        // Fixed height, so rows line up whatever the title length.
        Text(
            video.title,
            color = if (focused) Sunshine else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp).padding(horizontal = 4.dp),
        )
    }
}
