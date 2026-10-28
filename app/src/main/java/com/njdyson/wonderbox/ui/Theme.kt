package com.njdyson.wonderbox.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

val Night = Color(0xFF24164F)
val Deep = Color(0xFF1A1040)
val Plum = Color(0xFF352468)
val Sunshine = Color(0xFFFFD23F)
val Bubblegum = Color(0xFFFF6B9A)
val Sky = Color(0xFF3EC1D3)

@Composable
fun WonderBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sunshine,
            onPrimary = Deep,
            secondary = Bubblegum,
            background = Night,
            surface = Plum,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
        content = content,
    )
}

/** A child's colour from the server ("#RRGGBB"), or yellow if it's missing. */
fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Sunshine)

/** The "Wonder Box" logo. */
@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Wonder",
            color = Deep,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.background(Sunshine, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Text("Box", color = Bubblegum, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
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
