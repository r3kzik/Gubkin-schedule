package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Ближайшие пары: для напоминаний и виджета (чистая логика, без Android). */
object Upcoming {

    data class Reminder(
        val at: LocalDateTime,
        val start: LocalDateTime,
        val lessons: List<Lesson>,
    )

    data class WidgetDay(
        val date: LocalDate,
        val lessons: List<Lesson>,
        /** Индекс пары, которая идёт сейчас, или -1. */
        val currentIndex: Int,
        /** false — расписание на этот день ещё не загружено. */
        val known: Boolean = true,
    )

    fun parseTime(s: String): LocalTime? {
        val k = timeKey(s)
        return if (k >= 24 * 60) null else LocalTime.of(k / 60, k % 60)
    }

    /** Пары дня, которые касаются меня и реально состоятся. */
    fun activeLessons(week: WeekSchedule?, date: LocalDate, prefs: Prefs): List<Lesson> =
        week?.lessonsOn(date).orEmpty().filter { !it.cancelled && !it.moved && prefs.concernsMe(it) }

    /**
     * Следующее напоминание строго позже [now]. Пары с одинаковым временем начала
     * объединяются в одно уведомление.
     */
    fun nextReminder(weeks: List<WeekSchedule>, prefs: Prefs, now: LocalDateTime): Reminder? {
        var best: Reminder? = null
        for (w in weeks) {
            for (i in 0L until 7L) {
                val date = w.monday.plusDays(i)
                if (date.isBefore(now.toLocalDate())) continue
                val byStart = activeLessons(w, date, prefs).groupBy { it.start }
                for ((startStr, lessons) in byStart) {
                    val t = parseTime(startStr) ?: continue
                    val start = date.atTime(t)
                    val at = start.minusMinutes(prefs.remindMinutes.toLong())
                    if (!at.isAfter(now)) continue
                    if (best == null || at.isBefore(best.at)) best = Reminder(at, start, lessons)
                }
            }
        }
        return best
    }

    /**
     * Что показать на виджете: сегодняшние пары, пока они не закончились,
     * а после последней пары — ближайший следующий день (до недели вперёд).
     */
    fun widgetDay(weekOf: (LocalDate) -> WeekSchedule?, prefs: Prefs, now: LocalDateTime): WidgetDay {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val todayLessons = visible(weekOf(today), today, prefs)
        val lastEnd = todayLessons.filter { !it.cancelled && !it.moved }
            .maxOfOrNull { timeKey(it.end.ifBlank { it.start }) }
        if (todayLessons.isNotEmpty() && lastEnd != null && nowMin < lastEnd) {
            val cur = todayLessons.indexOfFirst {
                !it.cancelled && !it.moved &&
                    nowMin >= timeKey(it.start) && nowMin < timeKey(it.end.ifBlank { it.start })
            }
            return WidgetDay(today, todayLessons, cur)
        }
        if (weekOf(today) == null) return WidgetDay(today, emptyList(), -1, known = false)
        for (d in 1L..7L) {
            val date = today.plusDays(d)
            val week = weekOf(date) ?: return WidgetDay(date, emptyList(), -1, known = false)
            val lessons = visible(week, date, prefs)
            if (lessons.isNotEmpty()) return WidgetDay(date, lessons, -1)
        }
        return WidgetDay(today.plusDays(1), emptyList(), -1)
    }

    private fun visible(week: WeekSchedule?, date: LocalDate, prefs: Prefs) =
        week?.lessonsOn(date).orEmpty().filter { prefs.shows(it) }
}
