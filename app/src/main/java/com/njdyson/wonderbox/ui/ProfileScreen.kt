package com.njdyson.wonderbox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Child

/**
 * "Who's watching?": one big bobbing face per child. The whole screen turns the colour of the
 * focused child, so it already looks like theirs before they press OK. The grown-ups' button
 * sits quietly in the corner.
 */
@Composable
fun ProfileScreen(app: AppState, lastChildId: String?, onPick: (Child) -> Unit, onGrownUps: () -> Unit) {
    val children = app.library.children
    val first = remember { FocusRequester() }
    val focusIndex = children.indexOfFirst { it.id == lastChildId }.coerceAtLeast(0)
    var focusedId by remember { mutableStateOf(children.getOrNull(focusIndex)?.id) }
    val target = children.firstOrNull { it.id == focusedId }?.let { parseColor(it.color) } ?: Reef
    val color by animateColorAsState(target, tween(450), label = "background")
    val ink = color.onColor()

    PlayBackground(color) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo(boxColor = if (ink == Ink) Ink else Color.White)
            Text(
                if (children.isEmpty()) "Nobody here yet" else "Who's watching?",
                color = ink,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp, bottom = 48.dp),
            )
            if (children.isEmpty()) {
                Text(
                    "Ask a grown-up to add you in the Wonder Box phone app.",
                    color = ink.copy(alpha = 0.85f),
                    fontSize = 22.sp,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    children.forEachIndexed { i, child ->
                        Face(
                            child,
                            serverUrl = app.serverUrl,
                            index = i,
                            onClick = { onPick(child) },
                            onFocus = {
                                focusedId = child.id
                                app.speak(child.name)
                            },
                            modifier = if (i == focusIndex) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
        GrownUpsButton(
            onGrownUps,
            ink,
            Modifier.align(Alignment.TopEnd).padding(24.dp).then(if (children.isEmpty()) Modifier.focusRequester(first) else Modifier),
        )
    }
    LaunchedEffect(children.size) { first.requestFocusWhenReady() }
}

@Composable
private fun Face(child: Child, serverUrl: String, index: Int, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val color = parseColor(child.color)
    var focused by remember { mutableStateOf(false) }
    // Each face bobs gently, out of step with the others; the focused one bounces higher.
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400 + index * 230, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.graphicsLayer { translationY = bob * (if (focused) 10f else 5f) * density },
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(190.dp).onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            // A lighter ring of their colour always, white when focused, so the face stands out
            // on a screen of the same colour.
            colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(5.dp, Color.White.copy(alpha = 0.55f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(8.dp, Color.White), shape = CircleShape),
            ),
        ) {
            ChildFace(child, serverUrl, textSize = 96.sp, shape = CircleShape)
        }
        Text(
            child.name,
            color = if (focused) Ink else Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .background(if (focused) Color.White else Ink.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 4.dp),
        )
    }
}

/** A small, plain button: interesting to nobody under seven. */
@Composable
private fun GrownUpsButton(onClick: () -> Unit, ink: Color, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ink.copy(alpha = 0.08f),
            contentColor = ink.copy(alpha = 0.55f),
            focusedContainerColor = Color.White,
            focusedContentColor = Ink,
        ),
    ) {
        Text("⚙ Grown-ups", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}
