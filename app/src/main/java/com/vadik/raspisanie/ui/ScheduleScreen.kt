package com.vadik.raspisanie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.vadik.raspisanie.data.Edition
import com.vadik.raspisanie.data.ScheduleText
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Homework
import com.vadik.raspisanie.data.Lesson
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
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
/** «пятница, 25 сентября» */
private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.forLanguageTag("ru"))
private val DM_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM в HH:mm")

private enum class Screen(val depth: Int) { Loading(0), Onboarding(0), Main(0), Picker(1), ThemeEditor(1), Detail(2) }

private val TABS = listOf(
    TabItem("Расписание", Icons.Filled.DateRange),
    TabItem("Предметы", Icons.Filled.Edit),
    TabItem("Преподы", Icons.Filled.Person),
    TabItem("Карта", Icons.Filled.Place),
    TabItem("Настройки", Icons.Filled.Settings),
)

@Composable
fun AppRoot(state: UiState, vm: MainViewModel) {
    if (state.tampered) {
        TamperScreen()
        return
    }
    val settings = state.settings
    val screen = when {
        state.starting -> Screen.Loading
        state.onboarding != null -> Screen.Onboarding
        state.picker != null || settings == null -> Screen.Picker
        state.detail != null -> Screen.Detail
        state.showThemeEditor -> Screen.ThemeEditor
        else -> Screen.Main
    }
    // последняя открытая пара — чтобы экран не пропал посреди анимации закрытия
    val lastDetail = remember { arrayOfNulls<LessonDetail>(1) }
    state.detail?.let { lastDetail[0] = it }

    AuroraBackground(animated = state.prefs.animatedBackground && !Edition.lite) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (Edition.lite) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
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
                    LessonScreen(d, state, vm) { vm.closeLesson() }
                }
                Screen.ThemeEditor -> {
                    BackHandler { vm.closeThemeEditor() }
                    ThemeEditorScreen(state, vm)
                }
                Screen.Main -> MainTabs(state, vm)
                Screen.Onboarding -> state.onboarding?.let { OnboardingScreen(it, vm) }
            }
        }
    }
    state.captcha?.let { CaptchaDialog(it, vm) }
    state.hwDraft?.let { HomeworkEditorDialog(it, vm) }
    if (state.update.showDialog && state.update.info != null) UpdateDialog(state.update, vm)
}

/** Три вкладки с нижней панелью; содержимое меняется с лёгким сдвигом. */
@Composable
private fun MainTabs(state: UiState, vm: MainViewModel) {
    BackHandler(enabled = state.tab != 0) { vm.selectTab(0) }
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                if (Edition.lite) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { dir * it / 5 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -dir * it / 5 } + fadeOut(tween(150)))
            },
            modifier = Modifier.weight(1f),
            label = "tab",
        ) { t ->
            when (t) {
                MainViewModel.TAB_SUBJECTS -> SubjectsScreen(state, vm)
                MainViewModel.TAB_TEACHERS -> TeachersScreen(state, vm)
                MainViewModel.TAB_MAP -> MapScreen(state, vm)
                MainViewModel.TAB_SETTINGS -> SettingsScreen(state, vm)
                else -> ScheduleScreen(state, vm)
            }
        }
        StyledTabBar(TABS, state.tab) { vm.selectTab(it) }
    }
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
        // ---------------- шапка: сегодняшняя дата крупно, группа мелко под ней
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        today.format(HEADER_DATE).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    // тестовая сборка помечена, чтобы не путать с основной
                    if (androidx.compose.ui.platform.LocalContext.current.packageName.endsWith(".beta")) {
                        Text(
                            "BETA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onTertiary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(cs.tertiary)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                val sub = listOfNotNull(
                    settings.groupName,
                    state.week?.weekTypeLabel,
                    if (state.prefs.subgroup != 0) "${state.prefs.subgroup} подгр." else null,
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
            GlassIconButton(Icons.Filled.Refresh, "Обновить", size = 38.dp, iconRotation = spin) { vm.refresh() }
        }

        // ---------------- неделя и дни
        GlassCard(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
            shape = skinShape(26),
            strong = true,
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
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
        UpdateBanner(state, vm)

        // ---------------- пары
        Spacer(Modifier.height(4.dp))
        // верх списка плавно растворяется, а не обрезается под карточкой с днями
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().fadeEdges(40f, 70f),
        ) { page ->
            DayPage(days[page], today, state.week, state.refreshing, state.prefs, state.homework, updatedText(state.week?.fetchedAt), onWhere = { vm.showOnMap(it.room) }, groupName = settings.groupName) { d, l -> vm.openLesson(d, l) }
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
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    style = MaterialTheme.typography.titleSmall,
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
                .height(54.dp)
                .padding(horizontal = 3.dp)
                .clip(skinShape(22))
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
                        .height(54.dp)
                        .clip(skinShape(22))
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
                        style = MaterialTheme.typography.titleMedium,
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
        shape = skinShape(22),
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
    homework: List<Homework>,
    updated: String,
    onWhere: (Lesson) -> Unit,
    groupName: String? = null,
    onOpen: (LocalDate, Lesson) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val title = DAY_FULL[date.dayOfWeek.value - 1] + ", " + date.format(DM) +
        if (date == today) " · сегодня" else if (date == today.plusDays(1)) " · завтра" else ""
    if (week == null) {
        EmptyState(
            if (refreshing) "⏳" else "📭",
            title,
            if (refreshing) "Загружаю расписание…" else "Нет сохранённого расписания на эту неделю. Нажмите ⟳ вверху.",
            updated,
        )
        return
    }
    val lessons = week.lessonsOn(date).filter { prefs.shows(it) }
    if (lessons.isEmpty()) {
        EmptyState("🎉", title, "Пар нет — можно отдохнуть", updated)
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

    // При открытии сегодняшнего дня сразу прокручиваем к паре, которая идёт сейчас
    // (или к ближайшей следующей), чтобы не листать вручную.
    val listState = rememberLazyListState()
    LaunchedEffect(date, lessons.size) {
        if (date != today) return@LaunchedEffect
        val idx = lessons.indexOfFirst { l ->
            val s = timeKey(l.start)
            val e = timeKey(l.end.ifBlank { l.start })
            !l.cancelled && !l.moved && !prefs.isOtherSubgroup(l) && nowMin < e && (nowMin >= s || l == nextStart)
        }
        if (idx > 0) {
            delay(250) // даём карточкам начать появляться
            listState.animateScrollToItem(idx + 1) // +1 — заголовок дня
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 0.dp),
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
            val hw = homework.filter { !it.done && it.subject == l.subject && it.due == date }
            LessonCard(
                homework = hw,
                l = l,
                isNow = isNow,
                progress = progress,
                minutesLeft = e - nowMin,
                nextIn = nextIn,
                otherSubgroup = other,
                onWhere = { onWhere(l) },
                modifier = Modifier.graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationY = (1f - appear.value) * 80f
                    val sc = 0.94f + 0.06f * appear.value
                    scaleX = sc
                    scaleY = sc
                },
            ) { onOpen(date, l) }
        }
        // «Обновлено …» и копирование — в самом конце списка, уезжают вместе с парами
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassPill("⧉ Скопировать текстом", selected = false) {
                    copyText(ctx, ScheduleText.day(date, lessons, groupName))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    updated,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun updatedText(fetched: Long?): String =
    if (fetched != null && fetched > 0) {
        "Обновлено " + Instant.ofEpochMilli(fetched).atZone(ZoneId.systemDefault()).format(DM_HM)
    } else "Ещё не загружено"

private fun formatIn(min: Int): String = when {
    min < 60 -> "через $min мин"
    min % 60 == 0 -> "через ${min / 60} ч"
    else -> "через ${min / 60} ч ${min % 60} мин"
}

/** Пустой день: большой эмодзи мягко «парит». */
@Composable
private fun EmptyState(emoji: String, title: String, text: String, updated: String) {
    val t = if (Edition.lite) 0f else {
        val inf = rememberInfiniteTransition(label = "float")
        inf.animateFloat(
            0f, (2 * Math.PI).toFloat(),
            infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "t",
        ).value
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth(), shape = skinShape(32)) {
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
                Spacer(Modifier.height(14.dp))
                Text(
                    updated,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    homework: List<Homework>,
    l: Lesson,
    isNow: Boolean,
    progress: Float,
    minutesLeft: Int,
    nextIn: String?,
    otherSubgroup: Boolean,
    modifier: Modifier = Modifier,
    onWhere: () -> Unit = {},
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val red = changedColor()
    val inactive = l.cancelled || l.moved
    val alpha = if (inactive || otherSubgroup) 0.5f else 1f
    val stripe = kindColor(l.kind)
    val shape = skinShape(26)
    val glow = if (isNow && Edition.lite) 1f else if (isNow) {
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
                        // где это: корпус и этаж по номеру аудитории → на карту
                        Campus.locate(it)?.let { loc ->
                            Text(
                                "${loc.summary} · на карте ›",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary.copy(alpha = alpha),
                                modifier = Modifier
                                    .padding(start = 18.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onWhere)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
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
                    if (homework.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "📝 ДЗ: " + homework.joinToString(" · ") { it.text },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = cs.onTertiaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(skinShape(12))
                                .background(cs.tertiaryContainer.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
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
