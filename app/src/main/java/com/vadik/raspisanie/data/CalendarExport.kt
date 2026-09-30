package com.vadik.raspisanie.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Одно событие для календаря. */
data class CalEvent(
    val uid: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String?,
    val description: String,
)

/** Пары и свои дела → события календаря (чистая логика) и файл .ics. */
object CalendarExport {
    /** Метка в описании — по ней приложение находит и обновляет свои события. */
    const val TAG = "#MyGub"

    fun events(weeks: List<WeekSchedule>, prefs: Prefs, from: LocalDate): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        for (w in weeks) {
            for (i in 0L until 7L) {
                val d = w.monday.plusDays(i)
                if (d.isBefore(from)) continue
                for (l in w.lessonsOn(d)) {
                    if (l.cancelled || l.moved || !prefs.concernsMe(l)) continue
                    val s = Upcoming.parseTime(l.start) ?: continue
                    val e = Upcoming.parseTime(l.end) ?: s.plusMinutes(90)
                    val place = listOfNotNull(
                        l.room?.let { r -> if (r.first().isDigit()) "ауд. $r" else r },
                        Campus.locate(l.room)?.summary,
                    ).joinToString(", ").ifBlank { null }
                    val title = if (l.personalId != null) l.subject
                    else l.subject + (l.kind?.let { " ($it)" } ?: "")
                    val desc = listOfNotNull(
                        l.teacherFull ?: l.teacher,
                        l.info,
                        l.changeLines.takeIf { it.isNotEmpty() }?.joinToString("; "),
                        TAG,
                    ).joinToString("\n")
                    out += CalEvent(
                        uid = "${d}-${l.start}-${l.subject.hashCode()}@mygub",
                        title = title,
                        start = d.atTime(s),
                        end = d.atTime(e),
                        location = place,
                        description = desc,
                    )
                }
            }
        }
        return out.distinctBy { it.uid }.sortedBy { it.start }
    }

    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun utc(t: LocalDateTime, zone: ZoneId) =
        ZonedDateTime.of(t, zone).withZoneSameInstant(ZoneId.of("UTC")).format(UTC)

    /** Файл .ics — его понимают Google Календарь (через импорт), Samsung, Xiaomi и другие. */
    fun ics(events: List<CalEvent>, zone: ZoneId = ZoneId.systemDefault(), now: LocalDateTime = LocalDateTime.now()): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//MyGub//Raspisanie//RU\r\nCALSCALE:GREGORIAN\r\nX-WR-CALNAME:MyGub\r\n")
        for (e in events) {
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:").append(e.uid).append("\r\n")
            sb.append("DTSTAMP:").append(utc(now, zone)).append("\r\n")
            sb.append("DTSTART:").append(utc(e.start, zone)).append("\r\n")
            sb.append("DTEND:").append(utc(e.end, zone)).append("\r\n")
            sb.append("SUMMARY:").append(esc(e.title)).append("\r\n")
            e.location?.let { sb.append("LOCATION:").append(esc(it)).append("\r\n") }
            sb.append("DESCRIPTION:").append(esc(e.description)).append("\r\n")
            sb.append("END:VEVENT\r\n")
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }
}
