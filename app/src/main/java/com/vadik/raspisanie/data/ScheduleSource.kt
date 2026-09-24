package com.vadik.raspisanie.data

import java.time.LocalDate

/** Источник сырых ответов сайта (интерфейс нужен, чтобы логику можно было проверить без сети). */
interface ScheduleSource {
    fun weekJson(date: LocalDate, groupId: String): String
    fun facultiesJson(): String
    fun groupsJson(facultyId: String): String
}
