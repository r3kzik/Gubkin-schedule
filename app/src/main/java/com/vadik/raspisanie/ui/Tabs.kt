package com.vadik.raspisanie.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.vadik.raspisanie.data.Prefs

/** Разделы приложения и нижняя панель, которую пользователь настраивает сам. */
object Tabs {
    const val SCHEDULE = "schedule"
    const val SUBJECTS = "subjects"
    const val TEACHERS = "teachers"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val MORE = "more"

    /** Разделы, которые можно вынести на нижнюю панель (в этом порядке). */
    val OPTIONAL = listOf(SUBJECTS, TEACHERS, MAP, SETTINGS)
    const val MAX_EXTRA = 3

    private val ORDER = listOf(SCHEDULE, SUBJECTS, TEACHERS, MAP, MORE, SETTINGS)
    fun order(id: String) = ORDER.indexOf(id)

    fun title(id: String) = when (id) {
        SUBJECTS -> "Предметы"
        TEACHERS -> "Преподы"
        MAP -> "Карта"
        SETTINGS -> "Настройки"
        MORE -> "Другое"
        else -> "Расписание"
    }

    fun fullTitle(id: String) = when (id) {
        TEACHERS -> "Преподаватели"
        MAP -> "Карта кампуса"
        SUBJECTS -> "Предметы и ДЗ"
        else -> title(id)
    }

    fun subtitle(id: String) = when (id) {
        SUBJECTS -> "Домашние задания и сроки по предметам"
        TEACHERS -> "Где и когда пары у любого преподавателя"
        MAP -> "Корпуса, этажи и где найти аудиторию"
        SETTINGS -> "Группа, уведомления, оформление, виджеты"
        else -> ""
    }

    fun icon(id: String): ImageVector = when (id) {
        SUBJECTS -> Icons.Filled.Edit
        TEACHERS -> Icons.Filled.Person
        MAP -> Icons.Filled.Place
        SETTINGS -> Icons.Filled.Settings
        MORE -> Icons.Filled.Menu
        else -> Icons.Filled.DateRange
    }

    /** Вкладки снизу: Расписание, выбранные разделы, «Другое» (и «Настройки» — последними, если выбраны). */
    fun bar(prefs: Prefs): List<String> {
        val extra = OPTIONAL.filter { it in prefs.bottomTabs }.take(MAX_EXTRA)
        return listOf(SCHEDULE) + extra.filter { it != SETTINGS } + MORE +
            (if (SETTINGS in extra) listOf(SETTINGS) else emptyList())
    }
}
