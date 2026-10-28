package com.njdyson.wonderbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.njdyson.wonderbox.data.Photo

/** "Who's watching?": one big face per child. The grown-ups' button sits quietly in the corner. */
@Composable
fun ProfileScreen(app: AppState, lastChildId: String?, onPick: (Child) -> Unit, onPhotos: () -> Unit, onGrownUps: () -> Unit) {
    val children = app.library.children
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Night)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo()
            Text(
                if (children.isEmpty()) "Nobody here yet" else "Who's watching?",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 40.dp, bottom = 44.dp),
            )
            if (children.isEmpty()) {
                Text(
                    "Ask a grown-up to add you in the Wonder Box phone app.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    val focusIndex = children.indexOfFirst { it.id == lastChildId }.coerceAtLeast(0)
                    children.forEachIndexed { i, child ->
                        Face(
                            child,
                            onClick = { onPick(child) },
                            onFocus = { app.speak(child.name) },
                            modifier = if (i == focusIndex) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    // Family photos are for everyone, so they sit beside the faces rather than under one child.
                    app.photos.firstOrNull()?.let { newest ->
                        PhotosButton(app, newest, onClick = onPhotos, onFocus = { app.speak("Photos") })
                    }
                }
            }
        }
        GrownUpsButton(onGrownUps, Modifier.align(Alignment.TopEnd).padding(24.dp).then(if (children.isEmpty()) Modifier.focusRequester(first) else Modifier))
    }
    LaunchedEffect(children.size) { first.requestFocusWhenReady() }
}

@Composable
private fun Face(child: Child, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val color = parseColor(child.color)
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(170.dp).onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = CircleShape)),
        ) {
            Text(
                child.avatar.ifBlank { child.name.take(1).uppercase() },
                fontSize = 88.sp,
                color = Deep,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            child.name,
            color = if (focused) Sunshine else Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(200.dp),
        )
    }
}

/** The way into the family photos: a round picture of the newest one, sized like a face. */
@Composable
private fun PhotosButton(app: AppState, newest: Photo, onClick: () -> Unit, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(170.dp).onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Sky, focusedContainerColor = Sky),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(5.dp, Sky), shape = CircleShape),
                focusedBorder = Border(BorderStroke(7.dp, Color.White), shape = CircleShape),
            ),
        ) {
            PhotoThumb(app, newest, Modifier.fillMaxSize())
            Text(
                "📷",
                fontSize = 34.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp),
            )
        }
        Text(
            "Photos",
            color = if (focused) Sunshine else Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(200.dp),
        )
    }
}

/** A small, plain button: interesting to nobody under seven. */
@Composable
private fun GrownUpsButton(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.5f),
            focusedContainerColor = Color.White,
            focusedContentColor = Deep,
        ),
    ) {
        Text("⚙ Grown-ups", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}
