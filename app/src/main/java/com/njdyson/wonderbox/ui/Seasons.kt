package com.njdyson.wonderbox.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.njdyson.wonderbox.data.Avatars
import com.njdyson.wonderbox.data.Child
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorations for the time of year on the menus' [PlayBackground]: ghosts and bats at Halloween,
 * snow at Christmas, and so on. Each child's phone app setting is "auto" (by the date), "off",
 * or one season's [key] to keep it on. Pictures are Fluent Emoji in assets/season
 * (tools/make_season_pictures.py); [Music] plays each season's own tune.
 */
enum class Season(
    val key: String,
    /** What drifts across the screen, in turn: up, down or across. */
    val drifters: List<Drifter>,
    /** Pictures sitting still (rocking a little) around the edges. */
    val pieces: List<Piece>,
) {
    Halloween(
        "halloween",
        listOf(Drifter("👻", Motion.Up), Drifter("🦇", Motion.Across), Drifter("🍬", Motion.Up), Drifter("🦇", Motion.Across), Drifter("👻", Motion.Up), Drifter("🍭", Motion.Up)),
        listOf(
            Piece("🕸", 0.04f, 0.07f, 230f, 0f), Piece("🕸", 0.97f, 0.93f, 190f, 90f),
            Piece("🎃", 0.04f, 0.93f, 120f, -8f), Piece("🎃", 0.115f, 0.96f, 80f, 10f),
        ),
    ),
    Bonfire(
        "bonfire",
        listOf(Drifter("✨", Motion.Up), Drifter("⭐", Motion.Up), Drifter("✨", Motion.Up)),
        listOf(Piece("🔥", 0.05f, 0.93f, 120f, 0f), Piece("🔥", 0.95f, 0.94f, 90f, 0f)),
    ),
    Christmas(
        "christmas",
        listOf(Drifter("❄", Motion.Down), Drifter("⭐", Motion.Down), Drifter("❄", Motion.Down), Drifter("🔔", Motion.Down)),
        listOf(
            Piece("🎄", 0.05f, 0.9f, 150f, 0f), Piece("🎁", 0.13f, 0.96f, 80f, -6f),
            Piece("⛄", 0.95f, 0.91f, 130f, 4f),
        ),
    ),
    Valentine(
        "valentine",
        listOf(Drifter("💖", Motion.Up), Drifter("💕", Motion.Up), Drifter("💗", Motion.Up)),
        listOf(Piece("🌹", 0.04f, 0.93f, 110f, -12f), Piece("🌹", 0.96f, 0.93f, 110f, 12f)),
    ),
    Easter(
        "easter",
        listOf(Drifter("🥚", Motion.Up), Drifter("🌼", Motion.Up), Drifter("🐥", Motion.Up), Drifter("🥚", Motion.Up)),
        listOf(
            Piece("🌷", 0.04f, 0.93f, 110f, -6f), Piece("🐣", 0.12f, 0.96f, 80f, 0f),
            Piece("🐰", 0.95f, 0.92f, 130f, 0f),
        ),
    );

    enum class Motion { Up, Down, Across }

    class Drifter(val emoji: String, val motion: Motion)

    /** A picture at [x], [y] (fractions of the screen, its middle), [size] dp across, turned [angle] degrees. */
    class Piece(val emoji: String, val x: Float, val y: Float, val size: Float, val angle: Float)

    companion object {
        const val AUTO = "auto"
        const val OFF = "off"

        /** The season [today] falls in, or null for none. */
        fun on(today: Calendar = Calendar.getInstance()): Season? {
            val month = today.get(Calendar.MONTH)
            val day = today.get(Calendar.DAY_OF_MONTH)
            val easter = easterSunday(today.get(Calendar.YEAR))
            val daysToEaster = ((easter.timeInMillis - midnight(today).timeInMillis) / 86_400_000L).toInt()
            return when {
                month == Calendar.OCTOBER && day >= 17 || month == Calendar.NOVEMBER && day == 1 -> Halloween
                month == Calendar.NOVEMBER && day in 2..6 -> Bonfire
                month == Calendar.DECEMBER || month == Calendar.JANUARY && day <= 5 -> Christmas
                month == Calendar.FEBRUARY && day in 10..14 -> Valentine
                daysToEaster in -1..13 -> Easter
                else -> null
            }
        }

        /** A child's setting ("auto", "off" or a season's key) on [today]. */
        fun of(setting: String, today: Calendar = Calendar.getInstance()): Season? = when (setting) {
            OFF -> null
            AUTO, "" -> on(today)
            else -> entries.firstOrNull { it.key == setting } ?: on(today)
        }

        /** For the faces screen, before anyone's picked: what all the [children] share, or the date's unless they've all turned it off. */
        fun forFamily(children: List<Child>, today: Calendar = Calendar.getInstance()): Season? {
            val settings = children.map { it.games.season }.distinct()
            return when {
                settings.isEmpty() -> on(today)
                settings.size == 1 -> of(settings[0], today)
                settings.all { it == OFF } -> null
                else -> on(today)
            }
        }

        private fun midnight(day: Calendar) = Calendar.getInstance().apply {
            clear()
            set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
        }

        /** Easter Sunday in [year] (the anonymous Gregorian algorithm). */
        private fun easterSunday(year: Int): Calendar {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = (h + l - 7 * m + 114) % 31 + 1
            return Calendar.getInstance().apply {
                clear()
                set(year, month - 1, day)
            }
        }
    }
}

/** The pictures [season] needs, loaded once from assets/season (none if a file's missing). */
@Composable
fun rememberSeasonPictures(season: Season?): Map<String, ImageBitmap> {
    val context = LocalContext.current
    return remember(season) { if (season == null) emptyMap() else loadPictures(context, season) }
}

private fun loadPictures(context: Context, season: Season): Map<String, ImageBitmap> {
    // The Halloween spider hangs on its own thread (drawSeason).
    val emoji = (season.drifters.map { it.emoji } + season.pieces.map { it.emoji } + "🕷").distinct()
    return emoji.mapNotNull { e ->
        val name = Avatars.fileName(e) ?: return@mapNotNull null
        runCatching {
            context.assets.open("season/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()?.let { e to it }
    }.toMap()
}

/** Where each drifting picture starts and how fast it goes; [laps] are whole so the loop has no jump. */
private class Drift(val x: Float, val y: Float, val size: Float, val laps: Int)

private val paths = listOf(
    Drift(0.07f, 0.20f, 64f, 2), Drift(0.24f, 0.70f, 48f, 3), Drift(0.40f, 0.35f, 56f, 2),
    Drift(0.58f, 0.85f, 44f, 3), Drift(0.74f, 0.15f, 60f, 2), Drift(0.90f, 0.55f, 50f, 3),
    Drift(0.33f, 0.02f, 40f, 4), Drift(0.82f, 0.95f, 42f, 4),
)

/** Snowflakes for Christmas: x, starting height, size in dp, laps. */
private val snow = List(36) { i ->
    val x = ((i * 37) % 100) / 100f
    val y = ((i * 53) % 100) / 100f
    floatArrayOf(x, y, 2.5f + (i * 7 % 5), (3 + i % 4).toFloat())
}

/** Bonfire night's fireworks: where each bursts, its colour, and when in its turn. */
private val bursts = listOf(
    Triple(Offset(0.2f, 0.3f), Color(0xFFFFD23F), 0f), Triple(Offset(0.75f, 0.22f), Color(0xFFFF5C7A), 0.33f),
    Triple(Offset(0.5f, 0.15f), Color(0xFFC3E84B), 0.66f), Triple(Offset(0.88f, 0.45f), Color(0xFF7FDBFF), 0.5f),
    Triple(Offset(0.1f, 0.6f), Color(0xFFFFFFFF), 0.15f),
)

/**
 * Draws [season]'s decorations at [time] (0 to 1, once round every two minutes) with its
 * [pictures]: drifting ones, ones round the edges, and its own extras (a spider on a thread,
 * snow, fireworks).
 */
fun DrawScope.drawSeason(season: Season, pictures: Map<String, ImageBitmap>, time: Float) {
    val turn = 2f * PI.toFloat()
    when (season) {
        Season.Christmas -> for (f in snow) {
            val travel = (f[1] + time * f[3] * 1.1f) % 1.1f - 0.05f
            val sway = sin((time * f[3] * 6f + f[0]) * turn) * 0.01f
            drawCircle(Color.White.copy(alpha = 0.75f), f[2].dp.toPx(), Offset((f[0] + sway) * size.width, travel * size.height))
        }
        Season.Bonfire -> for ((at, color, start) in bursts) {
            // Each bursts 8 times a loop: spreading out and fading over the first half of its turn.
            val phase = (time * 8f + start) % 1f
            if (phase < 0.5f) {
                val grow = phase / 0.5f
                val radius = (40f + 80f * grow).dp.toPx()
                val center = Offset(at.x * size.width, at.y * size.height)
                for (ray in 0 until 12) {
                    val a = ray * turn / 12
                    drawCircle(color.copy(alpha = (1f - grow) * 0.9f), 4.dp.toPx(), center + Offset(cos(a), sin(a)) * radius)
                }
            }
        }
        Season.Halloween -> {
            // A spider letting itself down on its thread, and back up.
            val x = 0.66f * size.width
            val drop = (0.18f + 0.1f * sin(time * 12f * turn)) * size.height
            drawLine(Color.White.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, drop), strokeWidth = 1.5f.dp.toPx())
            pictures["🕷"]?.let { drawPicture(it, Offset(x, drop + 18.dp.toPx()), 52.dp.toPx(), 0f) }
        }
        else -> Unit
    }
    season.drifters.forEachIndexed { i, d ->
        val p = paths[i % paths.size]
        val picture = pictures[d.emoji] ?: return@forEachIndexed
        val laps = p.laps
        val wobble = sin((time * laps * 5f + p.x) * turn)
        val center = when (d.motion) {
            Season.Motion.Up -> {
                val travel = (p.y - time * laps * 1.3f).mod(1.3f) - 0.15f
                Offset((p.x + wobble * 0.02f) * size.width, travel * size.height)
            }
            Season.Motion.Down -> {
                val travel = (p.y + time * laps * 1.3f).mod(1.3f) - 0.15f
                Offset((p.x + wobble * 0.02f) * size.width, travel * size.height)
            }
            Season.Motion.Across -> {
                // Flapping across, swooping up and down, off one side and in at the other.
                val travel = (p.x + time * laps * 1.3f).mod(1.3f) - 0.15f
                Offset(travel * size.width, (p.y + wobble * 0.05f) * size.height)
            }
        }
        // Bats are small pictures: bigger so they show.
        val px = p.size.dp.toPx() * if (d.motion == Season.Motion.Across) 1.5f else 1f
        if (d.motion == Season.Motion.Across) {
            val flap = 0.75f + 0.25f * sin(time * 240f * turn)
            scale(1f, flap, center) { drawPicture(picture, center, px, 0f) }
        } else {
            drawPicture(picture, center, px, wobble * 10f)
        }
    }
    for (piece in season.pieces) {
        val picture = pictures[piece.emoji] ?: continue
        val rock = sin((time * 6f + piece.x) * turn) * 3f
        drawPicture(picture, Offset(piece.x * size.width, piece.y * size.height), piece.size.dp.toPx(), piece.angle + rock)
    }
}

private fun DrawScope.drawPicture(picture: ImageBitmap, center: Offset, size: Float, degrees: Float) {
    val side = size.toInt()
    rotate(degrees, center) {
        drawImage(
            picture,
            dstOffset = IntOffset((center.x - size / 2).toInt(), (center.y - size / 2).toInt()),
            dstSize = IntSize(side, side),
        )
    }
}
