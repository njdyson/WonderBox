package com.njdyson.wonderbox.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Pairing
import com.njdyson.wonderbox.update.UpdateButton
import com.njdyson.wonderbox.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Shown until the TV is linked to the parent's account: a code and a QR code that opens the
 * phone app ready to approve it. Check for update is here too, so a TV that can't link yet
 * (e.g. the server isn't set up) can still update itself.
 */
@Composable
fun LinkScreen(app: AppState, resumeCount: Int) {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Getting a code…") }
    val updateFocus = remember { FocusRequester() }
    val deviceName = remember {
        // The name set in Fire TV / Android TV settings, e.g. "Nick's Fire TV Stick".
        // ("device_name" is Settings.Global.DEVICE_NAME, which needs API 25 as a constant.)
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    LaunchedEffect(Unit) { updateFocus.requestFocusWhenReady() }
    LaunchedEffect(Unit) {
        while (true) {
            val started = try {
                withContext(Dispatchers.IO) { Pairing.start(app.serverUrl, deviceName) }
            } catch (e: Exception) {
                message = "Couldn't reach ${app.serverUrl.removePrefix("https://")} (${e.message}). Trying again…"
                code = null
                delay(10_000)
                continue
            }
            code = started.code
            message = "Waiting for you to approve it on your phone…"
            // Poll until approved, or the code expires and a new one is fetched.
            while (true) {
                delay(3_000)
                val poll = runCatching { withContext(Dispatchers.IO) { Pairing.poll(app.serverUrl, started) } }.getOrNull() ?: continue
                if (poll.status == "expired") break
                if (poll.status == "approved" && poll.deviceKey != null) {
                    message = "Linked! Loading…"
                    app.completeLink(poll.deviceKey)
                    return@LaunchedEffect
                }
            }
        }
    }

    Row(
        Modifier.fillMaxSize().background(Night).padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val current = code
        if (current != null) {
            Image(
                qrCode(Pairing.approveUrl(app.serverUrl, current)),
                contentDescription = "QR code to link this TV",
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(260.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Logo()
            Text("Grown-ups: link this TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (current != null) {
                Text("1. Scan the code with your phone, or open ${app.serverUrl.removePrefix("https://")}", color = Color.White, fontSize = 16.sp)
                Text("2. Sign in, then choose Link this TV.", color = Color.White, fontSize = 16.sp)
                Text(current.chunked(3).joinToString(" "), color = Sunshine, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            }
            Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
            Column(Modifier.focusRequester(updateFocus)) { UpdateButton(resumeCount) }
            Text("Build ${Updater.currentVersion} (${Updater.currentSha})", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}
