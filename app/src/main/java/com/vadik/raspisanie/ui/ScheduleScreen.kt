package com.vadik.raspisanie.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Prefs
import com.vadik.raspisanie.data.WeekSchedule
import com.vadik.raspisanie.data.timeKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val DAY_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val DM_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM в HH:mm")

@Composable
fun AppRoot(state: UiState, vm: MainViewModel) {
    val settings = state.settings
    when {
        state.starting -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.picker != null || settings == null -> {
            BackHandler(enabled = settings != null) { vm.closePicker() }
            PickerScreen(state.picker ?: PickerState(), canClose = settings != null, vm = vm)
        }

        state.detail != null -> {
            BackHandler { vm.closeLesson() }
            LessonScreen(state.detail, state.prefs) { vm.closeLesson() }
        }

        state.showSettings -> {
            BackHandler { vm.closeSettings() }
            SettingsScreen(state, vm)
        }

        else -> ScheduleScreen(state, vm)
    }
    state.captcha?.let { CaptchaDialog(it, vm) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(state: UiState, vm: MainViewModel) {
    val settings = state.settings ?: return
    val monday = state.monday
    val today = LocalDate.now()
    val days = remember(monday) { (0..6).map { monday.plusDays(it.toLong()) } }
    val targetPage = state.selectedDate.dayOfWeek.value - 1
    val pagerState = rememberPagerState(initialPage = targetPage, pageCount = { 7 })
    var menuOpen by remember { mutableStateOf(false) }

    // свайп между днями -> выбранный день
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.selectDayOfWeek(it) }
    }
    // нажатие на день / «Сегодня» -> листаем pager
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            settings.groupName +
                                if (state.prefs.subgroup != 0) " · ${state.prefs.subgroup} подгр." else "",
                            maxLines = 1,
                        )
                        val sub = state.week?.weekTypeLabel ?: "Расписание занятий"
                        Text(
                            sub.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.refreshing) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = { menuOpen = false; vm.openSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text("Сменить группу") },
                                onClick = { menuOpen = false; vm.openPicker() },
                            )
                            DropdownMenuItem(
                                text = { Text("Ввести капчу сайта") },
                                onClick = { menuOpen = false; vm.openCaptcha() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            WeekHeader(
                monday = monday,
                isCurrentWeek = today in days,
                onPrev = { vm.shiftWeek(-1) },
                onNext = { vm.shiftWeek(1) },
                onToday = { vm.goToday() },
            )
            DayStrip(days, state.selectedDate, today, state.week, state.prefs) { vm.selectDate(it) }

            state.error?.let { msg ->
                Banner(
                    title = msg,
                    lines = emptyList(),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onClose = { vm.dismissError() },
                )
            }
            if (state.changes.isNotEmpty()) {
                Banner(
                    title = "Расписание изменилось",
                    lines = state.changes,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClose = { vm.dismissChanges() },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                DayPage(days[page], today, state.week, state.refreshing, state.prefs) { d, l -> vm.openLesson(d, l) }
            }

            val fetched = state.week?.fetchedAt
            Text(
                text = if (fetched != null && fetched > 0) {
                    "Обновлено " + Instant.ofEpochMilli(fetched).atZone(ZoneId.systemDefault()).format(DM_HM)
                } else "Ещё не загружено",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun WeekHeader(
    monday: LocalDate,
    isCurrentWeek: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
        }
        Text(
            "${monday.format(DM)} – ${monday.plusDays(6).format(DM)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        if (!isCurrentWeek) {
            TextButton(onClick = onToday) { Text("Сегодня") }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
        }
    }
}

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    week: WeekSchedule?,
    prefs: Prefs,
    onClick: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEachIndexed { i, d ->
            val isSel = d == selected
            val hasLessons = week?.lessonsOn(d)?.any { !it.cancelled && prefs.concernsMe(it) } == true
            val bg = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
            val fg = when {
                isSel -> MaterialTheme.colorScheme.onPrimary
                d == today -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onClick(d) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(DAY_NAMES[i], style = MaterialTheme.typography.labelMedium, color = fg)
                Text(
                    d.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                    color = fg,
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (hasLessons) fg else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun Banner(
    title: String,
    lines: List<String>,
    container: Color,
    content: Color,
    onClose: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                )
                TextButton(onClick = onClose) { Text("Скрыть") }
            }
            lines.take(6).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 10.dp))
            }
            if (lines.size > 6) {
                Text("…и ещё ${lines.size - 6}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    week: WeekSchedule?,
    refreshing: Boolean,
    prefs: Prefs,
    onOpen: (LocalDate, Lesson) -> Unit,
) {
    val title = DAY_FULL[date.dayOfWeek.value - 1] + ", " + date.format(DM) +
        if (date == today) " · сегодня" else if (date == today.plusDays(1)) " · завтра" else ""
    if (week == null) {
        CenterMessage(
            title,
            if (refreshing) "Загружаю расписание…" else "Нет сохранённого расписания на эту неделю. Нажмите ⟳ вверху.",
            showProgress = refreshing,
        )
        return
    }
    val lessons = week.lessonsOn(date).filter { prefs.shows(it) }
    if (lessons.isEmpty()) {
        CenterMessage(title, "Пар нет 🎉", showProgress = false)
        return
    }
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
            )
        }
        items(lessons) { l ->
            val other = prefs.isOtherSubgroup(l)
            val isNow = date == today && !l.cancelled && !other &&
                nowMinutes >= timeKey(l.start) && nowMinutes < timeKey(l.end.ifBlank { l.start })
            LessonCard(l, isNow, other) { onOpen(date, l) }
        }
    }
}

@Composable
private fun CenterMessage(title: String, text: String, showProgress: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (showProgress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonCard(l: Lesson, isNow: Boolean, otherSubgroup: Boolean, onClick: () -> Unit) {
    val inactive = l.cancelled || l.moved
    val faded = inactive || otherSubgroup
    val colors = when {
        isNow -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val alpha = if (faded) 0.5f else 1f
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.width(58.dp)) {
                Text(
                    l.start,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor(alpha),
                )
                if (l.end.isNotBlank()) {
                    Text(l.end, style = MaterialTheme.typography.bodySmall, color = contentColor(alpha * 0.8f))
                }
            }
            Column(Modifier.weight(1f)) {
                if (isNow) {
                    Text(
                        "ИДЁТ СЕЙЧАС",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    l.subject,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (inactive) TextDecoration.LineThrough else null,
                    color = contentColor(alpha),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    l.kind?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        )
                    }
                    l.subgroup?.let { SubgroupBadge(it, otherSubgroup) }
                }
                Spacer(Modifier.height(6.dp))
                val red = changedColor()
                // заменённые аудитория/преподаватель — красным, как на сайте, и рядом «было»
                l.room?.let {
                    InfoRow(
                        Icons.Filled.Place, "ауд. $it", alpha,
                        highlight = if (l.roomChanged) red else null,
                        note = l.oldRoom?.let { o -> "было ${o.substringBefore(" - ")}" },
                    )
                }
                l.teacher?.let {
                    InfoRow(
                        Icons.Filled.Person, it, alpha,
                        highlight = if (l.teacherChanged) red else null,
                        note = l.oldTeacher?.let { o -> "вместо $o" },
                    )
                }
                val flags = buildList {
                    if (l.cancelled) add("Пара отменена")
                    if (l.moved) add(l.movedTo?.let { "Перенесена на $it" } ?: "Пара перенесена")
                    l.movedFrom?.let { add("Перенесено с $it") }
                }
                if (flags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        flags.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = red,
                    )
                }
                // изменения, которые не видны в строках выше (неизвестные виды) — текстом
                val shown = l.roomChanged || l.teacherChanged || l.movedFrom != null
                val otherLines = l.changeLines.filterNot {
                    it.startsWith("Аудитория") || it.startsWith("Преподаватель") || it.startsWith("Перенесено")
                }
                otherLines.firstOrNull()?.let {
                    Text(
                        it + if (otherLines.size > 1) " (ещё ${otherLines.size - 1})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = red,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (l.changed && !shown && otherLines.isEmpty()) {
                    Text(
                        "Есть изменения — нажмите, чтобы посмотреть",
                        style = MaterialTheme.typography.labelMedium,
                        color = red,
                    )
                }
            }
        }
    }
}

/** Метка «1 подгр.»: своя подгруппа — цветная, чужая — серая. */
@Composable
fun SubgroupBadge(n: Int, other: Boolean) {
    val bg = if (other) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (other) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        "$n подгр.",
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    alpha: Float,
    highlight: Color? = null,
    note: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = highlight ?: contentColor(alpha * 0.8f))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = highlight?.copy(alpha = alpha) ?: contentColor(alpha),
            fontWeight = if (highlight != null) FontWeight.Bold else null,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor(alpha * 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Красный для изменений (как на сайте), читаемый и в светлой, и в тёмной теме. */
@Composable
fun changedColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF7A7A) else Color(0xFFD32F2F)

@Composable
private fun contentColor(alpha: Float): Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = alpha)
