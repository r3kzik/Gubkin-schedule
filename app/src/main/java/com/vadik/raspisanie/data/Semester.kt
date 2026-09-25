package com.vadik.raspisanie.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Семестр и «какая это пара по счёту» (чистая логика). */
object Semester {

    /** Начало семестра: осенний — с 1 сентября (январь — его сессия), весенний — с 1 февраля. */
    fun start(date: LocalDate): LocalDate = when (date.monthValue) {
        in 9..12 -> LocalDate.of(date.year, 9, 1)
        1 -> LocalDate.of(date.year - 1, 9, 1)
        else -> LocalDate.of(date.year, 2, 1)
    }

    /** Понедельники всех недель семестра до недели с [date] включительно. */
    fun mondays(date: LocalDate): List<LocalDate> {
        val first = start(date).with(DayOfWeek.MONDAY)
        val last = date.with(DayOfWeek.MONDAY)
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /**
     * Номер пары [lesson] ([date]) среди пар того же предмета и того же типа с начала семестра.
     * Отменённые и перенесённые «отсюда» не считаются; пары чужой подгруппы — тоже.
     */
    fun ordinal(weeks: List<WeekSchedule>, date: LocalDate, lesson: Lesson, prefs: Prefs): Int {
        val from = start(date)
        val key = timeKey(lesson.start)
        var n = 0
        for (w in weeks) {
            for (i in 0L until 7L) {
                val d = w.monday.plusDays(i)
                if (d.isBefore(from) || d.isAfter(date)) continue
                for (l in w.lessonsOn(d)) {
                    if (l.subject != lesson.subject || l.kind != lesson.kind) continue
                    if (l.cancelled || l.moved || !prefs.concernsMe(l)) continue
                    if (d == date && timeKey(l.start) > key) continue
                    n++
                }
            }
        }
        // сама пара отменена/перенесена — показываем, какой по счёту она была бы
        if (lesson.cancelled || lesson.moved || !prefs.concernsMe(lesson)) n++
        return n
    }

    /** «Лекция № 5 в семестре». */
    fun label(kind: String, n: Int): String = "${kind.replaceFirstChar { it.uppercase() }} № $n в семестре"
}
