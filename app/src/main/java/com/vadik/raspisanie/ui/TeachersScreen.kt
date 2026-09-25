package com.vadik.raspisanie.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.ScheduleText
import com.vadik.raspisanie.data.Teacher
import com.vadik.raspisanie.data.TeacherLesson
import com.vadik.raspisanie.data.TeacherWeek
import com.vadik.raspisanie.data.normalizeSearch
import com.vadik.raspisanie.data.timeKey
import java.time.LocalDate
import java.time.LocalTime

private val DAY_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private fun dm(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

/** Копирует текст и коротко сообщает об этом (Android 13+ показывает своё окошко сам). */
fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("MyGub", text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
}

/** Поиск по нескольким словам: «волч ив» найдёт «Волчков Иван Сергеевич». */
private fun matches(t: Teacher, q: String): Boolean {
    val words = q.split(' ').filter { it.isNotBlank() }
    val key = t.searchKey + " " + normalizeSearch(t.shortName)
    return words.all { it in key }
}

@Composable
fun TeachersScreen(state: UiState, vm: MainViewModel) {
    val ts = state.teachers
    if (ts.selected != null) {
        BackHandler { vm.closeTeacher() }
        TeacherDetail(ts, vm)
        return
    }
    val q = normalizeSearch(ts.query)
    val results = remember(q, ts.all, ts.mine) {
        if (q.isEmpty()) emptyList()
        else (ts.mine + ts.all.orEmpty()).distinctBy { it.id }.filter { matches(it, q) }
            .sortedWith(compareBy({ !it.searchKey.startsWith(q) }, { it.searchKey })).take(80)
    }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Преподаватели", onBack = null)
        OutlinedTextField(
            value = ts.query,
            onValueChange = { vm.setTeacherQuery(it) },
            singleLine = true,
            placeholder = { Text("Фамилия преподавателя") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (ts.query.isNotEmpty()) IconButton(onClick = { vm.setTeacherQuery("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize().fadeEdges(40f, 70f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (q.isEmpty()) {
                item { SectionTitle("Преподаватели вашей группы") }
                if (ts.mine.isEmpty()) item {
                    Hint("Пока пусто — откройте расписание, и здесь появятся все, кто у вас ведёт пары.")
                }
                items(ts.mine, key = { "m" + it.id }) { t -> TeacherRow(t) { vm.openTeacher(t) } }
                item {
                    Spacer(Modifier.height(6.dp))
                    Hint(
                        when {
                            ts.loadingList -> "Загружаю список всех преподавателей…"
                            ts.all != null -> "Поиск — по всем ${ts.all.size} преподавателям вуза."
                            else -> "Сайт не отдал общий список — поиск идёт среди преподавателей вашей группы."
                        },
                    )
                    if (ts.all == null && !ts.loadingList && ts.listTried) {
                        val ctx = LocalContext.current
                        Row(Modifier.padding(start = 14.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassPill("Повторить", selected = true) { vm.retryTeacherList() }
                            GlassPill("Отправить ответ сайта автору", selected = false) {
                                shareRaw(ctx, vm.teacherRawFile(), "mygub_teacher_answer.txt", "Отправить ответ сайта")
                            }
                        }
                    }
                }
            } else {
                if (results.isEmpty()) item {
                    Hint(if (ts.loadingList) "Загружаю список преподавателей…" else "Никого не нашлось. Попробуйте ввести только фамилию.")
                }
                items(results, key = { "r" + it.id }) { t -> TeacherRow(t) { vm.openTeacher(t) } }
            }
        }
    }
}

@Composable
private fun TeacherRow(t: Teacher, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), shape = skinShape(22), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val initials = t.fullName.split(' ').filter { it.isNotBlank() }.take(2)
                    .joinToString("") { it.first().uppercase() }
                Text(initials, color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                t.department?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- расписание преподавателя

/** Где преподаватель сейчас или будет дальше (только для текущей недели). */
private fun statusLine(w: TeacherWeek): String? {
    val today = LocalDate.now()
    if (today < w.monday || today > w.monday.plusDays(6)) return null
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val active = w.lessons.filter { !it.cancelled && !it.replaced && !it.moved }
    fun room(l: TeacherLesson) = l.room?.let { if (it.first().isDigit()) "ауд. $it" else it } ?: "аудитория не указана"
    active.filter { it.date == today }.firstOrNull { now in timeKey(it.start) until timeKey(it.end.ifBlank { it.start }) }
        ?.let { return "Сейчас: ${room(it)} · до ${it.end} · ${it.subject}" }
    active.filter { it.date == today && timeKey(it.start) > now }.minByOrNull { timeKey(it.start) }
        ?.let { return "Сегодня в ${it.start}: ${room(it)}" }
    active.filter { it.date > today }.minWithOrNull(compareBy({ it.date }, { timeKey(it.start) }))
        ?.let { return "Ближайшая пара: ${DAY_SHORT[it.date.dayOfWeek.value - 1]}, ${it.start} · ${room(it)}" }
    return if (active.any { it.date == today }) "Сегодня пары уже закончились" else "На этой неделе пар больше нет"
}

@Composable
private fun TeacherDetail(ts: TeachersState, vm: MainViewModel) {
    val t = ts.selected ?: return
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val w = ts.week
    val thisWeek = ts.monday == com.vadik.raspisanie.data.Repository.mondayOf(LocalDate.now())
    val listState = rememberLazyListState()
    // на текущей неделе сразу прокручиваем к сегодняшнему дню (или ближайшему следующему с парами)
    LaunchedEffect(w) {
        if (w == null || !thisWeek) return@LaunchedEffect
        val today = LocalDate.now()
        var idx = 2 // карточка преподавателя + выбор недели
        if (!w.full) idx++
        if (ts.error != null) idx++
        var target = -1
        for ((date, list) in w.lessons.groupBy { it.date }.toSortedMap()) {
            if (date >= today) { target = idx; break }
            idx += 1 + list.size
        }
        if (target > 2) listState.animateScrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(t.shortName, onBack = { vm.closeTeacher() })
        LazyColumn(
            Modifier.fillMaxSize().fadeEdges(40f, 70f),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), shape = skinShape(26), strong = true) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        t.department?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        w?.let(::statusLine)?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.primary)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), shape = skinShape(22)) {
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.teacherShiftWeek(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${dm(ts.monday)} – ${dm(ts.monday.plusDays(6))}", fontWeight = FontWeight.SemiBold)
                            if (!thisWeek) Text(
                                "к текущей неделе",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary,
                                modifier = Modifier.clickable { vm.teacherThisWeek() },
                            )
                        }
                        IconButton(onClick = { vm.teacherShiftWeek(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
                        }
                    }
                }
                // в том же элементе списка, чтобы не сбивать автопрокрутку к сегодняшнему дню
                SlowSiteNote(ts.loadingWeek, Modifier.padding(horizontal = 0.dp))
            }
            if (w != null && !w.full) item {
                GlassCard(Modifier.fillMaxWidth(), shape = skinShape(22), tint = cs.tertiary.copy(alpha = 0.14f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            w.note ?: "Сайт не отдал полное расписание преподавателя — показаны только пары из расписания вашей группы.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassPill("Повторить", selected = true) { vm.retryTeacherFull() }
                            GlassPill("Отправить ответ сайта автору", selected = false) {
                                shareRaw(ctx, vm.teacherRawFile(), "mygub_teacher_answer.txt", "Отправить ответ сайта")
                            }
                        }
                    }
                }
            }
            ts.error?.let { e ->
                item {
                    Text(e, color = cs.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                    GlassPill("Повторить", selected = true) { vm.loadTeacherWeek() }
                }
            }
            if (w == null && ts.loadingWeek) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (w != null) {
                val byDay = w.lessons.groupBy { it.date }.toSortedMap()
                if (byDay.isEmpty()) item {
                    Hint(if (w.full) "На этой неделе пар нет." else "На этой неделе у вашей группы нет пар с этим преподавателем.")
                }
                byDay.forEach { (date, list) ->
                    item(key = "d$date") {
                        Text(
                            DAY_NAMES[date.dayOfWeek.value - 1] + ", " + dm(date) + if (date == LocalDate.now()) " · сегодня" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                        )
                    }
                    items(list.sortedBy { timeKey(it.start) }, key = { "l$date${it.start}${it.subject}${it.groups}" }) { l ->
                        TeacherLessonCard(l) { vm.showOnMap(l.room) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                        GlassPill("Скопировать неделю текстом", selected = false) {
                            copyText(ctx, ScheduleText.teacherWeek(w))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherLessonCard(l: TeacherLesson, onMap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val red = changedColor()
    val inactive = l.cancelled || l.replaced || l.moved
    GlassCard(Modifier.fillMaxWidth().alpha(if (inactive) 0.55f else 1f), shape = skinShape(22)) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.width(56.dp)) {
                Text(l.start, fontWeight = FontWeight.Bold)
                Text(l.end, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                val loc = Campus.locate(l.room)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        l.room?.let { if (it.first().isDigit()) "ауд. $it" else it } ?: "аудитория не указана",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (l.roomChanged) red else cs.primary,
                    )
                    if (loc != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "на карте ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.primary,
                            modifier = Modifier.clickable(onClick = onMap),
                        )
                    }
                }
                loc?.let { Text(it.summary, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                Text(
                    l.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (l.cancelled) TextDecoration.LineThrough else null,
                )
                val meta = listOfNotNull(l.kind, l.groups.ifBlank { null }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val mark = when {
                    l.replaced -> "Замена: пару ведёт другой преподаватель"
                    l.cancelled -> "Пара отменена"
                    l.moved -> "Пара перенесена"
                    l.substitute -> "На замене"
                    else -> null
                }
                mark?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = red) }
            }
        }
    }
}
