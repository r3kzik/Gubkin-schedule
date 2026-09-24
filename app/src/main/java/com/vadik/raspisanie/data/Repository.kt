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
