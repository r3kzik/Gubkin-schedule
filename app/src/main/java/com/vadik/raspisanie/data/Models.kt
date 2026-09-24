package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Одна пара. weekDay — номер дня недели так, как его отдаёт сайт (1 = понедельник). */
data class Lesson(
    val weekDay: Int,
    val start: String,
    val end: String,
    val subject: String,
    val kind: String?,
    val room: String?,
    val teacher: String?,
    val cancelled: Boolean,
    val moved: Boolean,
    val changed: Boolean,
)

/** День недели из ответа сайта: дата в формате dd-MM-yyyy и номер дня. */
data class WeekDay(val date: String, val weekDayNumber: Int)

data class WeekSchedule(
    val groupId: String,
    val monday: LocalDate,
    val weekType: String?,
    val days: List<WeekDay>,
    val lessons: List<Lesson>,
    val fetchedAt: Long,
) {
    /** Пары на конкретную дату, отсортированные по времени. */
    fun lessonsOn(date: LocalDate): List<Lesson> {
        val key = date.format(SITE_DATE)
        val wd = days.firstOrNull { it.date == key }?.weekDayNumber
            ?: date.dayOfWeek.value
        return lessons.filter { it.weekDay == wd }.sortedBy { timeKey(it.start) }
    }

    val weekTypeLabel: String?
        get() = when (weekType?.lowercase()) {
            "upper" -> "верхняя неделя"
            "lower" -> "нижняя неделя"
            null, "" -> null
            else -> weekType
        }

    companion object {
        val SITE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

data class Faculty(val id: String, val name: String)
data class Group(val id: String, val code: String)

data class Settings(
    val groupId: String,
    val groupName: String,
    val facultyName: String,
)

/** "08:45" -> 525 (минуты от полуночи); мусор уходит в конец. */
fun timeKey(s: String): Int {
    val parts = s.trim().split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return 99 * 60
    val m = parts.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: 0
    return h * 60 + m
}

/** Сайт требует капчу: нужно показать картинку пользователю. */
class CaptchaRequiredException : Exception("Сайт просит ввести капчу")

/** Сайт ответил, но не так, как ожидалось (ошибка сервера, блокировка, отказ). */
class SiteException(message: String) : Exception(message)
