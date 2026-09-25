package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Подписи сроков ДЗ для виджета и списков (чистая логика). */
object HomeworkDue {
    private val SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    enum class Urgency { OVERDUE, TODAY, TOMORROW, SOON, LATER, NONE }

    fun urgency(due: LocalDate?, today: LocalDate): Urgency {
        if (due == null) return Urgency.NONE
        val d = ChronoUnit.DAYS.between(today, due)
        return when {
            d < 0 -> Urgency.OVERDUE
            d == 0L -> Urgency.TODAY
            d == 1L -> Urgency.TOMORROW
            d <= 6 -> Urgency.SOON
            else -> Urgency.LATER
        }
    }

    /** «просрочено», «сегодня», «завтра», «пт», «03.10», «без срока». */
    fun label(due: LocalDate?, today: LocalDate): String = when (urgency(due, today)) {
        Urgency.OVERDUE -> "просрочено"
        Urgency.TODAY -> "сегодня"
        Urgency.TOMORROW -> "завтра"
        Urgency.SOON -> SHORT[due!!.dayOfWeek.value - 1] + " " + "%02d.%02d".format(due.dayOfMonth, due.monthValue)
        Urgency.LATER -> "%02d.%02d".format(due!!.dayOfMonth, due.monthValue)
        Urgency.NONE -> "без срока"
    }

    /** Невыполненные задания: сначала просроченные и ближайшие, без срока — в конце. */
    fun pending(all: List<Homework>): List<Homework> =
        all.filter { !it.done }.sortedWith(compareBy({ it.due == null }, { it.due }, { it.createdAt }))
}
