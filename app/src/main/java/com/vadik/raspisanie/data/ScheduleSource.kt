package com.vadik.raspisanie.data

import java.time.LocalDate

/** Источник сырых ответов сайта (интерфейс нужен, чтобы логику можно было проверить без сети). */
interface ScheduleSource {
    fun weekJson(date: LocalDate, groupId: String): String
    fun facultiesJson(): String
    fun groupsJson(facultyId: String): String

    /** Неделя преподавателя. variant — какой из возможных адресов API попробовать. */
    fun teacherWeekJson(date: LocalDate, teacherId: String, divisionId: String?, variant: Int): String =
        throw UnsupportedOperationException()

    /** Список всех преподавателей. */
    fun teachersJson(variant: Int): String = throw UnsupportedOperationException()

    val teacherWeekVariants: Int get() = 0
    val teacherListVariants: Int get() = 0
}
