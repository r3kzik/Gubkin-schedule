package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Сравнивает старую и новую версию недели и описывает изменения по-человечески. */
object ScheduleDiff {

    private val dayFmt = DateTimeFormatter.ofPattern("EE dd.MM", Locale.forLanguageTag("ru"))

    fun describe(
        old: WeekSchedule?,
        new: WeekSchedule,
        today: LocalDate,
        relevant: (Lesson) -> Boolean = { true },
    ): List<String> {
        if (old == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0L until 7L) {
            val date = new.monday.plusDays(i)
            if (date.isBefore(today)) continue // прошедшие дни не интересны
            val before = old.lessonsOn(date).filter(relevant)
            val after = new.lessonsOn(date).filter(relevant)
            if (before == after) continue
            val dayLabel = date.format(dayFmt).replaceFirstChar { it.uppercase() }
            out += describeDay(dayLabel, before, after)
        }
        return out
    }

    private fun describeDay(day: String, before: List<Lesson>, after: List<Lesson>): List<String> {
        val out = mutableListOf<String>()
        val remaining = before.toMutableList()
        val unmatchedNew = mutableListOf<Lesson>()

        // 1) точные совпадения — без изменений
        for (n in after) {
            val same = remaining.firstOrNull { it == n }
            if (same != null) remaining.remove(same) else unmatchedNew += n
        }
        // 2) та же пара (предмет + время или предмет + тип) с изменёнными деталями
        val added = mutableListOf<Lesson>()
        for (n in unmatchedNew) {
            val o = remaining.firstOrNull { it.subject == n.subject && it.start == n.start }
                ?: remaining.firstOrNull { it.subject == n.subject && it.kind == n.kind }
            if (o == null) {
                added += n
                continue
            }
            remaining.remove(o)
            val parts = mutableListOf<String>()
            if (!o.cancelled && n.cancelled) parts += "ОТМЕНЕНА"
            if (o.cancelled && !n.cancelled) parts += "снова в расписании"
            if (!o.moved && n.moved) parts += "перенесена"
            if (o.start != n.start || o.end != n.end) parts += "время ${o.start} → ${n.start}"
            if (o.room != n.room) parts += "ауд. ${o.room ?: "—"} → ${n.room ?: "—"}"
            if (o.subgroup != n.subgroup) parts += "подгруппа ${o.subgroup ?: "вся группа"} → ${n.subgroup ?: "вся группа"}"
            if (o.teacher != n.teacher) parts += "преп. ${o.teacher ?: "—"} → ${n.teacher ?: "—"}"
            if (parts.isNotEmpty()) out += "$day, ${n.start} ${n.subject}: ${parts.joinToString(", ")}"
        }
        for (n in added) {
            out += "$day: добавлена пара ${n.start} ${n.subject}" +
                (n.room?.let { ", ауд. $it" } ?: "")
        }
        for (o in remaining) {
            out += "$day: убрана пара ${o.start} ${o.subject}"
        }
        return out
    }
}
