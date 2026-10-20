package com.vadik.raspisanie.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
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
    const val TOOLS = "tools"
    const val MORE = "more"
    /** Только в бете — ради развлечения. */
    const val GAMES = "games"

    /** Разделы, которые можно вынести на нижнюю панель (в этом порядке). */
    val OPTIONAL: List<String>
        get() = listOf(SUBJECTS, TEACHERS, MAP, TOOLS) +
            (if (com.vadik.raspisanie.data.Edition.beta) listOf(GAMES) else emptyList()) + SETTINGS
    const val MAX_EXTRA = 3

    private val ORDER = listOf(SCHEDULE, SUBJECTS, TEACHERS, MAP, TOOLS, GAMES, MORE, SETTINGS)
    fun order(id: String) = ORDER.indexOf(id)

    fun title(id: String) = when (id) {
        SUBJECTS -> "Предметы"
        TEACHERS -> "Преподы"
        MAP -> "Карта"
        SETTINGS -> "Настройки"
        TOOLS -> "Сервис"
        GAMES -> "Игры"
        MORE -> "Другое"
        else -> "Расписание"
    }

    fun fullTitle(id: String) = when (id) {
        TEACHERS -> "Преподаватели"
        MAP -> "Карта кампуса"
        SUBJECTS -> "Предметы и ДЗ"
        TOOLS -> "Копия, календарь, сводка"
        else -> title(id)
    }

    fun subtitle(id: String) = when (id) {
        SUBJECTS -> "Домашние задания и сроки по предметам"
        TEACHERS -> "Где и когда пары у любого преподавателя"
        MAP -> "Корпуса, этажи и где найти аудиторию"
        SETTINGS -> "Группа, уведомления, оформление, виджеты"
        TOOLS -> "Резервная копия, экспорт в календарь, утренняя сводка"
        GAMES -> "Тетрис и симулятор апгрейдера — только в бете, ради развлечения"
        else -> ""
    }

    fun icon(id: String): ImageVector = when (id) {
        SUBJECTS -> Icons.Filled.Edit
        TEACHERS -> Icons.Filled.Person
        MAP -> Icons.Filled.Place
        SETTINGS -> Icons.Filled.Settings
        TOOLS -> Icons.Filled.Build
        GAMES -> Icons.Filled.PlayArrow
        MORE -> Icons.Filled.Menu
        else -> Icons.Filled.DateRange
    }

    /** Варианты формы нижней панели. */
    val SHAPES = listOf("island" to "Островок", "rounded" to "Закруглённая", "auto" to "Как в стиле")

    /** Вкладки снизу: Расписание, выбранные разделы, «Другое» (и «Настройки» — последними, если выбраны). */
    fun bar(prefs: Prefs): List<String> {
        val extra = OPTIONAL.filter { it in prefs.bottomTabs }.take(MAX_EXTRA)
        return listOf(SCHEDULE) + extra.filter { it != SETTINGS } + MORE +
            (if (SETTINGS in extra) listOf(SETTINGS) else emptyList())
    }
}
