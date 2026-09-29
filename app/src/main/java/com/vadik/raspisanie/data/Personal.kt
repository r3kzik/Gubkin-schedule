package com.vadik.raspisanie.data

import java.time.LocalDate

/** Своё дело / своя пара, которую пользователь добавил сам. */
data class PersonalEvent(
    val id: String,
    val title: String,
    /** Дата (для повторяющихся — первая дата). */
    val date: LocalDate,
    /** «09:30» */
    val start: String,
    val end: String,
    val place: String? = null,
    val note: String? = null,
    /** none | daily | weekly */
    val repeat: String = "none",
) {
    fun occursOn(d: LocalDate): Boolean = when (repeat) {
        "daily" -> !d.isBefore(date)
        "weekly" -> !d.isBefore(date) && d.dayOfWeek == date.dayOfWeek
        else -> d == date
    }
}

/** Свои дела в расписании и перемены между парами (чистая логика). */
object Personal {
    const val KIND = "Своё"

    /** Своё дело как «пара» — чтобы его видели список дня, виджеты, напоминания и шторка. */
    fun asLesson(e: PersonalEvent, weekDay: Int) = Lesson(
        weekDay = weekDay,
        start = e.start,
        end = e.end,
        subject = e.title,
        kind = KIND,
        room = e.place?.takeIf { it.isNotBlank() },
        teacher = null,
        cancelled = false,
        moved = false,
        changed = false,
        info = e.note?.takeIf { it.isNotBlank() },
        personalId = e.id,
    )

    /** Неделя с добавленными своими делами. Если с сайта недели ещё нет — неделя только из своих дел. */
    fun merge(week: WeekSchedule?, monday: LocalDate, groupId: String, events: List<PersonalEvent>): WeekSchedule? {
        if (events.isEmpty()) return week
        val extra = (0L until 7L).flatMap { i ->
            val d = monday.plusDays(i)
            val key = d.format(WeekSchedule.SITE_DATE)
            val wd = week?.days?.takeIf { it.isNotEmpty() }?.let { days -> days.firstOrNull { it.date == key }?.weekDayNumber }
                ?: (d.dayOfWeek.value - 1)
            events.filter { it.occursOn(d) }.map { asLesson(it, wd) }
        }
        if (extra.isEmpty()) return week
        val base = week ?: WeekSchedule(groupId, monday, null, emptyList(), emptyList(), 0L)
        return base.copy(lessons = (base.lessons + extra).sortedWith(compareBy({ it.weekDay }, { timeKey(it.start) })))
    }

    // ------------------------------------------------------------ перемены

    /** Перемена между парами: [kind] — break | lunch | window. */
    data class Gap(val start: String, val end: String, val minutes: Int, val kind: String) {
        val title: String
            get() = when (kind) {
                "lunch" -> "Обед"
                "window" -> "Окно"
                else -> "Перемена"
            }
    }

    private const val LUNCH_FROM = 13 * 60 + 30
    private const val LUNCH_TO = 14 * 60
    /** Промежуток длиннее — это уже «окно», а не перемена. */
    private const val WINDOW_MIN = 50

    private fun hm(m: Int) = "%d:%02d".format(m / 60, m % 60)

    /**
     * Перемены между соседними парами (с учётом параллельных пар разных подгрупп).
     * Промежуток 13:30–14:00 — «Обед»; больше 50 минут — «Окно».
     * Возвращает пары в том же порядке, где после индекса i стоит перемена gaps[i] (если есть).
     */
    fun gapsAfter(lessons: List<Lesson>): Map<Int, Gap> {
        val out = mutableMapOf<Int, Gap>()
        var maxEnd = -1
        for (i in lessons.indices) {
            val l = lessons[i]
            maxEnd = maxOf(maxEnd, timeKey(l.end.ifBlank { l.start }))
            if (i == lessons.lastIndex) break
            val nextStart = timeKey(lessons[i + 1].start)
            if (maxEnd <= 0 || nextStart <= maxEnd || nextStart >= 24 * 60) continue
            val minutes = nextStart - maxEnd
            val kind = when {
                maxEnd <= LUNCH_FROM && nextStart >= LUNCH_TO && minutes <= WINDOW_MIN -> "lunch"
                minutes > WINDOW_MIN -> "window"
                else -> "break"
            }
            out[i] = Gap(hm(maxEnd), hm(nextStart), minutes, kind)
        }
        return out
    }

}
