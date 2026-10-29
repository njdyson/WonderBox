package com.njdyson.wonderbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.Avatars

/** True for a quiz image on the server, false for an emoji. */
fun isImagePath(picture: String) = picture.startsWith("/")

/**
 * A quiz picture, [size] square: an emoji, or a quiz image fetched from the server with this
 * TV's key (the files are protected, like the photos).
 */
@Composable
fun QuizPicture(app: AppState, picture: String, size: Dp, modifier: Modifier = Modifier) {
    if (isImagePath(picture)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.photoUrl(picture))
                .addHeader("X-Device-Key", app.deviceKey.orEmpty())
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        EmojiPicture(picture, size, modifier)
    }
}

/**
 * An emoji, [size] square: from the bundled Fluent pictures (assets/avatars) when there's one,
 * since older Fire TVs draw newer emoji (🧩, 🤸) as blanks, otherwise in the TV's own font.
 */
@Composable
fun EmojiPicture(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    val bundled = Avatars.uri(LocalContext.current, emoji)
    if (bundled != null) {
        AsyncImage(model = bundled, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = (size.value * 0.72f).sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The picture Gemini drew for the menu tile [key] ("home.games", "game.jigsaw"), filling
 * [modifier] (cropped to fit), or nothing when there isn't one. [onShown] says whether it's
 * showing, so the tile can show its emoji instead while it loads or if it can't.
 */
@Composable
fun MenuPicture(app: AppState, key: String, modifier: Modifier = Modifier, onShown: (Boolean) -> Unit = {}) {
    val path = app.library.menuPictures[key]
    LaunchedEffect(path) { if (path == null) onShown(false) }
    if (path == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(app.photoUrl(path))
            .addHeader("X-Device-Key", app.deviceKey.orEmpty())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { onShown(true) },
        onError = { onShown(false) },
        modifier = modifier,
    )
}
