package com.vadik.raspisanie.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalDate

/**
 * Разбор ответов API расписания lk.gubkin.ru/schedule/api/api.php.
 *
 * Формат (act=schedule):
 * { "state": true, "rows": {
 *     "week": { "weekRussia": { "type": "upper|lower",
 *                               "days": [ {"date": "22-09-2026", "weekDayNumber": 1}, ... ] } },
 *     "organizations": [ {
 *         "lessonsTimeChunks": ["08:45-09:30", "09:30-10:15", ...],
 *         "lessons": [ { "weekDayNumber": 1, "timeChunks": [0, 1],
 *                        "course": {"name": "..."}, "type": "Лекция",
 *                        "rooms": [{"number": "1234"}], "teachers": [{"lastName": "..."}],
 *                        "groups": [{"id": 10706}], "isCanceled": false,
 *                        "isMoved": false, "changes": ... } ] } ] } }
 */
object ScheduleParser {

    private val json = Json { isLenient = true }

    fun parseRoot(text: String): JsonObject {
        val el = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw SiteException("Сайт вернул не расписание, а что-то другое")
        }
        val root = el as? JsonObject ?: throw SiteException("Неожиданный ответ сайта")
        val state = root["state"]
        if (!state.truthy()) {
            val reason = root["reason"].str() ?: root["message"].str() ?: ""
            if (reason.contains("капч", ignoreCase = true) ||
                reason.contains("captcha", ignoreCase = true)
            ) throw CaptchaRequiredException()
            throw SiteException(
                if (reason.isBlank()) "Сайт отказал в выдаче расписания" else reason.take(200)
            )
        }
        return root
    }

    fun parseWeek(text: String, groupId: String, monday: LocalDate, now: Long): WeekSchedule {
        val root = parseRoot(text)
        val rows = root["rows"].obj() ?: JsonObject(emptyMap())
        val weekRussia = rows["week"].obj()?.get("weekRussia").obj()
        val days = weekRussia?.get("days").arr().orEmpty().mapNotNull { d ->
            val o = d.obj() ?: return@mapNotNull null
            val date = o["date"].str() ?: return@mapNotNull null
            val n = o["weekDayNumber"].int() ?: return@mapNotNull null
            WeekDay(normalizeDate(date), n)
        }
        val lessons = mutableListOf<Lesson>()
        for (org in rows["organizations"].arr().orEmpty()) {
            val o = org.obj() ?: continue
            val chunks = o["lessonsTimeChunks"].arr().orEmpty().map { it.str().orEmpty() }
            for (l in o["lessons"].arr().orEmpty()) {
                val lo = l.obj() ?: continue
                val groupIds = lo["groups"].arr().orEmpty().mapNotNull { it.obj()?.get("id").str() }
                if (groupIds.isNotEmpty() && groupId !in groupIds) continue
                val tc = lo["timeChunks"].arr().orEmpty().mapNotNull { it.int() }
                val first = tc.firstOrNull()
                val last = tc.lastOrNull()
                val start = first?.let { chunks.getOrNull(it) }?.substringBefore("-")?.trim()
                val end = last?.let { chunks.getOrNull(it) }?.substringAfterLast("-")?.trim()
                if (start.isNullOrEmpty()) continue
                val kind = lo["type"].str()?.takeIf { it.isNotBlank() }
                val subject = lo["course"].obj()?.get("name").str()?.takeIf { it.isNotBlank() }
                    ?: lo["name"].str()?.takeIf { it.isNotBlank() }
                    ?: kind ?: "Занятие"
                val rooms = lo["rooms"].arr().orEmpty().mapNotNull { r ->
                    val ro = r.obj() ?: return@mapNotNull r.str()
                    (ro["number"].str() ?: ro["name"].str())?.takeIf { it.isNotBlank() }
                }.distinct().joinToString(", ")
                val teachers = lo["teachers"].arr().orEmpty().mapNotNull { teacherName(it) }
                    .distinct().joinToString(", ")
                lessons += Lesson(
                    weekDay = lo["weekDayNumber"].int() ?: continue,
                    start = start,
                    end = end.orEmpty(),
                    subject = subject,
                    kind = kind,
                    room = rooms.ifBlank { null },
                    teacher = teachers.ifBlank { null },
                    cancelled = lo["isCanceled"].truthy() || lo["isCancelled"].truthy(),
                    moved = lo["isMoved"].truthy(),
                    changed = lo["changes"].truthy(),
                )
            }
        }
        lessons.sortWith(compareBy({ it.weekDay }, { timeKey(it.start) }))
        return WeekSchedule(
            groupId = groupId,
            monday = monday,
            weekType = weekRussia?.get("type").str(),
            days = days,
            lessons = lessons,
            fetchedAt = now,
        )
    }

    fun parseFaculties(text: String): List<Faculty> =
        parseRoot(text)["rows"].arr().orEmpty().mapNotNull { r ->
            val o = r.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            Faculty(id, o["name"].str() ?: o["title"].str() ?: id)
        }.sortedBy { it.name }

    fun parseGroups(text: String): List<Group> =
        parseRoot(text)["rows"].arr().orEmpty().mapNotNull { r ->
            val o = r.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            Group(id, o["code"].str() ?: o["name"].str() ?: id)
        }.sortedBy { it.code }

    /** "Иванов Иван Петрович" -> "Иванов И. П." */
    private fun teacherName(el: JsonElement): String? {
        val o = el.obj() ?: return el.str()?.takeIf { it.isNotBlank() }
        val last = o["lastName"].str()?.trim().orEmpty()
        val first = o["firstName"].str()?.trim().orEmpty()
        val middle = (o["middleName"].str() ?: o["patronymic"].str() ?: o["secondName"].str())
            ?.trim().orEmpty()
        if (last.isEmpty()) {
            return (o["fullName"].str() ?: o["name"].str() ?: o["fio"].str())
                ?.trim()?.takeIf { it.isNotEmpty() }
        }
        val initials = listOf(first, middle).filter { it.isNotEmpty() }
            .joinToString(" ") { it.first().uppercaseChar() + "." }
        return if (initials.isEmpty()) last else "$last $initials"
    }

    /** "1-9-2026" / "01.09.2026" -> "01-09-2026" */
    private fun normalizeDate(s: String): String {
        val p = s.trim().split('-', '.', '/')
        if (p.size != 3) return s
        return if (p[0].length == 4) {
            "%02d-%02d-%s".format(p[2].toIntOrNull() ?: 0, p[1].toIntOrNull() ?: 0, p[0])
        } else {
            "%02d-%02d-%s".format(p[0].toIntOrNull() ?: 0, p[1].toIntOrNull() ?: 0, p[2])
        }
    }
}

// ---- безопасный доступ к JSON (сайт может прислать null/число вместо строки и т. п.)

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.str(): String? =
    if (this is JsonPrimitive && this !is JsonNull) this.content else null
internal fun JsonElement?.int(): Int? = str()?.trim()?.toDoubleOrNull()?.toInt()
internal fun JsonElement?.truthy(): Boolean = when (this) {
    null, JsonNull -> false
    is JsonPrimitive -> booleanOrNull ?: content.let { it.isNotEmpty() && it != "0" }
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
}
