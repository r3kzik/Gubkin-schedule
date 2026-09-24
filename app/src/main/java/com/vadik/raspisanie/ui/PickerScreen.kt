package com.vadik.raspisanie.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.Repository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(p: PickerState, canClose: Boolean, vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (p.faculty == null) "Выберите факультет" else "Выберите группу") },
                navigationIcon = {
                    if (p.faculty != null) {
                        IconButton(onClick = { vm.backToFaculties() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    } else if (canClose) {
                        IconButton(onClick = { vm.closePicker() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                p.loading -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    p.message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, textAlign = TextAlign.Center)
                    }
                }

                p.faculty == null && p.faculties.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        p.message ?: "Список факультетов ещё не загружен",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.retryPicker() }) { Text("Повторить") }
                    TextButton(onClick = { vm.openPicker() }) { Text("Выбрать группу вручную") }
                }

                p.faculty == null -> {
                    p.message?.let {
                        Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(p.faculties, key = { it.id }) { f ->
                            ListItem(
                                headlineContent = { Text(f.name) },
                                modifier = Modifier.clickable { vm.pickFaculty(f) },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск, например ${App.DEFAULT_GROUP}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                    val q = Repository.normalizeCode(query)
                    val filtered = p.groups.filter { Repository.normalizeCode(it.code).contains(q) }
                    if (filtered.isEmpty()) {
                        Text(
                            if (p.groups.isEmpty()) "На этом факультете нет групп с расписанием" else "Ничего не найдено",
                            Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { g ->
                            ListItem(
                                headlineContent = { Text(g.code) },
                                modifier = Modifier.clickable { vm.pickGroup(g) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CaptchaDialog(c: CaptchaState, vm: MainViewModel) {
    var code by remember(c.image) { mutableStateOf("") }
    val bitmap = remember(c.image) {
        c.image?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    AlertDialog(
        onDismissRequest = { vm.cancelCaptcha() },
        title = { Text("Сайт вуза просит капчу") },
        text = {
            Column {
                Text("Введите символы с картинки. Это подтверждение сайту, что вы человек; после этого расписание снова будет обновляться.")
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    when {
                        bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = "Капча",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clickable { vm.openCaptcha() },
                        )
                        c.loading -> CircularProgressIndicator()
                        else -> Text("Картинка не загрузилась")
                    }
                }
                TextButton(onClick = { vm.openCaptcha() }) { Text("Другая картинка") }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text("Код с картинки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                c.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank() && !c.loading,
                onClick = { vm.submitCaptcha(code) },
            ) { Text("Проверить") }
        },
        dismissButton = {
            TextButton(onClick = { vm.cancelCaptcha() }) { Text("Отмена") }
        },
    )
}
