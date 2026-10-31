package com.njdyson.wonderbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Video
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Straight after picking a treat: a celebration with the treat they picked, so they know it's
 * theirs, then on by itself ([onDone]) once it's been said, or sooner with OK. [more] when they
 * still have another treat to pick.
 */
@Composable
fun TreatSavedScreen(app: AppState, child: Child, video: Video, more: Boolean, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    val pop = remember { Animatable(0.3f) }
    val wiggle = remember { Animatable(0f) }
    // Once only, whether OK, BACK or the timer gets there first.
    val finish = remember {
        var gone = false
        { if (!gone) { gone = true; onDone() } }
    }
    LaunchedEffect(Unit) {
        val said = CompletableDeferred<Unit>()
        app.say(
            "Yay, ${child.name}! ${video.title} is saved in your treats!" + if (more) " Now pick another one!" else "",
            onDone = { said.complete(Unit) },
        )
        // Long enough to see it even if the voice is quick (or missing), never stuck if it's slow.
        val started = System.currentTimeMillis()
        withTimeoutOrNull(MAX_SPEAKING_MS) { said.await() }
        delay(maxOf(AFTER_SPEAKING_MS, MIN_SHOWN_MS - (System.currentTimeMillis() - started)))
        finish()
    }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) {
        // The treat's picture wiggles like a sticker just stuck in a book.
        repeat(3) {
            wiggle.animateTo(4f, spring(stiffness = Spring.StiffnessMediumLow))
            wiggle.animateTo(-4f, spring(stiffness = Spring.StiffnessMediumLow))
        }
        wiggle.animateTo(0f)
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    BackHandler { finish() }

    ChildScreen(app, child, Category.Treats.title) {
        Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .background(Ink.copy(alpha = 0.88f), RoundedCornerShape(40.dp))
                    .padding(horizontal = 56.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "🎉 Yay, ${child.name}! 🎉",
                    color = Tangerine,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = wiggle.value; scaleX = pop.value; scaleY = pop.value }
                        .width(272.dp)
                        .height(153.dp),
                ) {
                    AsyncImage(
                        model = video.thumbnail.ifBlank { "https://i.ytimg.com/vi/${video.youtubeId}/hqdefault.jpg" },
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .border(6.dp, Color.White, RoundedCornerShape(22.dp)),
                    )
                    Text(
                        "⭐",
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Tangerine, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    video.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (more) "is in your treats! Now pick another ⭐" else "is saved in your treats ⭐",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 24.sp,
                )
                BigButton(if (more) "⭐ Pick another" else "Yay!", Modifier.padding(top = 4.dp, bottom = 8.dp).focusRequester(first), finish)
            }
        }
    }
}

private const val MAX_SPEAKING_MS = 9_000L
private const val AFTER_SPEAKING_MS = 1_500L
private const val MIN_SHOWN_MS = 4_000L
