package com.vadik.raspisanie.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Homework
import com.vadik.raspisanie.data.SubjectInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_DM: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM", Locale.forLanguageTag("ru"))

/** «сегодня», «завтра», «пт 25.09», «просрочено · 23.09». */
fun dueLabel(due: LocalDate?, today: LocalDate = LocalDate.now()): String = when {
    due == null -> "без срока"
    due.isBefore(today) -> "просрочено · ${due.format(DAY_DM)}"
    due == today -> "сегодня"
    due == today.plusDays(1) -> "завтра"
    else -> due.format(DAY_DM)
}

/** Вкладка «Предметы»: ближайшие задания и список предметов с ДЗ. */
@Composable
fun SubjectsScreen(state: UiState, vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var expanded by rememberSaveable { mutableStateOf("") }
    val open = state.homework.filter { !it.done }.sortedBy { it.due ?: LocalDate.MAX }
    // предметы из расписания (без мероприятий) + предметы, по которым уже есть ДЗ
    val fromSchedule = state.subjects.filterNot { s -> s.kinds.isNotEmpty() && s.kinds.all { it.startsWith("Мероприят") } }
    val extra = state.homework.map { it.subject }.distinct()
        .filter { name -> fromSchedule.none { it.name == name } }
        .map { SubjectInfo(it, null, null, emptyList(), emptyList()) }
    val subjects = fromSchedule + extra

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Предметы", onBack = null)
        LazyColumn(
            Modifier.fillMaxSize().fadeEdges(40f, 70f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("Ближайшие задания") }
            if (open.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 40.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Невыполненных заданий нет", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Нажмите на предмет ниже, чтобы записать ДЗ",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            open.take(6).forEach { h ->
                                HomeworkRow(
                                    h,
                                    showSubject = true,
                                    onToggle = { vm.toggleHomework(h) },
                                    onClick = { vm.openHomeworkEditor(h.subject, h) },
                                )
                            }
                            if (open.size > 6) {
                                Text(
                                    "…и ещё ${open.size - 6} — смотрите в предметах",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Все предметы") }
            if (subjects.isEmpty()) {
                item {
                    Text(
                        "Список появится после загрузки расписания.",
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
            }
            items(subjects, key = { it.name }) { s ->
                val hw = state.homework.filter { it.subject == s.name }
                    .sortedWith(compareBy({ it.done }, { it.due ?: LocalDate.MAX }))
                SubjectCard(
                    s = s,
                    homework = hw,
                    expanded = expanded == s.name,
                    today = today,
                    onToggleExpand = { expanded = if (expanded == s.name) "" else s.name },
                    onAdd = { vm.openHomeworkEditor(s.name) },
                    onToggleHw = { vm.toggleHomework(it) },
                    onEditHw = { vm.openHomeworkEditor(it.subject, it) },
                )
            }
        }
    }
}

@Composable
private fun SubjectCard(
    s: SubjectInfo,
    homework: List<Homework>,
    expanded: Boolean,
    today: LocalDate,
    onToggleExpand: () -> Unit,
    onAdd: () -> Unit,
    onToggleHw: (Homework) -> Unit,
    onEditHw: (Homework) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val openCount = homework.count { !it.done }
    GlassCard(Modifier.fillMaxWidth(), onClick = onToggleExpand) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (s.nextDate != null) {
                            "Следующая пара: ${dueLabel(s.nextDate, today)}" + (s.nextStart?.let { ", $it" } ?: "")
                        } else "Ближайших пар в загруженном расписании нет",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                if (openCount > 0) {
                    Text(
                        "$openCount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(cs.primary)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    if (s.teachers.isNotEmpty()) {
                        Text(
                            s.teachers.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    homework.forEach { h ->
                        HomeworkRow(h, onToggle = { onToggleHw(h) }, onClick = { onEditHw(h) })
                    }
                    Spacer(Modifier.height(6.dp))
                    GlassPill("+ Записать ДЗ", selected = true, onClick = onAdd)
                }
            }
        }
    }
}

/** Строка задания: круглая галочка (с пружинкой), текст и срок. */
@Composable
fun HomeworkRow(h: Homework, showSubject: Boolean = false, onToggle: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val overdue = !h.done && h.due != null && h.due.isBefore(today)
    val soon = !h.done && h.due != null && !h.due.isAfter(today.plusDays(1))
    val fill by animateColorAsState(if (h.done) cs.primary else Color.Transparent, tween(200), label = "chk")
    val scale by animateFloatAsState(
        if (h.done) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chkScale",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skinShape(14))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (h.done) cs.primary else cs.outline, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (h.done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Выполнено",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(16.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showSubject) {
                Text(
                    h.subject,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                h.text,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (h.done) TextDecoration.LineThrough else null,
                color = if (h.done) cs.onSurfaceVariant else cs.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            dueLabel(h.due, today),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = when {
                h.done -> cs.onSurfaceVariant
                overdue -> changedColor()
                soon -> cs.onTertiaryContainer
                else -> cs.onSurfaceVariant
            },
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    when {
                        h.done -> Color.Transparent
                        overdue -> changedColor().copy(alpha = 0.14f)
                        soon -> cs.tertiaryContainer
                        else -> cs.onSurface.copy(alpha = 0.06f)
                    },
                )
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** Редактор ДЗ: текст и срок (к следующей паре, завтра, через неделю или любая дата). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeworkEditorDialog(draft: HomeworkDraft, vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var text by remember(draft) { mutableStateOf(draft.text) }
    var due by remember(draft) { mutableStateOf(draft.due) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.closeHomeworkEditor() },
        title = { Text(draft.subject, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Что задали") },
                    minLines = 3,
                    shape = skinShape(18),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Срок: ${dueLabel(due, today)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.nextLesson?.let { n ->
                        GlassPill("К след. паре (${dueLabel(n, today)})", selected = due == n) { due = n }
                    }
                    GlassPill("Завтра", selected = due == today.plusDays(1)) { due = today.plusDays(1) }
                    GlassPill("Через неделю", selected = due == today.plusWeeks(1)) { due = today.plusWeeks(1) }
                    GlassPill("Без срока", selected = due == null) { due = null }
                    GlassPill("Дата…", selected = false) { picking = true }
                }
                if (draft.id != null) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { vm.deleteHomework(draft.id) }) {
                        Text("Удалить задание", color = changedColor())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { vm.saveHomework(draft.copy(text = text, due = due)) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeHomeworkEditor() }) { Text("Отмена") }
        },
        containerColor = cs.surfaceContainerHigh,
    )

    if (picking) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = st)
        }
    }
}
