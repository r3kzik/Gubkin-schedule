package com.vadik.raspisanie.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Преподаватель. id — как на сайте (например «BQZubwwD»). */
data class Teacher(
    val id: String,
    val fullName: String,
    val shortName: String,
    val department: String? = null,
    /** id кафедры — сайт отдаёт расписание преподавателя по паре divisionId + teacherId. */
    val divisionId: String? = null,
) {
    /** Ключ для поиска: без регистра и «ё». */
    val searchKey: String get() = normalizeSearch(fullName)
}

/** Пара преподавателя. */
data class TeacherLesson(
    val date: LocalDate,
    val start: String,
    val end: String,
    val subject: String,
    val kind: String?,
    val room: String?,
    /** Группы, у которых эта пара: «КВ-26-02, КВ-26-03». */
    val groups: String,
    val cancelled: Boolean,
    val moved: Boolean,
    val roomChanged: Boolean,
    /** Эту пару у него забрали — ведёт другой преподаватель. */
    val replaced: Boolean = false,
    /** Он ведёт пару вместо другого преподавателя. */
    val substitute: Boolean = false,
)

data class TeacherWeek(
    val teacher: Teacher,
    val monday: LocalDate,
    val lessons: List<TeacherLesson>,
    /** true — полное расписание с сайта; false — только пары вашей группы из сохранённых недель. */
    val full: Boolean,
    val fetchedAt: Long,
) {
    fun on(date: LocalDate) = lessons.filter { it.date == date }.sortedBy { timeKey(it.start) }
}

/** Ответ сайта явно не про этого преподавателя (адрес API не подошёл). */
class WrongEndpointException : Exception()

fun normalizeSearch(s: String): String = s.lowercase().replace('ё', 'е').trim()

/** Разбор данных о преподавателях из ответов сайта того же формата, что и расписание группы. */
object TeacherParser {
    private val json = Json { isLenient = true }

    private fun departments(lesson: JsonObject): Map<String, String> =
        lesson["divisions"].arr().orEmpty().mapNotNull { d ->
            val o = d.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val name = o["name"].str()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            id to name
        }.toMap()

    /** Преподаватель из объекта сайта {id, lastName, firstName, patronymic, divisionsIds}. */
    fun teacherOf(el: JsonElement, deps: Map<String, String> = emptyMap()): Teacher? {
        val o = el.obj() ?: return null
        val id = o["id"].str()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val full = ScheduleParser.fullName(el) ?: return null
        val short = ScheduleParser.teacherName(el) ?: full
        val divIds = o["divisionsIds"].arr().orEmpty().mapNotNull { it.str() } +
            listOfNotNull(o["divisionId"].str(), o["division"].obj()?.get("id").str())
        val dep = (divIds.mapNotNull { deps[it] }.firstOrNull())
            ?: o["division"].obj()?.get("name").str()
            ?: o["divisionName"].str()
            ?: o["department"].str()
        return Teacher(id, full, short, dep?.trim()?.takeIf { it.isNotEmpty() }, divIds.firstOrNull())
    }

    /** Преподаватели одной пары (с учётом замены). */
    fun teachersOfLesson(lesson: JsonObject): List<Teacher> {
        val deps = departments(lesson)
        val base = lesson["teachers"].arr().orEmpty()
        val repl = lesson["changes"].obj()?.get("teachers").arr().orEmpty()
        return (base + repl).mapNotNull { teacherOf(it, deps) }
    }

    /** Преподаватели из сохранённого JSON пары (поле Lesson.raw). */
    fun teachersFromRaw(raw: String?): List<Teacher> {
        if (raw.isNullOrBlank()) return emptyList()
        val o = runCatching { json.parseToJsonElement(raw).obj() }.getOrNull() ?: return emptyList()
        return teachersOfLesson(o)
    }

    /** Все преподаватели из сохранённых недель группы — без дублей, по алфавиту. */
    fun teachersOfWeeks(weeks: List<WeekSchedule>): List<Teacher> {
        val byId = LinkedHashMap<String, Teacher>()
        for (w in weeks) for (l in w.lessons) for (t in teachersFromRaw(l.raw)) {
            val old = byId[t.id]
            if (old == null || (old.department == null && t.department != null)) byId[t.id] = t
        }
        return byId.values.sortedBy { it.searchKey }
    }

    /**
     * Список преподавателей из ответа сайта неизвестной формы: ищем массивы объектов
     * с фамилией (lastName) или ФИО где угодно внутри ответа.
     */
    fun parseTeacherList(text: String): List<Teacher> {
        val root = ScheduleParser.parseRoot(text)
        val out = LinkedHashMap<String, Teacher>()
        fun walk(el: JsonElement?, depth: Int) {
            if (el == null || depth > 6) return
            when (el) {
                is JsonArray -> el.forEach { item ->
                    val o = item.obj()
                    if (o != null && o["id"] != null &&
                        (o["lastName"] != null || o["fio"] != null || o["fullName"] != null)
                    ) {
                        teacherOf(o)?.let { out.putIfAbsent(it.id, it) }
                    } else walk(item, depth + 1)
                }
                is JsonObject -> el.values.forEach { walk(it, depth + 1) }
                else -> Unit
            }
        }
        walk(root["rows"] ?: root, 0)
        return out.values.sortedBy { it.searchKey }
    }

    /** Разбор недели из ответа сайта: только пары этого преподавателя. */
    fun parseWeek(text: String, teacher: Teacher, monday: LocalDate, now: Long): TeacherWeek {
        val root = ScheduleParser.parseRoot(text)
        val rows = root["rows"].obj() ?: throw WrongEndpointException()
        val days = rows["week"].obj()?.get("weekRussia").obj()?.get("days").arr().orEmpty()
            .mapNotNull { d ->
                val o = d.obj() ?: return@mapNotNull null
                val n = o["weekDayNumber"].int() ?: return@mapNotNull null
                val date = o["date"].str()?.let { parseSiteDate(it) } ?: return@mapNotNull null
                n to date
            }.toMap()
        val orgs = rows["organizations"].arr() ?: throw WrongEndpointException()
        // преподаватель в паре: по id, а если id записан иначе — по ФИО
        fun isHim(el: JsonElement): Boolean =
            el.obj()?.get("id").str() == teacher.id || ScheduleParser.fullName(el) == teacher.fullName
        val all = orgs.mapNotNull { it.obj() }.flatMap { o ->
            val chunks = o["lessonsTimeChunks"].arr().orEmpty().map { it.str().orEmpty() }
            o["lessons"].arr().orEmpty().mapNotNull { it.obj() }.map { it to chunks }
        }
        fun baseOf(lo: JsonObject) = lo["teachers"].arr().orEmpty()
        fun replOf(lo: JsonObject) = lo["changes"].obj()?.get("teachers").arr().orEmpty()
        val mentioned = all.any { (lo, _) -> (baseOf(lo) + replOf(lo)).any(::isHim) }
        val anyTeachers = all.any { (lo, _) -> (baseOf(lo) + replOf(lo)).isNotEmpty() }
        // в ответе только чужие преподаватели — значит, запрос понят не так
        if (all.isNotEmpty() && !mentioned && anyTeachers) throw WrongEndpointException()
        val out = mutableListOf<TeacherLesson>()
        for ((lo, chunks) in all) {
            run {
                val base = baseOf(lo)
                val repl = replOf(lo)
                val effective = repl.ifEmpty { base }
                // в «его» расписании сайт может вовсе не указывать преподавателя — тогда все пары его
                val mine = !mentioned || effective.any(::isHim)
                val replaced = mentioned && !mine && base.any(::isHim)
                if (!mine && !replaced) return@run
                val tc = lo["timeChunks"].arr().orEmpty().mapNotNull { it.int() }
                val start = tc.firstOrNull()?.let { chunks.getOrNull(it) }?.substringBefore("-")?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return@run
                val end = tc.lastOrNull()?.let { chunks.getOrNull(it) }?.substringAfterLast("-")?.trim().orEmpty()
                val wd = lo["weekDayNumber"].int() ?: return@run
                val date = days[wd] ?: monday.plusDays(wd.toLong())
                out += lessonOf(
                    lo, date, start, end, replaced = replaced,
                    substitute = mentioned && mine && repl.isNotEmpty() && base.none(::isHim),
                )
            }
        }
        return TeacherWeek(teacher, monday, merge(out), true, now)
    }

    private fun lessonOf(
        lo: JsonObject, date: LocalDate, start: String, end: String,
        replaced: Boolean, substitute: Boolean,
    ): TeacherLesson {
        val baseRooms = rooms(lo["rooms"])
        val newRooms = rooms(lo["changes"].obj()?.get("rooms"))
        val roomChanged = newRooms.isNotEmpty() && newRooms != baseRooms
        val sub = lo["subgroup"].int()?.takeIf { it > 0 }
        val groups = lo["groups"].arr().orEmpty().mapNotNull { it.obj()?.get("code").str()?.trim() }
            .distinct().joinToString(", ") { code -> if (sub != null) "$code ($sub подгр.)" else code }
        return TeacherLesson(
            date = date,
            start = start,
            end = end,
            subject = lo["course"].obj()?.get("name").str()?.takeIf { it.isNotBlank() }
                ?: lo["type"].str() ?: "Занятие",
            kind = lo["type"].str()?.takeIf { it.isNotBlank() },
            room = (if (roomChanged) newRooms else baseRooms).ifBlank { null },
            groups = groups,
            cancelled = lo["isCanceled"].truthy() || lo["isCancelled"].truthy(),
            moved = lo["isMoved"].truthy(),
            roomChanged = roomChanged,
            replaced = replaced,
            substitute = substitute,
        )
    }

    /** Запасной вариант: пары преподавателя из сохранённых недель своей группы. */
    fun fromWeeks(weeks: List<WeekSchedule>, teacher: Teacher, monday: LocalDate, groupCode: String, now: Long): TeacherWeek {
        val out = mutableListOf<TeacherLesson>()
        for (w in weeks.filter { it.monday == monday }) {
            for (l in w.lessons) {
                val o = runCatching { json.parseToJsonElement(l.raw ?: "").obj() }.getOrNull() ?: continue
                val base = o["teachers"].arr().orEmpty().mapNotNull { it.obj()?.get("id").str() }
                val repl = o["changes"].obj()?.get("teachers").arr().orEmpty().mapNotNull { it.obj()?.get("id").str() }
                val effective = repl.ifEmpty { base }
                val mine = teacher.id in effective
                val replaced = !mine && teacher.id in base
                if (!mine && !replaced) continue
                val date = w.days.firstOrNull { it.weekDayNumber == l.weekDay }?.date?.let { parseSiteDate(it) }
                    ?: w.monday.plusDays(l.weekDay.toLong())
                out += lessonOf(o, date, l.start, l.end, replaced, mine && teacher.id !in base)
                    .let { if (it.groups.isBlank()) it.copy(groups = groupCode) else it }
            }
        }
        return TeacherWeek(teacher, monday, merge(out), false, now)
    }

    /**
     * Поток: у преподавателя одна и та же пара (время, предмет, аудитория) приходит с сайта
     * отдельно для каждой группы — объединяем в одну карточку со списком групп.
     */
    fun merge(list: List<TeacherLesson>): List<TeacherLesson> =
        list.groupBy { listOf(it.date, it.start, it.end, it.subject, it.kind, it.room, it.cancelled, it.moved, it.replaced) }
            .values.map { same ->
                if (same.size == 1) same[0]
                else same[0].copy(
                    groups = same.flatMap { it.groups.split(", ") }.map { it.trim() }.filter { it.isNotEmpty() }
                        .distinct().sorted().joinToString(", "),
                    roomChanged = same.any { it.roomChanged },
                    substitute = same.any { it.substitute },
                )
            }
            .sortedWith(compareBy({ it.date }, { timeKey(it.start) }))

    private fun rooms(el: JsonElement?): String = el.arr().orEmpty().mapNotNull { r ->
        val ro = r.obj() ?: return@mapNotNull r.str()
        (ro["number"].str() ?: ro["name"].str())?.trim()?.takeIf { it.isNotBlank() }
    }.distinct().joinToString(", ")

    /** "21-09-2026" / "2026-09-21" / "21.09.2026" -> дата. */
    fun parseSiteDate(s: String): LocalDate? {
        val p = s.trim().split('-', '.', '/')
        if (p.size != 3) return null
        return runCatching {
            if (p[0].length == 4) LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
            else LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt())
        }.getOrNull()
    }
}

/** Расписание текстом — для кнопки «Скопировать». */
object ScheduleText {
    private val DAYS = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

    private fun dm(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

    private fun roomText(r: String?): String = when {
        r.isNullOrBlank() -> "ауд. —"
        r.first().isDigit() -> "ауд. $r"
        else -> r
    }

    private fun time(start: String, end: String) = if (end.isBlank()) start else "$start–$end"

    /** День студента: «09:00–10:30 · ауд. 1234 · Физика». */
    fun day(date: LocalDate, lessons: List<Lesson>, group: String?): String {
        val head = DAYS[date.dayOfWeek.value - 1] + ", " + dm(date) + (group?.let { " · $it" } ?: "")
        if (lessons.isEmpty()) return "$head\nПар нет"
        val lines = lessons.map { l ->
            val mark = when {
                l.cancelled -> " (отменена)"
                l.moved -> " (перенесена" + (l.movedTo?.let { " на $it" } ?: "") + ")"
                else -> ""
            }
            "${time(l.start, l.end)} · ${roomText(l.room)} · ${l.subject}$mark"
        }
        return (listOf(head) + lines).joinToString("\n")
    }

    /** Неделя преподавателя по дням. */
    fun teacherWeek(w: TeacherWeek): String {
        val sb = StringBuilder(w.teacher.fullName)
        val byDay = w.lessons.groupBy { it.date }.toSortedMap()
        if (byDay.isEmpty()) sb.append("\nНа этой неделе пар нет")
        for ((date, list) in byDay) {
            sb.append("\n\n").append(DAYS[date.dayOfWeek.value - 1]).append(", ").append(dm(date))
            for (l in list.sortedBy { timeKey(it.start) }) {
                val mark = when {
                    l.replaced -> " (замена — ведёт другой преподаватель)"
                    l.cancelled -> " (отменена)"
                    l.moved -> " (перенесена)"
                    else -> ""
                }
                sb.append("\n").append("${time(l.start, l.end)} · ${roomText(l.room)} · ${l.subject}")
                if (l.groups.isNotBlank()) sb.append(" · ").append(l.groups)
                sb.append(mark)
            }
        }
        return sb.toString()
    }
}
