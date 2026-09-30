package com.vadik.raspisanie.data

import java.time.LocalDate

/** Утренняя сводка (чистая логика). */
object Morning {
    data class Summary(val title: String, val lines: List<String>)

    /** null — сегодня нечего сообщать (ни пар, ни дел, ни ДЗ). */
    fun build(week: WeekSchedule?, prefs: Prefs, homework: List<Homework>, date: LocalDate): Summary? {
        val all = week?.lessonsOn(date).orEmpty().filter { prefs.concernsMe(it) }
        val lessons = all.filter { it.personalId == null && !it.cancelled && !it.moved }
        val own = all.filter { it.personalId != null }
        val cancelled = all.filter { it.personalId == null && (it.cancelled || it.moved) }
        val hw = homework.filter { !it.done && it.due == date }
        if (lessons.isEmpty() && own.isEmpty() && hw.isEmpty() && cancelled.isEmpty()) return null
        fun room(l: Lesson) = l.room?.let { if (it.first().isDigit()) "ауд. $it" else it } ?: "ауд. —"
        val first = lessons.firstOrNull()
        val title = when {
            first != null -> "Сегодня ${WeekParity.lessonsWord(lessons.size)}, первая в ${first.start} · ${room(first)}"
            own.isNotEmpty() -> "Пар нет, в планах: ${own.first().subject} в ${own.first().start}"
            else -> "Сегодня пар нет"
        }
        val lines = mutableListOf<String>()
        lessons.forEach { l -> lines += "${l.start} · ${room(l)} · ${l.subject}" + (if (l.changed) " (изменения)" else "") }
        cancelled.forEach { l -> lines += "✕ ${l.start} · ${l.subject} — " + if (l.cancelled) "отменена" else "перенесена" }
        own.forEach { l -> lines += "● ${l.start} · ${l.subject}" + (l.room?.let { " · $it" } ?: "") }
        if (hw.isNotEmpty()) lines += "📝 Сдать сегодня: " + hw.joinToString(", ") { it.subject }
        return Summary(title, lines)
    }
}
