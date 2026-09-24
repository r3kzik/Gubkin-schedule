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
    /** Номер подгруппы (1, 2…), если пара только для части группы; null — для всей группы. */
    val subgroup: Int? = null,
    /** Полные ФИО преподавателей. */
    val teacherFull: String? = null,
    /** Что изменилось — со слов сайта (поле changes и т. п.). */
    val changeLines: List<String> = emptyList(),
    /** Исходные данные пары с сайта (JSON) — для экрана подробностей. */
    val raw: String? = null,
    /** Аудитория заменена (показывать красным, как на сайте); oldRoom — какая была по расписанию. */
    val roomChanged: Boolean = false,
    val oldRoom: String? = null,
    /** Преподаватель заменён; oldTeacher — кто был по расписанию (если сайт его указал). */
    val teacherChanged: Boolean = false,
    val oldTeacher: String? = null,
    /** «18.09 в 10:15» — откуда перенесена эта пара / куда перенесена отменённая. */
    val movedFrom: String? = null,
    val movedTo: String? = null,
    /** Доп. информация к паре и кафедра. */
    val info: String? = null,
    val department: String? = null,
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
        val wd = if (days.isEmpty()) date.dayOfWeek.value - 1 // на сайте понедельник = 0
        else days.firstOrNull { it.date == key }?.weekDayNumber ?: return emptyList()
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

/** Настройки приложения. */
data class Prefs(
    /** 0 — все подгруппы, 1 или 2 — своя подгруппа. */
    val subgroup: Int = 0,
    /** true — пары другой подгруппы скрыты, false — показаны бледными. */
    val hideOtherSubgroup: Boolean = true,
    val remindEnabled: Boolean = true,
    val remindMinutes: Int = 10,
    val changeNotify: Boolean = true,
    /** system | light | dark */
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    /** Непрозрачность фона виджета, 0–100 %. */
    val widgetOpacity: Int = 100,
    /** Тема виджета: system | light | dark */
    val widgetTheme: String = "system",
    /** Цвет интерфейса: dynamic (обои, Android 12+) или один из пресетов (blue, violet, …). */
    val accent: String = "blue",
    /** Анимированный «северное сияние» фон. */
    val animatedBackground: Boolean = true,
) {
    /** Пара другой подгруппы (не моей). */
    fun isOtherSubgroup(l: Lesson): Boolean =
        subgroup != 0 && l.subgroup != null && l.subgroup != subgroup

    /** Показывать ли пару с учётом выбранной подгруппы. */
    fun shows(l: Lesson): Boolean = !(hideOtherSubgroup && isOtherSubgroup(l))

    /** Касается ли пара меня (для уведомлений и виджета). */
    fun concernsMe(l: Lesson): Boolean = !isOtherSubgroup(l)
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
