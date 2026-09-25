package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Что показать в шторке прямо сейчас (чистая логика, без Android). */
object NowStatus {

    sealed class State {
        /** Идёт пара: [lessons] начались в [start] и закончатся в [end]. */
        data class InLesson(val lessons: List<Lesson>, val start: LocalDateTime, val end: LocalDateTime, val next: Lesson?) : State()
        /** Перемена или скоро первая пара: следующая [lessons] начнётся в [start]. */
        data class Before(val lessons: List<Lesson>, val start: LocalDateTime, val first: Boolean) : State()
        object Nothing : State()
    }

    /** За сколько минут до первой пары начинаем показывать «скоро пара». */
    const val LEAD_MINUTES = 30

    fun at(week: WeekSchedule?, prefs: Prefs, now: LocalDateTime): State {
        val today: LocalDate = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val lessons = Upcoming.activeLessons(week, today, prefs)
        if (lessons.isEmpty()) return State.Nothing
        val groups = lessons.groupBy { it.start }.toSortedMap(compareBy { timeKey(it) })
            .map { (s, ls) -> Triple(timeKey(s), ls.maxOf { timeKey(it.end.ifBlank { it.start }) }, ls) }
        fun time(m: Int) = today.atTime(m / 60, m % 60)
        groups.forEachIndexed { i, (s, e, ls) ->
            if (nowMin in s until e) return State.InLesson(ls, time(s), time(e), groups.getOrNull(i + 1)?.third?.first())
        }
        val firstStart = groups.first().first
        val lastEnd = groups.maxOf { it.second }
        val next = groups.firstOrNull { it.first > nowMin } ?: return State.Nothing
        return when {
            nowMin >= firstStart && nowMin < lastEnd -> State.Before(next.third, time(next.first), first = false)
            nowMin < firstStart && firstStart - nowMin <= LEAD_MINUTES -> State.Before(next.third, time(next.first), first = true)
            else -> State.Nothing
        }
    }

    /** Когда перерисовать уведомление: ближайшая граница пары (или начало показа перед первой). */
    fun nextCheck(week: WeekSchedule?, prefs: Prefs, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val lessons = Upcoming.activeLessons(week, today, prefs)
        val marks = lessons.flatMap { listOf(timeKey(it.start), timeKey(it.end.ifBlank { it.start })) } +
            listOfNotNull(lessons.minOfOrNull { timeKey(it.start) }?.minus(LEAD_MINUTES))
        val m = marks.filter { it > nowMin && it < 24 * 60 }.minOrNull() ?: return null
        return today.atTime(m / 60, m % 60)
    }
}
