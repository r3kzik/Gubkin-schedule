package com.vadik.raspisanie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FULL_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val D_M: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val NOTICED: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM в HH:mm")

/** Подробности пары: полная информация и что изменилось. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(detail: LessonDetail, prefs: Prefs, onBack: () -> Unit) {
    val l = detail.lesson
    var showRaw by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пара") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(l.subject, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                l.kind?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                l.subgroup?.let { SubgroupBadge(it, prefs.isOtherSubgroup(l)) }
            }

            if (l.cancelled || l.moved) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        listOfNotNull(
                            if (l.cancelled) "Пара отменена" else null,
                            if (l.moved) "Пара перенесена" else null,
                        ).joinToString(" · "),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val date = detail.date
            InfoLine("Когда", "${DAY_FULL_NAMES[date.dayOfWeek.value - 1]}, ${date.format(D_M)} · " +
                if (l.end.isNotBlank()) "${l.start}–${l.end}" else l.start)
            InfoLine("Аудитория", l.room ?: "не указана")
            InfoLine("Преподаватель", l.teacherFull ?: l.teacher ?: "не указан")
            if (l.subgroup != null) InfoLine("Подгруппа", "${l.subgroup}-я подгруппа")

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Изменения", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val nothing = l.changeLines.isEmpty() && detail.history.isEmpty()
            if (l.changeLines.isNotEmpty()) {
                Text("По данным сайта:", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                l.changeLines.forEach { Bullet(it) }
                Spacer(Modifier.height(8.dp))
            }
            if (detail.history.isNotEmpty()) {
                Text("Замечено приложением:", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.history.forEach { c ->
                    val at = if (c.noticedAt > 0) {
                        Instant.ofEpochMilli(c.noticedAt).atZone(ZoneId.systemDefault()).format(NOTICED) + " — "
                    } else ""
                    Bullet(at + c.text)
                }
            }
            if (nothing) {
                Text(
                    if (l.changed) {
                        "Сайт отметил пару как изменённую, но не уточнил, что именно. " +
                            "Подробности могут быть в разделе «Данные с сайта» ниже. Если там что-то есть, " +
                            "отправьте данные через Настройки → «Отправить данные расписания», и я научу приложение это показывать."
                    } else "Изменений нет.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (l.raw != null) {
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Скрыть данные с сайта" else "Показать данные с сайта")
                }
                if (showRaw) {
                    SelectionContainer {
                        Text(
                            l.raw,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
