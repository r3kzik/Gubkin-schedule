package com.vadik.raspisanie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Prefs
import com.vadik.raspisanie.data.WeekSchedule
import com.vadik.raspisanie.data.timeKey
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val DAY_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val DM_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM в HH:mm")

private enum class Screen(val depth: Int) { Loading(0), Schedule(0), Picker(1), Settings(1), Detail(2) }

@Composable
fun AppRoot(state: UiState, vm: MainViewModel) {
    val settings = state.settings
    val screen = when {
        state.starting -> Screen.Loading
        state.picker != null || settings == null -> Screen.Picker
        state.detail != null -> Screen.Detail
        state.showSettings -> Screen.Settings
        else -> Screen.Schedule
    }
    // последняя открытая пара — чтобы экран не пропал посреди анимации закрытия
    val lastDetail = remember { arrayOfNulls<LessonDetail>(1) }
    state.detail?.let { lastDetail[0] = it }

    AuroraBackground(animated = state.prefs.animatedBackground) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                val dur = 380
                if (forward) {
                    (slideInHorizontally(tween(dur)) { it / 3 } + fadeIn(tween(dur))) togetherWith
                        (slideOutHorizontally(tween(dur)) { -it / 6 } + fadeOut(tween(dur / 2)))
                } else {
                    (slideInHorizontally(tween(dur)) { -it / 6 } + fadeIn(tween(dur))) togetherWith
                        (slideOutHorizontally(tween(dur)) { it / 3 } + fadeOut(tween(dur / 2)))
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                Screen.Picker -> {
                    BackHandler(enabled = state.settings != null) { vm.closePicker() }
                    PickerScreen(state.picker ?: PickerState(), canClose = state.settings != null, vm = vm)
                }
                Screen.Detail -> lastDetail[0]?.let { d ->
                    BackHandler { vm.closeLesson() }
                    LessonScreen(d, state.prefs) { vm.closeLesson() }
                }
                Screen.Settings -> {
                    BackHandler { vm.closeSettings() }
                    SettingsScreen(state, vm)
                }
                Screen.Schedule -> ScheduleScreen(state, vm)
            }
        }
    }
    state.captcha?.let { CaptchaDialog(it, vm) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(state: UiState, vm: MainViewModel) {
    val settings = state.settings ?: return
    val cs = MaterialTheme.colorScheme
    val monday = state.monday
    val today = LocalDate.now()
    val days = remember(monday) { (0..6).map { monday.plusDays(it.toLong()) } }
    val targetPage = state.selectedDate.dayOfWeek.value - 1
    val pagerState = rememberPagerState(initialPage = targetPage, pageCount = { 7 })

    // свайп между днями -> выбранный день
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.selectDayOfWeek(it) }
    }
    // нажатие на день / «Сегодня» -> листаем
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    val spin = if (state.refreshing) {
        val inf = rememberInfiniteTransition(label = "spin")
        inf.animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "r",
        ).value
    } else 0f

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---------------- шапка
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    settings.groupName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                val sub = listOfNotNull(
                    state.week?.weekTypeLabel?.replaceFirstChar { it.uppercase() } ?: "Расписание занятий",
                    if (state.prefs.subgroup != 0) "${state.prefs.subgroup} подгруппа" else null,
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            GlassIconButton(Icons.Filled.Refresh, "Обновить", iconRotation = spin) { vm.refresh() }
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Filled.Settings, "Настройки") { vm.openSettings() }
        }

        // ---------------- неделя и дни
        GlassCard(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            strong = true,
        ) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                WeekHeader(
                    monday = monday,
                    isCurrentWeek = today in days,
                    onPrev = { vm.shiftWeek(-1) },
                    onNext = { vm.shiftWeek(1) },
                    onToday = { vm.goToday() },
                )
                DayStrip(days, state.selectedDate, today, state.week, state.prefs) { vm.selectDate(it) }
            }
        }

        // ---------------- сообщения
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Banner(
                title = state.error.orEmpty(),
                lines = emptyList(),
                tint = cs.error.copy(alpha = 0.16f),
                onClose = { vm.dismissError() },
            )
        }
        AnimatedVisibility(
            visible = state.changes.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Banner(
                title = "Расписание изменилось",
                lines = state.changes,
                tint = cs.tertiary.copy(alpha = 0.14f),
                onClose = { vm.dismissChanges() },
            )
        }

        // ---------------- пары
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
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
        )
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = monday,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(300)) { dir * it / 2 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { -dir * it / 2 } + fadeOut(tween(200)))
                },
                label = "week",
            ) { m ->
                Text(
                    "${m.format(DM)} – ${m.plusDays(6).format(DM)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(visible = !isCurrentWeek, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            GlassPill("Сегодня", selected = true, onClick = onToday)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
        }
    }
}

/** Дни недели: выбранный день подсвечен «пилюлей», которая плавно переезжает с пружинкой. */
@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    week: WeekSchedule?,
    prefs: Prefs,
    onClick: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        val cell = maxWidth / 7
        val index = days.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(
            cell * index,
            spring(dampingRatio = 0.72f, stiffness = 380f),
            label = "indicator",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(cell)
                .height(66.dp)
                .padding(horizontal = 3.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.78f)))),
        )
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val isSel = d == selected
                val hasLessons = week?.lessonsOn(d)?.any { !it.cancelled && prefs.concernsMe(it) } == true
                val fg by animateColorAsState(
                    when {
                        isSel -> cs.onPrimary
                        d == today -> cs.primary
                        else -> cs.onSurface
                    },
                    tween(250),
                    label = "dayFg",
                )
                Column(
                    Modifier
                        .width(cell)
                        .height(66.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClick(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(DAY_NAMES[i], style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.85f))
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (d == today || isSel) FontWeight.Bold else FontWeight.Normal,
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
}

@Composable
private fun Banner(title: String, lines: List<String>, tint: Color, onClose: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
        shape = RoundedCornerShape(22.dp),
        tint = tint,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                )
                GlassPill("Скрыть", selected = false, onClick = onClose)
            }
            lines.take(5).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
            }
            if (lines.size > 5) Text("…и ещё ${lines.size - 5}", style = MaterialTheme.typography.bodySmall)
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
        EmptyState(
            if (refreshing) "⏳" else "📭",
            title,
            if (refreshing) "Загружаю расписание…" else "Нет сохранённого расписания на эту неделю. Нажмите ⟳ вверху.",
        )
        return
    }
    val lessons = week.lessonsOn(date).filter { prefs.shows(it) }
    if (lessons.isEmpty()) {
        EmptyState("🎉", title, "Пар нет — можно отдохнуть")
        return
    }
    // раз в 30 секунд обновляем «идёт сейчас» и прогресс
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(date) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute } + tick * 0
    val nextStart = if (date == today) lessons
        .filter { !it.cancelled && !it.moved && !prefs.isOtherSubgroup(it) && timeKey(it.start) > nowMin }
        .minByOrNull { timeKey(it.start) } else null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
        itemsIndexed(lessons, key = { i, _ -> "$date-$i" }) { i, l ->
            // появление карточек по очереди: снизу вверх с лёгкой пружинкой
            val appear = remember(date) { Animatable(0f) }
            LaunchedEffect(date) {
                delay(i * 55L)
                appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 260f))
            }
            val other = prefs.isOtherSubgroup(l)
            val s = timeKey(l.start)
            val e = timeKey(l.end.ifBlank { l.start })
            val isNow = date == today && !l.cancelled && !l.moved && !other && nowMin in s until e
            val progress = if (isNow && e > s) (nowMin - s).toFloat() / (e - s) else 0f
            val nextIn = if (l == nextStart) formatIn(s - nowMin) else null
            LessonCard(
                l = l,
                isNow = isNow,
                progress = progress,
                minutesLeft = e - nowMin,
                nextIn = nextIn,
                otherSubgroup = other,
                modifier = Modifier.graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationY = (1f - appear.value) * 80f
                    val sc = 0.94f + 0.06f * appear.value
                    scaleX = sc
                    scaleY = sc
                },
            ) { onOpen(date, l) }
        }
    }
}

private fun formatIn(min: Int): String = when {
    min < 60 -> "через $min мин"
    min % 60 == 0 -> "через ${min / 60} ч"
    else -> "через ${min / 60} ч ${min % 60} мин"
}

/** Пустой день: большой эмодзи мягко «парит». */
@Composable
private fun EmptyState(emoji: String, title: String, text: String) {
    val inf = rememberInfiniteTransition(label = "float")
    val t by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, fontSize = 56.sp, modifier = Modifier.graphicsLayer { translationY = sin(t) * 14f })
                Spacer(Modifier.height(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Цвет полоски слева — по типу занятия. */
@Composable
private fun kindColor(kind: String?): Color {
    val cs = MaterialTheme.colorScheme
    val k = kind?.lowercase().orEmpty()
    return when {
        k.startsWith("лек") -> cs.primary
        k.startsWith("сем") || k.startsWith("прак") -> cs.tertiary
        k.startsWith("лаб") -> cs.secondary
        else -> cs.outline
    }
}

@Composable
private fun LessonCard(
    l: Lesson,
    isNow: Boolean,
    progress: Float,
    minutesLeft: Int,
    nextIn: String?,
    otherSubgroup: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val red = changedColor()
    val inactive = l.cancelled || l.moved
    val alpha = if (inactive || otherSubgroup) 0.5f else 1f
    val stripe = kindColor(l.kind)
    val shape = RoundedCornerShape(26.dp)
    val glow = if (isNow) {
        val inf = rememberInfiniteTransition(label = "glow")
        inf.animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g",
        ).value
    } else 0f

    GlassCard(
        modifier
            .fillMaxWidth()
            .then(if (isNow) Modifier.border(2.dp, cs.primary.copy(alpha = glow), shape) else Modifier),
        shape = shape,
        strong = isNow,
        tint = if (isNow) cs.primary.copy(alpha = 0.10f) else null,
        onClick = onClick,
    ) {
        CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
            Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(stripe.copy(alpha = alpha), stripe.copy(alpha = alpha * 0.4f)))),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.width(54.dp)) {
                    Text(
                        l.start,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor(alpha),
                    )
                    if (l.end.isNotBlank()) {
                        Text(l.end, style = MaterialTheme.typography.bodySmall, color = contentColor(alpha * 0.65f))
                    }
                }
                Column(Modifier.weight(1f)) {
                    if (isNow) {
                        Text(
                            "● ИДЁТ СЕЙЧАС",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                    } else if (nextIn != null) {
                        Text(
                            "СЛЕДУЮЩАЯ · ${nextIn.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.tertiary,
                        )
                    }
                    Text(
                        l.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (inactive) TextDecoration.LineThrough else null,
                        color = contentColor(alpha),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        l.kind?.let { Chip(it, stripe.copy(alpha = 0.16f * alpha + 0.04f), stripe.copy(alpha = alpha)) }
                        l.subgroup?.let {
                            Spacer(Modifier.width(6.dp))
                            SubgroupBadge(it, otherSubgroup)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(6.dp))
                        Text(
                            flags.joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = red,
                        )
                    }
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
                        Text("Есть изменения — нажмите, чтобы посмотреть", style = MaterialTheme.typography.labelMedium, color = red)
                    }
                    if (isNow) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(progress)
                        Text(
                            "до конца пары $minutesLeft мин",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val cs = MaterialTheme.colorScheme
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(cs.onSurface.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))),
        )
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Метка «1 подгр.»: своя подгруппа — цветная, чужая — серая. */
@Composable
fun SubgroupBadge(n: Int, other: Boolean) {
    val cs = MaterialTheme.colorScheme
    Chip(
        "$n подгр.",
        if (other) cs.onSurface.copy(alpha = 0.08f) else cs.secondaryContainer,
        if (other) cs.onSurfaceVariant else cs.onSecondaryContainer,
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    text: String,
    alpha: Float,
    highlight: Color? = null,
    note: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = highlight ?: contentColor(alpha * 0.7f))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = highlight?.copy(alpha = alpha) ?: contentColor(alpha * 0.9f),
            fontWeight = if (highlight != null) FontWeight.Bold else null,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor(alpha * 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun contentColor(alpha: Float): Color = LocalContentColor.current.copy(alpha = alpha)
