package com.vadik.raspisanie.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vadik.raspisanie.data.timeKey
import com.vadik.raspisanie.work.CalendarSync
import java.time.LocalDate

/** Вкладка «Сервис»: резервная копия, экспорт в календарь, утренняя сводка. */
@Composable
fun ToolsScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickCalendar by remember { mutableStateOf<List<CalendarSync.Calendar>?>(null) }
    var pickAuto by remember { mutableStateOf(true) }

    // ---- системные окна: сохранить / открыть файл, разрешение на календарь
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { confirmRestore = it }
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) {
            val list = vm.calendars()
            if (list.isEmpty()) Toast.makeText(ctx, "На телефоне нет календаря, в который можно записывать", Toast.LENGTH_LONG).show()
            else pickCalendar = list
        } else {
            Toast.makeText(ctx, "Без доступа к календарю можно отправить файл .ics", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Сервис", onBack = null)
        Column(
            Modifier
                .fillMaxSize()
                .fadeEdges(40f, 70f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---------------- итог последнего действия
            if (state.toolsBusy || state.toolsMessage != null) {
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), shape = skinShape(22), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.toolsBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(12.dp))
                            Text("Минутку…")
                        } else {
                            Text(state.toolsMessage.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            GlassPill("OK", selected = false) { vm.dismissToolsMessage() }
                        }
                    }
                }
            }

            // ---------------- резервная копия
            SectionTitle("Резервная копия")
            SettingsCard {
                Hint("В копию входят группа, настройки и тема, домашние задания, свои дела, заметки и фото к парам. Расписание не копируется — оно скачается заново.")
                NavRow("Сохранить копию", "Файл .zip — в «Загрузки», на Google Диск или куда удобно") {
                    saveBackup.launch("mygub-backup-${LocalDate.now()}.zip")
                }
                Divider()
                NavRow("Восстановить из копии", "Например, после переустановки или на новом телефоне") {
                    openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
            }

            // ---------------- календарь
            SectionTitle("Календарь")
            SettingsCard {
                Hint("Пары и свои дела появятся в обычном календаре телефона — рядом с остальными планами. Экспортируются загруженные недели, свои дела — на 8 недель вперёд.")
                NavRow(
                    "Добавить в календарь телефона",
                    if (prefs.calendarId >= 0) "Уже добавлено — нажмите, чтобы обновить" else "Google Календарь, Samsung, Xiaomi и другие",
                ) {
                    if (CalendarSync.hasPermission(ctx)) {
                        val list = vm.calendars()
                        if (list.isEmpty()) Toast.makeText(ctx, "На телефоне нет календаря, в который можно записывать", Toast.LENGTH_LONG).show()
                        else pickCalendar = list
                    } else {
                        askCalendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    }
                }
                if (prefs.calendarId >= 0) {
                    Divider()
                    SwitchRow(
                        "Обновлять автоматически",
                        "Замены и новые недели попадут в календарь сами",
                        prefs.calendarAuto,
                    ) { v -> vm.updatePrefs { it.copy(calendarAuto = v) } }
                    Divider()
                    NavRow("Убрать пары из календаря", "Удалит будущие события MyGub") { vm.clearCalendar() }
                }
                Divider()
                NavRow("Отправить файлом .ics", "Для импорта в любой календарь или на компьютер") {
                    val f = vm.icsFile()
                    if (f == null) {
                        Toast.makeText(ctx, "Сначала загрузите расписание", Toast.LENGTH_SHORT).show()
                    } else runCatching {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).setType("text/calendar")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        send.clipData = android.content.ClipData.newRawUri("", uri)
                        ctx.startActivity(Intent.createChooser(send, "Расписание в календарь"))
                    }
                }
            }

            // ---------------- утренняя сводка
            SectionTitle("Утренняя сводка")
            SettingsCard {
                SwitchRow(
                    "Утренняя сводка",
                    "Одно уведомление: сколько пар, где первая, что сдавать. В дни без пар и дел — не беспокоит",
                    prefs.morningEnabled,
                ) { v -> vm.updatePrefs { it.copy(morningEnabled = v) } }
                if (prefs.morningEnabled) {
                    Divider()
                    NavRow("Время", prefs.morningTime) {
                        val m = timeKey(prefs.morningTime).takeIf { it < 24 * 60 } ?: (7 * 60 + 30)
                        TimePickerDialog(ctx, { _, h, min ->
                            vm.updatePrefs { it.copy(morningTime = "%d:%02d".format(h, min)) }
                        }, m / 60, m % 60, true).show()
                    }
                }
                Divider()
                NavRow("Показать сводку сейчас", "Как она выглядит на сегодня") { vm.showMorningNow() }
            }
        }
    }

    // ---- подтверждение восстановления
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Восстановить из копии?") },
            text = { Text("Текущие настройки, ДЗ, свои дела и заметки заменятся данными из файла.") },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmRestore = null }) { Text("Восстановить") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Отмена") } },
        )
    }

    // ---- выбор календаря
    pickCalendar?.let { list ->
        AlertDialog(
            onDismissRequest = { pickCalendar = null },
            title = { Text("В какой календарь?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.exportToCalendar(c.id, pickAuto); pickCalendar = null }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(c.name, fontWeight = if (c.id == prefs.calendarId) FontWeight.Bold else FontWeight.Medium)
                            if (c.account.isNotBlank()) Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow("Обновлять автоматически", "Замены попадут в календарь сами", pickAuto) { pickAuto = it }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickCalendar = null }) { Text("Отмена") } },
        )
    }
}
