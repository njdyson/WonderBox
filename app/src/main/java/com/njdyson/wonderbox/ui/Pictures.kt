package com.njdyson.wonderbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.njdyson.wonderbox.data.Avatars

/** True for a quiz image on the server, false for an emoji. */
fun isImagePath(picture: String) = picture.startsWith("/")

/**
 * A quiz picture, [size] square: an emoji, a patch of colour ("color:#RRGGBB", for the photo
 * quiz's colour answers), or a quiz image fetched from the server with this TV's key (the files
 * are protected, like the photos).
 */
@Composable
fun QuizPicture(app: AppState, picture: String, size: Dp, modifier: Modifier = Modifier) {
    if (picture.startsWith("color:")) {
        Box(
            modifier
                .size(size * 0.8f)
                .background(parseColor(picture.removePrefix("color:")), CircleShape)
                .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        )
    } else if (isImagePath(picture)) {
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
 * A line of [text] with emoji in it ("🦩", or "🍎🍎 + 🍎" for a sum), [fontSize] high: each emoji
 * that has a bundled Fluent picture is drawn as that picture, so the TV never shows a blank box
 * for an emoji its font doesn't have (older Fire TVs can't draw 🦩 or 🥫). Anything else is
 * ordinary text.
 */
@Composable
fun PictureLine(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pictures = mutableMapOf<String, String>()
    val line = buildAnnotatedString {
        for (piece in emojiPieces(text)) {
            val uri = if (piece.length > 1 || piece[0].code > 0x2000) Avatars.uri(context, piece) else null
            if (uri != null) {
                pictures[piece] = uri
                appendInlineContent(piece, piece)
            } else {
                append(piece)
            }
        }
    }
    val inline = pictures.mapValues { (_, uri) ->
        InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.Center)) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    Text(line, fontSize = fontSize, textAlign = TextAlign.Center, inlineContent = inline, modifier = modifier)
}

/**
 * [text] cut into pieces: each emoji on its own (with any variation selector, skin tone or
 * joined parts that follow it), and each other character on its own.
 */
fun emojiPieces(text: String): List<String> {
    val pieces = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        var end = i + Character.charCount(text.codePointAt(i))
        while (end < text.length) {
            val next = text.codePointAt(end)
            end += when {
                // Variation selectors, keycap, skin tones.
                next == 0xFE0F || next == 0x20E3 || next in 0x1F3FB..0x1F3FF -> Character.charCount(next)
                // A zero width joiner and the part it joins on.
                next == 0x200D && end + 1 < text.length -> 1 + Character.charCount(text.codePointAt(end + 1))
                else -> break
            }
        }
        pieces += text.substring(i, end)
        i = end
    }
    return pieces
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
