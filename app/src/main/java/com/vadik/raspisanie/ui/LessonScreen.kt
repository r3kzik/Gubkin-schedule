package com.vadik.raspisanie.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FULL_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val D_M: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val NOTICED: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM в HH:mm")

/** Подробности пары: полная информация и что изменилось. */
@Composable
fun LessonScreen(detail: LessonDetail, state: UiState, vm: MainViewModel, onBack: () -> Unit) {
    val prefs = state.prefs
    val l = detail.lesson
    val cs = MaterialTheme.colorScheme
    val red = changedColor()
    var showRaw by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Пара", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .fadeEdges(40f, 70f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- заголовок
            DetailCard {
                Text(l.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    l.kind?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    l.subgroup?.let {
                        Spacer(Modifier.width(8.dp))
                        SubgroupBadge(it, prefs.isOtherSubgroup(l))
                    }
                }
                if (l.cancelled || l.moved || l.movedFrom != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        listOfNotNull(
                            if (l.cancelled) "Пара отменена" else null,
                            if (l.moved) (l.movedTo?.let { "Перенесена на $it" } ?: "Пара перенесена") else null,
                            l.movedFrom?.let { "Перенесено с $it" },
                        ).joinToString(" · "),
                        fontWeight = FontWeight.Bold,
                        color = red,
                    )
                }
            }

            // ---------------- основное
            DetailCard {
                val date = detail.date
                InfoLine(
                    "Когда",
                    "${DAY_FULL_NAMES[date.dayOfWeek.value - 1]}, ${date.format(D_M)} · " +
                        if (l.end.isNotBlank()) "${l.start}–${l.end}" else l.start,
                )
                InfoLine(
                    if (l.roomChanged) "Аудитория · замена" else "Аудитория",
                    l.room ?: "не указана",
                    highlight = if (l.roomChanged) red else null,
                    was = l.oldRoom,
                )
                Campus.locate(l.room)?.let { loc ->
                    Text(
                        "📍 ${loc.summary}" + (loc.note?.let { "\n$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    GlassPill("Показать на карте", selected = true) { vm.showOnMap(l.room) }
                    Spacer(Modifier.height(4.dp))
                }
                InfoLine(
                    if (l.teacherChanged) "Преподаватель · замена" else "Преподаватель",
                    l.teacherFull ?: l.teacher ?: "не указан",
                    highlight = if (l.teacherChanged) red else null,
                    was = l.oldTeacher,
                )
                // расписание преподавателя: где он ещё ведёт пары
                val teachers = remember(l.raw) {
                    com.vadik.raspisanie.data.TeacherParser.teachersFromRaw(l.raw)
                        .let { all -> if (l.teacherChanged) all.filter { t -> l.teacherFull?.contains(t.fullName) != false } else all }
                        .distinctBy { it.id }
                }
                teachers.forEach { t ->
                    Spacer(Modifier.height(4.dp))
                    GlassPill("Расписание: ${t.shortName}", selected = false) { vm.openTeacherFromLesson(t) }
                }
                if (teachers.isNotEmpty()) Spacer(Modifier.height(4.dp))
                if (l.subgroup != null) InfoLine("Подгруппа", "${l.subgroup}-я подгруппа")
                l.department?.let { InfoLine("Кафедра", it) }
                l.info?.let { InfoLine("Доп. информация", it) }
            }

            // ---------------- изменения
            DetailCard {
                Text("Изменения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (l.changeLines.isNotEmpty()) {
                    SmallCaption("По данным сайта")
                    l.changeLines.forEach { Bullet(it, red) }
                    Spacer(Modifier.height(8.dp))
                }
                if (detail.history.isNotEmpty()) {
                    SmallCaption("Замечено приложением")
                    detail.history.forEach { c ->
                        val at = if (c.noticedAt > 0) {
                            Instant.ofEpochMilli(c.noticedAt).atZone(ZoneId.systemDefault()).format(NOTICED) + " — "
                        } else ""
                        Bullet(at + c.text, cs.primary)
                    }
                }
                if (l.changeLines.isEmpty() && detail.history.isEmpty()) {
                    Text(
                        if (l.changed) "Сайт отметил пару как изменённую. Подробности — в данных с сайта ниже."
                        else "Изменений нет — всё по расписанию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            // ---------------- домашнее задание
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Домашнее задание",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    GlassPill("+ Добавить", selected = true) { vm.openHomeworkEditor(l.subject) }
                }
                val hw = state.homework.filter { it.subject == l.subject }
                    .sortedWith(compareBy({ it.done }, { it.due ?: java.time.LocalDate.MAX }))
                if (hw.isEmpty()) {
                    Text(
                        "Заданий по этому предмету нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    hw.forEach { h ->
                        HomeworkRow(h, onToggle = { vm.toggleHomework(h) }, onClick = { vm.openHomeworkEditor(h.subject, h) })
                    }
                }
            }

            // ---------------- сырые данные
            if (l.raw != null) {
                DetailCard {
                    Text(
                        if (showRaw) "Скрыть данные с сайта" else "Показать данные с сайта",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRaw = !showRaw }
                            .padding(vertical = 4.dp),
                    )
                    AnimatedVisibility(
                        visible = showRaw,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        SelectionContainer {
                            Text(
                                l.raw.orEmpty(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = skinShape(28)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
private fun SmallCaption(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String, highlight: Color? = null, was: String? = null) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = highlight ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = highlight ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlight != null) FontWeight.Bold else FontWeight.Medium,
        )
        if (was != null) {
            Text(
                "по расписанию: $was",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

@Composable
private fun Bullet(text: String, dot: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
