package com.vadik.raspisanie.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vadik.raspisanie.data.Updates

/** Ссылка на установку: сразу скачивается свежий APK основной версии. */
const val SHARE_APK_URL = "https://github.com/r3kzik/Gubkin-schedule/releases/latest/download/mygub.apk"

/** QR-код: чёрные модули на белом, с полем по краям. */
fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val px = IntArray(m.width * m.height)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        px[y * m.width + x] = if (m.get(x, y)) AColor.BLACK else AColor.WHITE
    }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

private fun shareLink(ctx: Context) {
    val text = "MyGub — удобное расписание Губкинского: замены, аудитории на карте, ДЗ и виджеты.\n" +
        "Скачать: $SHARE_APK_URL\nВсе версии: ${Updates.RELEASES_PAGE}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Поделиться MyGub").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Окно с QR-кодом: друг наводит камеру — и скачивает MyGub. */
@Composable
fun ShareAppDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val qr = remember { runCatching { qrBitmap(SHARE_APK_URL).asImageBitmap() }.getOrNull() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Поделиться MyGub") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Пусть друг наведёт камеру телефона на код — скачается установочный файл MyGub.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) Image(qr, contentDescription = "QR-код для установки MyGub", filterQuality = FilterQuality.None)
                    else Text("Не удалось создать код", color = Color.Black)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Android спросит разрешение на установку из браузера — это нормально, MyGub нет в Google Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = { shareLink(ctx) }) { Text("Отправить ссылку") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}
