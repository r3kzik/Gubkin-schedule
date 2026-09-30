package com.vadik.raspisanie.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Картинка из файла в фоне; [sample] — во сколько раз уменьшить (для миниатюр). */
@Composable
private fun rememberPhoto(file: File, sample: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file.path, sample) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bmp
}

/** Раздел «Заметки и фото» в карточке пары. */
@Composable
fun LessonNotesSection(key: String, state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val note = state.notes[key]
    var text by remember(key) { mutableStateOf(note?.text.orEmpty()) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }

    // автосохранение текста через полсекунды после ввода
    LaunchedEffect(key, text) {
        if (text == (state.notes[key]?.text ?: "")) return@LaunchedEffect
        delay(500)
        vm.saveNoteText(key, text)
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null) vm.attachTakenPhoto(key, f) else f?.delete()
        pendingPhoto = null
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.attachGalleryPhoto(key, it) }
    }

    Text("Заметки и фото", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Например: принести калькулятор, тема — интегралы") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 8,
    )
    val photos = note?.photos.orEmpty()
    if (photos.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { name ->
                val img = rememberPhoto(vm.photoFile(name), 4)
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewing = name },
                    contentAlignment = Alignment.Center,
                ) {
                    if (img != null) Image(img, contentDescription = "Фото к паре", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("…")
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPill("📷 Сфотографировать", selected = false) {
            runCatching {
                val f = vm.newPhotoFile()
                pendingPhoto = f
                takePhoto.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
            }
        }
        GlassPill("🖼 Из галереи", selected = false) {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // ---- просмотр фото на весь экран
    viewing?.let { name ->
        val file = vm.photoFile(name)
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val img = rememberPhoto(file, 1)
                    if (img != null) Image(img, contentDescription = "Фото к паре", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                            val send = Intent(Intent.ACTION_SEND).setType("image/jpeg")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            send.clipData = android.content.ClipData.newRawUri("", uri)
                            ctx.startActivity(Intent.createChooser(send, "Поделиться фото"))
                        }
                    }) { Text("Поделиться", color = Color.White) }
                    TextButton(onClick = { vm.deletePhoto(key, name); viewing = null }) { Text("Удалить", color = Color(0xFFFF8A80)) }
                    TextButton(onClick = { viewing = null }) { Text("Закрыть", color = Color.White) }
                }
            }
        }
    }
}
