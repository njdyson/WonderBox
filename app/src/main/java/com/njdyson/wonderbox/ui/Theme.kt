package com.njdyson.wonderbox.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import com.njdyson.wonderbox.R
import com.njdyson.wonderbox.data.Avatars
import com.njdyson.wonderbox.data.Child
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// Wonder Box's own colours: a deep lagoon with tangerine, coral and lime, so it never looks
// like BlockbusterTV (navy and yellow). The children's screens use each child's own colour.
val Lagoon = Color(0xFF0E5E66)
val Ink = Color(0xFF073B40)
val Reef = Color(0xFF177A80)
val Tangerine = Color(0xFFFF8A3D)
val Coral = Color(0xFFFF5C7A)
val Lime = Color(0xFFC3E84B)

/** Fredoka (SIL Open Font Licence): round and friendly, and easy to read from the sofa. */
val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

private fun TextStyle.fredoka() = copy(fontFamily = Fredoka)

private val FredokaTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.fredoka(), displayMedium = displayMedium.fredoka(), displaySmall = displaySmall.fredoka(),
        headlineLarge = headlineLarge.fredoka(), headlineMedium = headlineMedium.fredoka(), headlineSmall = headlineSmall.fredoka(),
        titleLarge = titleLarge.fredoka(), titleMedium = titleMedium.fredoka(), titleSmall = titleSmall.fredoka(),
        bodyLarge = bodyLarge.fredoka(), bodyMedium = bodyMedium.fredoka(), bodySmall = bodySmall.fredoka(),
        labelLarge = labelLarge.fredoka(), labelMedium = labelMedium.fredoka(), labelSmall = labelSmall.fredoka(),
    )
}

@Composable
fun WonderBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = FredokaTypography,
        colorScheme = darkColorScheme(
            primary = Tangerine,
            onPrimary = Ink,
            secondary = Coral,
            background = Lagoon,
            surface = Reef,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
        content = content,
    )
}

/** A child's colour from the server ("#RRGGBB"), or tangerine if it's missing. */
fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Tangerine)

/** Text that reads well on [this] colour: dark on light colours like yellow, white on the rest. */
fun Color.onColor(): Color = if (luminance() > 0.42f) Ink else Color.White

/** The "Wonder Box" logo. [boxColor] is for "Box", which would vanish on a pink screen. */
@Composable
fun Logo(boxColor: Color = Coral) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Wonder",
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.background(Tangerine, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Text("Box", color = boxColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * A child's face, filling its parent: their own picture, else their avatar's bundled picture,
 * else the avatar or their initial as text. The caller gives it its shape and colour.
 */
@Composable
fun ChildFace(child: Child, serverUrl: String, textSize: TextUnit, shape: Shape, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val own = child.picture.isNotBlank()
    val model = if (own) serverUrl.trimEnd('/') + child.picture else Avatars.uri(context, child.avatar)
    var failed by remember(model) { mutableStateOf(false) }
    Box(modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = child.name,
                contentScale = if (own) ContentScale.Crop else ContentScale.Fit,
                onError = { failed = true },
                modifier = if (own) Modifier.fillMaxSize() else Modifier.fillMaxSize(0.78f),
            )
        } else {
            Text(
                child.avatar.ifBlank { child.name.take(1).uppercase() },
                fontSize = textSize,
                color = Ink,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * One drifting shape in [PlayBackground]. Positions are fractions of the screen; [laps] is how
 * many times it crosses the screen per loop, a whole number so the loop has no jump.
 */
private class Floater(val x: Float, val y: Float, val size: Float, val laps: Int, val kind: Int, val spin: Float)

private val floaters = listOf(
    Floater(0.06f, 0.15f, 46f, 2, 0, 0.0f), Floater(0.18f, 0.72f, 26f, 4, 1, 0.3f),
    Floater(0.30f, 0.40f, 70f, 2, 2, 0.0f), Floater(0.42f, 0.90f, 22f, 5, 0, 0.0f),
    Floater(0.55f, 0.25f, 34f, 3, 1, 0.6f), Floater(0.66f, 0.62f, 54f, 2, 0, 0.0f),
    Floater(0.78f, 0.10f, 28f, 4, 2, 0.0f), Floater(0.88f, 0.48f, 40f, 3, 1, 0.9f),
    Floater(0.95f, 0.85f, 60f, 2, 0, 0.0f), Floater(0.12f, 0.95f, 36f, 3, 2, 0.0f),
    Floater(0.48f, 0.05f, 20f, 5, 1, 0.2f), Floater(0.72f, 0.88f, 24f, 4, 1, 0.5f),
)

/**
 * A bright backdrop in [color] (a child's own colour, so they can tell whose screen it is),
 * with soft bubbles, rings and stars drifting slowly upwards, or [season]'s decorations
 * (Seasons.kt) instead. Only the drawing redraws each frame, not the screen on top.
 */
@Composable
fun PlayBackground(color: Color, modifier: Modifier = Modifier, season: Season? = null, content: @Composable BoxScope.() -> Unit) {
    val time by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val light = lerp(color, Color.White, 0.18f)
    val dark = lerp(color, Ink, 0.22f)
    val ink = if (color.onColor() == Ink) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f)
    val pictures = rememberSeasonPictures(season)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(light, color, dark)))) {
        Canvas(Modifier.fillMaxSize()) {
            if (season != null) {
                drawSeason(season, pictures, time)
                return@Canvas
            }
            for (f in floaters) {
                // Up the screen and round again (1.2 screens, so it's off screen when it wraps),
                // swaying a little from side to side.
                val travel = (f.y - time * f.laps * 1.2f) % 1.2f
                val y = (if (travel < 0) travel + 1.2f else travel) - 0.1f
                val sway = sin((time * f.laps * 4f + f.x) * 2f * PI.toFloat()) * 0.012f
                val center = Offset((f.x + sway) * size.width, y * size.height)
                val r = f.size.dp.toPx()
                when (f.kind) {
                    0 -> drawCircle(ink, r, center)
                    1 -> drawStar(ink, center, r, time * 360f * f.laps + f.spin * 72f)
                    else -> drawCircle(ink, r, center, style = Stroke(width = r * 0.22f))
                }
            }
        }
        content()
    }
}

private fun DrawScope.drawStar(color: Color, center: Offset, radius: Float, degrees: Float) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val a = (degrees + i * 36f - 90f) * PI.toFloat() / 180f
        val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

/** requestFocus() fails if the target isn't laid out yet; retry for a few frames. */
suspend fun FocusRequester.requestFocusWhenReady() {
    repeat(10) {
        if (runCatching { requestFocus() }.isSuccess) return
        withFrameNanos { }
    }
}

/** One pixel per QR module; draw it with FilterQuality.None so the modules stay sharp when scaled up. */
fun qrCode(text: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
