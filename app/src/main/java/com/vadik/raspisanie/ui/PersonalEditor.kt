package com.vadik.raspisanie.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vadik.raspisanie.data.PersonalEvent
import com.vadik.raspisanie.data.timeKey
import java.time.LocalDate

private val DAY_SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

private fun dateText(d: LocalDate): String {
    val today = LocalDate.now()
    val word = when (d) {
        today -> "сегодня, "
        today.plusDays(1) -> "завтра, "
        else -> ""
    }
    return word + DAY_SHORT[d.dayOfWeek.value - 1] + " " + "%02d.%02d".format(d.dayOfMonth, d.monthValue)
}

private fun hm(m: Int) = "%d:%02d".format((m / 60).coerceIn(0, 23), m % 60)

/** Как повторяется: для подписи в списке. */
fun repeatText(e: PersonalEvent): String = when (e.repeat) {
    "daily" -> "каждый день"
    "weekly" -> "каждую неделю, " + DAY_SHORT[e.date.dayOfWeek.value - 1]
    else -> dateText(e.date)
}

/** Окно «Своё дело»: что, когда, где, повтор. */
@Composable
fun PersonalEditorDialog(initial: PersonalDraft, vm: MainViewModel) {
    val ctx = LocalContext.current
    var d by remember(initial) { mutableStateOf(initial) }
    val valid = d.title.isNotBlank() && timeKey(d.end) > timeKey(d.start)

    fun pickTime(current: String, onPick: (String) -> Unit) {
        val m = timeKey(current).takeIf { it < 24 * 60 } ?: (10 * 60)
        TimePickerDialog(ctx, { _, h, min -> onPick(hm(h * 60 + min)) }, m / 60, m % 60, true).show()
    }

    AlertDialog(
        onDismissRequest = { vm.closePersonalEditor() },
        title = { Text(if (d.id == null) "Своё дело" else "Изменить дело") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = d.title,
                    onValueChange = { d = d.copy(title = it) },
                    label = { Text("Что") },
                    placeholder = { Text("Например: английский, спортзал, встреча") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            ctx,
                            { _, y, mo, day -> d = d.copy(date = LocalDate.of(y, mo + 1, day)) },
                            d.date.year, d.date.monthValue - 1, d.date.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📅  " + dateText(d.date)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(d.start) { t ->
                        // конец сдвигаем вместе с началом, сохраняя длительность
                        val len = (timeKey(d.end) - timeKey(d.start)).coerceAtLeast(30)
                        d = d.copy(start = t, end = hm((timeKey(t) + len).coerceAtMost(23 * 60 + 59)))
                    } }, modifier = Modifier.weight(1f)) { Text("с ${d.start}") }
                    OutlinedButton(onClick = { pickTime(d.end) { t -> d = d.copy(end = t) } }, modifier = Modifier.weight(1f)) {
                        Text("до ${d.end}")
                    }
                }
                if (timeKey(d.end) <= timeKey(d.start)) {
                    Text("Конец должен быть позже начала", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = d.place,
                    onValueChange = { d = d.copy(place = it) },
                    label = { Text("Где (необязательно)") },
                    placeholder = { Text("Аудитория или место") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = d.note,
                    onValueChange = { d = d.copy(note = it) },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
                Text("Повтор", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                Pills(
                    listOf("none" to "Один раз", "weekly" to "Каждую неделю", "daily" to "Каждый день"),
                    d.repeat,
                ) { v -> d = d.copy(repeat = v) }
                Text(
                    "Дело появится в расписании рядом с парами, в виджетах и в напоминании перед началом.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                d.id?.let { id ->
                    Spacer(Modifier.height(2.dp))
                    TextButton(onClick = { vm.deletePersonal(id) }) {
                        Text(if (d.repeat == "none") "Удалить" else "Удалить (все повторы)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { vm.savePersonal(d) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { vm.closePersonalEditor() }) { Text("Отмена") } },
    )
}
