package com.njdyson.wonderbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.games.Game
import com.njdyson.wonderbox.games.GameEntry
import com.njdyson.wonderbox.games.HANGMAN
import com.njdyson.wonderbox.games.JIGSAW
import com.njdyson.wonderbox.games.MEMORY
import com.njdyson.wonderbox.games.gamesFor

/** What a child can pick on their home page. */
enum class Category(val title: String, val icon: String, val colors: List<Color>) {
    Quizzes("Quizzes", "❓", listOf(Color(0xFF4D96FF), Color(0xFF2B5FD9))),
    Games("Games", "🎮", listOf(Color(0xFFA259FF), Color(0xFF6A2BD9))),
    Videos("Videos", "📺", listOf(Color(0xFFFF5C7A), Color(0xFFD12E55))),
    Music("Music", "🎵", listOf(Color(0xFF2EC4B6), Color(0xFF13867C))),
    Photos("Photos", "📷", listOf(Color(0xFF8BC34A), Color(0xFF4F8A1B))),
    /** The treats they've saved up, and any won but not picked yet. Last, so learning comes first. */
    Treats("Treats", "⭐", listOf(Color(0xFFFFC93C), Color(0xFFFF8A3D))),
}

/**
 * The categories on [child]'s home page, always in the same order, leaving out any with
 * nothing in them, so a tile never opens onto an empty screen.
 */
fun AppState.categoriesFor(child: Child): List<Category> {
    val games = library.gamesFor(child, photos = photosFor(child.id).isNotEmpty())
    return buildList {
        if (games.any { it.isQuiz }) add(Category.Quizzes)
        if (games.any { !it.isQuiz }) add(Category.Games)
        if (library.videoShelvesFor(child.id).isNotEmpty()) add(Category.Videos)
        if (library.musicShelvesFor(child.id).isNotEmpty()) add(Category.Music)
        if (photosFor(child.id).isNotEmpty()) add(Category.Photos)
        // Always there once the parent has added treats, empty or not: it's where treats are saved up.
        if (library.treatShelvesFor(child.id).isNotEmpty()) add(Category.Treats)
    }
}

/** A child's screen: their face and name, what [title] they're in, then [content] below. */
@Composable
fun ChildScreen(app: AppState, child: Child, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val color = parseColor(child.color)
    val ink = color.onColor()
    // Treats saved up, the same number as on the Treats tile.
    val treats = app.savedTreatsFor(child.id).size
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
                Text(child.name, color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                if (title != null) Text("·  $title", color = ink.copy(alpha = 0.85f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                // Nothing at all when there are none.
                if (treats > 0) {
                    Text(
                        "⭐ $treats",
                        color = Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Logo(boxColor = if (ink == Ink) Ink else Color.White)
            }
            content()
        }
    }
}

/**
 * A child's home page: a big tile for each [Category] they have something in, three to a row.
 * The tiles are always in the same places, and focus comes back to the one last opened.
 * The Treats tile shows how many treats are saved up there.
 */
@Composable
fun HomeScreen(app: AppState, child: Child, onOpen: (Category) -> Unit) {
    val ink = parseColor(child.color).onColor()
    val categories = app.categoriesFor(child)
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    val target = categories.firstOrNull { it.name == focused } ?: categories.firstOrNull()
    val saved = app.savedTreatsFor(child.id).size
    val restore = remember { FocusRequester() }

    ChildScreen(app, child) {
        if (categories.isEmpty()) {
            Text(
                "Nothing here yet. Ask a grown-up to add some!",
                color = ink,
                fontSize = 26.sp,
                modifier = Modifier.padding(start = 48.dp, top = 40.dp),
            )
            return@ChildScreen
        }
        Column(
            Modifier.fillMaxSize().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            categories.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    row.forEach { category ->
                        CategoryTile(
                            category,
                            count = if (category == Category.Treats) saved else 0,
                            onClick = { onOpen(category) },
                            onFocus = {
                                focused = category.name
                                app.speak(category.title)
                            },
                            modifier = if (category == target) Modifier.focusRequester(restore) else Modifier,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(target) { restore.requestFocusWhenReady() }
}

@Composable
private fun CategoryTile(category: Category, count: Int, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(264.dp).height(176.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.7f)), shape = RoundedCornerShape(28.dp)),
            focusedBorder = Border(BorderStroke(8.dp, Color.White), shape = RoundedCornerShape(28.dp)),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().background(Brush.linearGradient(category.colors)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(category.icon, fontSize = 64.sp)
            Text(category.title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
        if (count > 0) {
            Text(
                "$count",
                color = Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.White, CircleShape).padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}

/** The Quizzes or Games screen: a tile for each of [games], three to a row. */
@Composable
fun GamesScreen(app: AppState, child: Child, title: String, games: List<GameEntry>, onGame: (GameEntry) -> Unit) {
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    val target = games.firstOrNull { it.key == focused } ?: games.firstOrNull()
    val restore = remember { FocusRequester() }
    val hasTreats = app.library.treatShelvesFor(child.id).isNotEmpty()
    ChildScreen(app, child, title) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(games, key = { _, g -> g.key }) { i, game ->
                GameTile(
                    app,
                    // A star on the games that win a treat.
                    if (hasTreats && child.games.earnsTreat(game.key)) game.copy(subtitle = "⭐ ${game.subtitle}") else game,
                    index = i,
                    onClick = { onGame(game) },
                    onFocus = {
                        focused = game.key
                        app.speak(game.title)
                    },
                    modifier = if (game == target) Modifier.focusRequester(restore) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) { restore.requestFocusWhenReady() }
}

// Maths, Spelling, then each quiz in the next colour along.
private val TILE_COLORS = listOf(
    listOf(Color(0xFF4D96FF), Color(0xFF2B5FD9)),
    listOf(Color(0xFFA259FF), Color(0xFF6A2BD9)),
    listOf(Color(0xFFFF8A3D), Color(0xFFD9541B)),
    listOf(Color(0xFF2EC4B6), Color(0xFF13867C)),
    listOf(Color(0xFFFF5C7A), Color(0xFFD12E55)),
    listOf(Color(0xFF3FB950), Color(0xFF1E7A33)),
)

@Composable
private fun GameTile(app: AppState, game: GameEntry, index: Int, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val colors = when (game.game) {
        Game.Maths -> TILE_COLORS[0]
        Game.Spelling -> TILE_COLORS[1]
        Game.Clock -> TILE_COLORS[3]
        Game.Flags -> TILE_COLORS[5]
        null -> when (game.key) {
            JIGSAW -> TILE_COLORS[2]
            HANGMAN -> TILE_COLORS[4]
            MEMORY -> TILE_COLORS[0]
            else -> TILE_COLORS[index % TILE_COLORS.size]
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(132.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.7f)), shape = RoundedCornerShape(24.dp)),
            focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = RoundedCornerShape(24.dp)),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuizPicture(app, game.icon, 72.dp)
            Column {
                Text(game.title, color = Color.White, fontSize = when {
                    game.title.length > 10 -> 24.sp
                    game.title.length > 6 -> 28.sp
                    else -> 32.sp
                }, fontWeight = FontWeight.Black, maxLines = 2)
                Text(game.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
            }
        }
    }
}

/** The Photos screen: all of a child's photos, four to a row. OK on one opens the slideshow there. */
@Composable
fun PhotoGridScreen(app: AppState, child: Child, onOpen: (Int) -> Unit) {
    val photos = app.photosFor(child.id)
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    val target = photos.firstOrNull { it.id == focused } ?: photos.firstOrNull()
    val restore = remember { FocusRequester() }
    ChildScreen(app, child, Category.Photos.title) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(photos, key = { _, p -> p.id }) { i, photo ->
                Surface(
                    onClick = { onOpen(i) },
                    modifier = (if (photo == target) Modifier.focusRequester(restore) else Modifier)
                        .aspectRatio(4f / 3f)
                        .onFocusChanged { if (it.isFocused) focused = photo.id },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Ink, focusedContainerColor = Ink),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
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
    LaunchedEffect(Unit) { restore.requestFocusWhenReady() }
}
