package com.vadik.raspisanie.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Результат обновления недели: свежие данные и список изменений относительно сохранённой версии. */
data class RefreshResult(val week: WeekSchedule, val changes: List<String>)

/**
 * Вся работа с расписанием: сеть + сохранённые копии.
 * Методы блокирующие — вызывать из фонового потока (Dispatchers.IO / WorkManager).
 */
class Repository(
    private val source: ScheduleSource,
    private val storage: Storage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun settings(): Settings? = storage.loadSettings()
    fun saveSettings(s: Settings) = storage.saveSettings(s)

    fun cachedWeek(groupId: String, date: LocalDate): WeekSchedule? =
        storage.loadWeek(groupId, mondayOf(date))

    fun prefs(): Prefs = storage.loadPrefs()
    fun savePrefs(p: Prefs) = storage.savePrefs(p)
    fun rawFile() = storage.rawFile()

    /** Изменения этой пары, замеченные приложением (новые сверху). */
    fun historyFor(date: LocalDate, subject: String): List<Change> =
        storage.loadHistory().filter { it.date == date && it.subject == subject }.sortedByDescending { it.noticedAt }

    @Synchronized
    fun refreshWeek(settings: Settings, date: LocalDate, today: LocalDate = LocalDate.now()): RefreshResult {
        val monday = mondayOf(date)
        val text = source.weekJson(monday, settings.groupId)
        val fresh = ScheduleParser.parseWeek(text, settings.groupId, monday, clock(), settings.groupName)
        storage.saveRaw(text)
        val old = storage.loadWeek(settings.groupId, monday)
        // Защита: если сайт вдруг отдал пустую неделю при непустой сохранённой,
        // не затираем её молча (это чаще сбой сайта, чем реальная отмена всех пар).
        if (fresh.lessons.isEmpty() && old != null && old.lessons.isNotEmpty() && fresh.days.isEmpty()) {
            throw SiteException("Сайт вернул пустое расписание — показано сохранённое")
        }
        val prefs = storage.loadPrefs()
        val changes = ScheduleDiff.changes(old, fresh, today, { prefs.concernsMe(it) }, clock())
        storage.saveWeek(fresh)
        storage.appendHistory(changes)
        return RefreshResult(fresh, changes.map { it.line })
    }

    // ---------------------------------------------------------------- ДЗ и предметы

    fun homework(): List<Homework> = storage.loadHomework()

    @Synchronized
    fun upsertHomework(h: Homework): List<Homework> {
        val list = storage.loadHomework().filter { it.id != h.id } + h
        storage.saveHomework(list)
        return list
    }

    @Synchronized
    fun deleteHomework(id: String): List<Homework> {
        val list = storage.loadHomework().filter { it.id != id }
        storage.saveHomework(list)
        return list
    }

    /**
     * Предметы из сохранённых недель: название, ближайшая пара (после [after]),
     * типы занятий и преподаватели. Отменённые/перенесённые и чужие подгруппы не считаются.
     */
    fun subjects(groupId: String, prefs: Prefs, after: LocalDate = LocalDate.now()): List<SubjectInfo> {
        data class Occ(val date: LocalDate, val lesson: Lesson)
        val occ = mutableListOf<Occ>()
        for (w in storage.allWeeks(groupId)) {
            for (i in 0L until 7L) {
                val d = w.monday.plusDays(i)
                w.lessonsOn(d).forEach { occ += Occ(d, it) }
            }
        }
        return occ.groupBy { it.lesson.subject }.map { (name, list) ->
            val next = list
                .filter { it.date.isAfter(after) && !it.lesson.cancelled && !it.lesson.moved && prefs.concernsMe(it.lesson) }
                .minWithOrNull(compareBy({ it.date }, { timeKey(it.lesson.start) }))
            SubjectInfo(
                name = name,
                nextDate = next?.date,
                nextStart = next?.lesson?.start,
                kinds = list.mapNotNull { it.lesson.kind }.distinct(),
                teachers = list.mapNotNull { it.lesson.teacher }.distinct(),
            )
        }.sortedWith(compareBy({ it.nextDate ?: LocalDate.MAX }, { it.name }))
    }

    // ------------------------------------------------------------ номер пары в семестре

    /** Номер пары и полон ли подсчёт (все ли недели семестра удалось загрузить). */
    data class Ordinal(val number: Int, val complete: Boolean)

    /**
     * Какая по счёту эта пара (лекция, семинар…) в семестре. Недостающие прошлые недели
     * один раз скачиваются с сайта и сохраняются — дальше считается мгновенно.
     */
    fun lessonOrdinal(settings: Settings, date: LocalDate, lesson: Lesson, pause: (Long) -> Unit = { Thread.sleep(it) }): Ordinal? {
        if (lesson.kind.isNullOrBlank()) return null
        val weeks = mutableListOf<WeekSchedule>()
        var complete = true
        var fetched = 0
        for (m in Semester.mondays(date)) {
            val cached = storage.loadWeek(settings.groupId, m)
            if (cached != null) {
                weeks += cached
                continue
            }
            if (!complete) continue // сайт уже отказал — остальные не дёргаем
            try {
                if (fetched > 0) pause(700) // не частим запросами
                val text = source.weekJson(m, settings.groupId)
                val w = ScheduleParser.parseWeek(text, settings.groupId, m, clock(), settings.groupName)
                storage.saveWeek(w)
                weeks += w
                fetched++
            } catch (e: Exception) {
                complete = false
            }
        }
        return Ordinal(Semester.ordinal(weeks, date, lesson, storage.loadPrefs()), complete)
    }

    // ------------------------------------------------------------ преподаватели

    /** Преподаватели своей группы — из сохранённых недель, работает и без сети. */
    fun myTeachers(groupId: String): List<Teacher> = TeacherParser.teachersOfWeeks(storage.allWeeks(groupId))

    /** Порядок вариантов адреса: сначала тот, что уже сработал; пустой — если недавно не подошёл ни один. */
    private fun variantsToTry(key: String, count: Int): List<Int> {
        val probe = storage.loadProbe()
        val known = probe[key]
        val at = probe["${key}At"] ?: 0L
        return when {
            count == 0 -> emptyList()
            known != null && known >= 0 && known < count -> listOf(known.toInt())
            known == -1L && clock() - at < PROBE_RETRY_MS -> emptyList()
            else -> (0 until count).toList()
        }
    }

    private fun remember(key: String, variant: Int) {
        storage.saveProbe(storage.loadProbe() + mapOf(key to variant.toLong(), "${key}At" to clock()))
    }

    /** Полный список преподавателей вуза (раз в неделю с сайта) или null, если сайт его не отдаёт. */
    fun allTeachers(force: Boolean = false): List<Teacher>? {
        val cached = storage.loadTeachers()
        if (!force && cached != null && clock() - cached.second < TEACHERS_TTL_MS) return cached.first
        var networkFailed = false
        for (v in variantsToTry("list", source.teacherListVariants)) {
            try {
                val list = TeacherParser.parseTeacherList(source.teachersJson(v))
                if (list.size >= 5) {
                    storage.saveTeachers(list, clock())
                    remember("list", v)
                    return list
                }
            } catch (e: CaptchaRequiredException) {
                throw e
            } catch (e: java.io.IOException) {
                networkFailed = true
            } catch (e: Exception) {
                // этот адрес не подошёл — пробуем следующий
            }
        }
        if (!networkFailed && cached == null) remember("list", -1)
        return cached?.first
    }

    /**
     * Неделя преподавателя: полное расписание с сайта, а если не получилось —
     * его пары из сохранённого расписания своей группы.
     */
    fun teacherWeek(teacher: Teacher, date: LocalDate, settings: Settings): TeacherWeek {
        val monday = mondayOf(date)
        var networkFailed = false
        for (v in variantsToTry("week", source.teacherWeekVariants)) {
            try {
                val w = TeacherParser.parseWeek(source.teacherWeekJson(monday, teacher.id, teacher.divisionId, v), teacher, monday, clock())
                remember("week", v)
                return w
            } catch (e: CaptchaRequiredException) {
                throw e
            } catch (e: java.io.IOException) {
                networkFailed = true
                break
            } catch (e: Exception) {
                // адрес не подошёл
            }
        }
        if (!networkFailed && storage.loadProbe()["week"]?.let { it >= 0 } != true) remember("week", -1)
        return TeacherParser.fromWeeks(storage.allWeeks(settings.groupId), teacher, monday, settings.groupName, clock())
    }

    fun faculties(): List<Faculty> = ScheduleParser.parseFaculties(source.facultiesJson())

    fun groups(facultyId: String): List<Group> = ScheduleParser.parseGroups(source.groupsJson(facultyId))

    /**
     * Автоматический поиск группы: факультет по подстроке названия, группа по коду.
     * Возвращает null, если не нашлось — тогда пользователь выберет вручную.
     */
    fun findGroup(facultyHint: String, groupCode: String): Settings? {
        val wanted = normalizeCode(groupCode)
        val all = faculties()
        val preferred = all.filter { it.name.contains(facultyHint, ignoreCase = true) }
        for (f in preferred) {
            val g = groups(f.id).firstOrNull { normalizeCode(it.code) == wanted } ?: continue
            return Settings(g.id, g.code, f.name)
        }
        return null
    }

    companion object {
        private const val TEACHERS_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val PROBE_RETRY_MS = 24 * 60 * 60 * 1000L

        fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

        private val latinToCyr = mapOf(
            'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
            'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
        )

        /** "кв 26-02", "KB-26-02", "КВ–26–02" -> "КВ-26-02" */
        fun normalizeCode(s: String): String = s.uppercase()
            .map { latinToCyr[it] ?: it }
            .joinToString("")
            .replace('–', '-').replace('—', '-')
            .filterNot { it.isWhitespace() }
    }
}
