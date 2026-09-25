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
    private val pretty = Json { prettyPrint = true }

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

    fun parseWeek(
        text: String,
        groupId: String,
        monday: LocalDate,
        now: Long,
        groupCode: String = "",
    ): WeekSchedule {
        val myCode = Repository.normalizeCode(groupCode)
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
                val groups = lo["groups"].arr().orEmpty().mapNotNull { it.obj() }
                // своя группа: по id или по коду (подгруппы на сайте могут быть отдельными «группами»
                // вида «КВ-26-02/1»)
                val mine = groups.firstOrNull { it["id"].str() == groupId }
                    ?: groups.firstOrNull { g ->
                        myCode.isNotEmpty() && groupCodeOf(g)?.let { Repository.normalizeCode(it).startsWith(myCode) } == true
                    }
                if (groups.isNotEmpty() && mine == null) continue
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
                // Формат сайта: rooms/teachers — по основному расписанию, а в "changes"
                // лежат ЗАМЕНЫ: {"rooms":[…]} и/или {"teachers":[…]}. Сайт показывает замену красным.
                val changesObj = lo["changes"].obj()
                val baseRooms = roomsText(lo["rooms"])
                val newRooms = roomsText(changesObj?.get("rooms"))
                val baseTeachers = lo["teachers"].arr().orEmpty()
                val newTeachers = changesObj?.get("teachers").arr().orEmpty()
                val roomChanged = newRooms.isNotEmpty() && newRooms != baseRooms
                val teacherChanged = newTeachers.isNotEmpty()
                val effTeachers = if (teacherChanged) newTeachers else baseTeachers
                val shortNames = effTeachers.mapNotNull { teacherName(it) }.distinct().joinToString(", ")
                val fullNames = effTeachers.mapNotNull { fullName(it) }.distinct().joinToString(", ")
                val oldShort = baseTeachers.mapNotNull { teacherName(it) }.distinct().joinToString(", ")
                    .takeIf { teacherChanged && it.isNotBlank() && it != shortNames }
                val oldFull = baseTeachers.mapNotNull { fullName(it) }.distinct().joinToString(", ")
                    .takeIf { teacherChanged && it.isNotBlank() }
                // перенос: у новой пары есть movedFrom (+ номер «кусочка» времени), у старой — isMoved + movedTo
                val movedFrom = lo["movedFrom"].str()?.takeIf { it.isNotBlank() }?.let { d ->
                    val t = lo["movedFromStartTimeChunkId"].int()?.let { chunks.getOrNull(it) }
                        ?.substringBefore("-")?.trim()
                    shortDate(d) + (t?.let { " в $it" } ?: "")
                }
                val movedTo = lo["movedTo"].str()?.takeIf { it.isNotBlank() }?.let { d ->
                    val t = lo["movedStartTimeChunkId"].int()?.let { chunks.getOrNull(it) }
                        ?.substringBefore("-")?.trim()
                    shortDate(d) + (t?.let { " в $it" } ?: "")
                }
                val moved = lo["isMoved"].truthy()

                val lines = mutableListOf<String>()
                if (roomChanged) {
                    lines += if (baseRooms.isNotEmpty()) "Аудитория: $baseRooms → $newRooms" else "Аудитория: $newRooms"
                }
                if (teacherChanged) {
                    lines += if (oldFull != null && oldFull != fullNames) "Преподаватель: $oldFull → $fullNames"
                    else "Преподаватель (замена): $fullNames"
                }
                if (movedFrom != null) lines += "Перенесено с $movedFrom"
                if (moved && movedTo != null) lines += "Перенесено на $movedTo"
                // прочие, пока неизвестные виды изменений — общим разбором
                val handled = setOf("rooms", "teachers").filter { changesObj?.get(it) is JsonArray }
                val rest = if (changesObj != null) {
                    JsonObject(lo + ("changes" to JsonObject(changesObj.filterKeys { it !in handled })))
                } else lo
                lines += changeLines(rest)

                val info = lo["course"].obj()?.get("additionalInfo").str()?.trim()?.takeIf { it.isNotEmpty() }
                val department = lo["divisions"].arr().orEmpty()
                    .mapNotNull { it.obj()?.get("name").str()?.trim()?.takeIf { n -> n.isNotEmpty() } }
                    .distinct().joinToString(", ").ifBlank { null }

                lessons += Lesson(
                    weekDay = lo["weekDayNumber"].int() ?: continue,
                    start = start,
                    end = end.orEmpty(),
                    subject = subject,
                    kind = kind,
                    room = (if (roomChanged) newRooms else baseRooms).ifBlank { null },
                    teacher = shortNames.ifBlank { null },
                    cancelled = lo["isCanceled"].truthy() || lo["isCancelled"].truthy(),
                    moved = moved,
                    changed = roomChanged || teacherChanged || movedFrom != null || lines.isNotEmpty(),
                    subgroup = detectSubgroup(lo, mine, myCode),
                    teacherFull = fullNames.ifBlank { null },
                    changeLines = lines.distinct(),
                    raw = runCatching { pretty.encodeToString(JsonElement.serializer(), lo) }.getOrNull(),
                    roomChanged = roomChanged,
                    oldRoom = baseRooms.takeIf { roomChanged && it.isNotEmpty() },
                    teacherChanged = teacherChanged,
                    oldTeacher = oldShort,
                    movedFrom = movedFrom,
                    movedTo = movedTo,
                    info = info,
                    department = department,
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

    // ---- подгруппы
    //
    // Точный формат подгрупп в API неизвестен, поэтому ищем в нескольких местах:
    // 1) числовое поле пары вроде subgroup / subGroupNumber / subgroups;
    // 2) то же поле у записи своей группы в groups[] или код группы вида «КВ-26-02/1»;
    // 3) текст где угодно в паре: «1 п/г», «подгруппа 2», «2-я подгр.».

    private val SUB_KEY = Regex("(?i)^(sub_?groups?|podgr\\w*|sub_?group_?(number|num|no|index|nr))$")
    private val TXT_BEFORE = Regex("(?iu)(?<![\\d/])([1-9])\\s*(?:-?\\s*(?:я|ая))?\\s*(?:п/г|пг(?![а-яё])|подгр|гр\\.|групп)")
    private val TXT_AFTER = Regex("(?iu)(?:п/г|подгр[а-яё]*\\.?)\\s*№?\\s*([1-9])(?!\\d)")
    private val CODE_SUFFIX = Regex("^[\\s/.()\\-]*([1-9])\\)?$")

    fun textSubgroup(s: String): Int? =
        (TXT_BEFORE.find(s) ?: TXT_AFTER.find(s))?.groupValues?.get(1)?.toIntOrNull()

    private fun groupCodeOf(g: JsonObject): String? =
        g["code"].str() ?: g["name"].str() ?: g["title"].str()

    private fun numFrom(el: JsonElement?): Int? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.str()?.let { s ->
            s.trim().toIntOrNull()?.takeIf { it in 1..9 } ?: textSubgroup(s)
        }
        is JsonArray -> el.mapNotNull { numFrom(it) }.distinct().singleOrNull()
        is JsonObject -> numFrom(el["number"] ?: el["num"] ?: el["name"] ?: el["title"])
    }

    private fun keySubgroup(o: JsonObject): Int? {
        for ((k, v) in o) {
            if (SUB_KEY.matches(k)) numFrom(v)?.let { return it }
        }
        return null
    }

    private fun scanText(el: JsonElement?, depth: Int = 0): Int? {
        if (depth > 4) return null
        return when (el) {
            null, JsonNull -> null
            is JsonPrimitive -> if (el.isString) textSubgroup(el.content) else null
            is JsonArray -> el.firstNotNullOfOrNull { scanText(it, depth + 1) }
            // преподавателей не просматриваем: в ФИО подгрупп нет, а ложные совпадения возможны
            is JsonObject -> el.entries.filter { it.key != "teachers" }
                .firstNotNullOfOrNull { scanText(it.value, depth + 1) }
        }
    }

    private fun detectSubgroup(lesson: JsonObject, mine: JsonObject?, myCode: String): Int? {
        // формат сайта: "subgroup": 0 — вся группа, 1/2 — подгруппа
        val explicit = lesson["subgroup"]
        if (explicit is JsonPrimitive && explicit !is JsonNull) {
            explicit.str()?.trim()?.toIntOrNull()?.let { return it.takeIf { n -> n in 1..9 } }
        }
        keySubgroup(lesson)?.let { return it }
        if (mine != null) {
            keySubgroup(mine)?.let { return it }
            val code = groupCodeOf(mine)?.let { Repository.normalizeCode(it) }
            if (code != null && myCode.isNotEmpty() && code.startsWith(myCode) && code != myCode) {
                val rest = code.removePrefix(myCode)
                CODE_SUFFIX.matchEntire(rest)?.let { return it.groupValues[1].toInt() }
                textSubgroup(rest)?.let { return it }
            }
        }
        val withoutGroups = JsonObject(lesson.filterKeys { it != "groups" })
        return scanText(withoutGroups)
    }

    // ---- изменения в паре
    //
    // Сайт отмечает изменённые пары полем "changes"; его точный формат неизвестен,
    // поэтому разбираем любые варианты: строку, объект {поле: значение}, {old/new},
    // список таких объектов. Плюс поля пары со словами old/original/prev/replace.

    private val LABELS = mapOf(
        "teacher" to "Преподаватель", "teachers" to "Преподаватель", "lecturer" to "Преподаватель",
        "room" to "Аудитория", "rooms" to "Аудитория", "auditory" to "Аудитория", "auditorium" to "Аудитория",
        "time" to "Время", "timechunks" to "Время", "start" to "Начало", "end" to "Конец",
        "date" to "Дата", "day" to "День", "weekdaynumber" to "День недели",
        "course" to "Предмет", "discipline" to "Предмет", "subject" to "Предмет",
        "type" to "Тип занятия", "comment" to "Комментарий", "reason" to "Причина",
        "note" to "Примечание", "description" to "Описание", "iscanceled" to "Отмена",
        "ismoved" to "Перенос", "groups" to "Группы", "building" to "Корпус",
    )
    private val OLD_KEYS = listOf("old", "from", "before", "prev", "previous", "was", "original")
    private val NEW_KEYS = listOf("new", "to", "after", "next", "now", "current", "replacement")
    private val EXTRA_KEY = Regex("(?i)(old|original|prev|replac|substit|zamen|was)")
    private val KNOWN_KEYS = setOf("movedFrom", "movedTo", "movedStartTimeChunkId", "movedFromStartTimeChunkId")

    private fun label(key: String): String {
        val k = key.lowercase()
        LABELS[k]?.let { return it }
        // oldTeachers / originalRoom / replacementTeacher -> по «корню»
        LABELS.entries.firstOrNull { k.endsWith(it.key) || k.startsWith(it.key) }?.let {
            val prefix = when {
                k.startsWith("old") || k.startsWith("prev") || k.startsWith("original") || k.startsWith("was") -> " (было)"
                k.startsWith("new") || k.startsWith("replac") || k.startsWith("substit") -> " (замена)"
                else -> ""
            }
            return it.value + prefix
        }
        return key
    }

    /** Любое значение JSON -> короткий текст. */
    fun valueText(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> when (el.booleanOrNull) {
            true -> "да"
            false -> "нет"
            null -> el.content.takeIf { it.isNotBlank() }
        }
        is JsonArray -> el.mapNotNull { valueText(it) }.distinct().joinToString(", ").ifBlank { null }
        is JsonObject -> when {
            el["lastName"] != null -> fullName(el)
            el["number"] != null -> el["number"].str()
            el["name"] != null -> valueText(el["name"])
            el["title"] != null -> valueText(el["title"])
            el["code"] != null -> valueText(el["code"])
            else -> el.entries.mapNotNull { (k, v) -> valueText(v)?.let { "${label(k)}: $it" } }
                .joinToString("; ").ifBlank { null }
        }
    }

    private fun renderChangeObject(o: JsonObject, prefix: String?): List<String> {
        val oldV = OLD_KEYS.firstNotNullOfOrNull { k -> o.entries.firstOrNull { it.key.equals(k, true) }?.value }
        val newV = NEW_KEYS.firstNotNullOfOrNull { k -> o.entries.firstOrNull { it.key.equals(k, true) }?.value }
        if (oldV != null || newV != null) {
            val name = prefix
                ?: (o["field"] ?: o["name"] ?: o["type"] ?: o["property"] ?: o["key"]).str()?.let { label(it) }
            val text = "${valueText(oldV) ?: "—"} → ${valueText(newV) ?: "—"}"
            return listOf(if (name != null) "$name: $text" else text)
        }
        return o.entries.flatMap { (k, v) ->
            when (v) {
                is JsonObject -> renderChangeObject(v, label(k))
                // ложные флаги («isMoved: false») не показываем
                is JsonPrimitive -> if (v.booleanOrNull == false) emptyList()
                else listOfNotNull(valueText(v)?.let { "${label(k)}: $it" })
                else -> listOfNotNull(valueText(v)?.let { "${label(k)}: $it" })
            }
        }
    }

    fun changeLines(lesson: JsonObject): List<String> {
        val out = mutableListOf<String>()
        when (val c = lesson["changes"]) {
            null, JsonNull -> Unit
            is JsonPrimitive -> if (c.isString && c.content.isNotBlank()) out += c.content
            is JsonArray -> c.forEach { e ->
                when (e) {
                    is JsonObject -> out += renderChangeObject(e, null)
                    else -> valueText(e)?.let { out += it }
                }
            }
            is JsonObject -> out += renderChangeObject(c, null)
        }
        for ((k, v) in lesson) {
            if (k == "changes" || k in KNOWN_KEYS || !EXTRA_KEY.containsMatchIn(k)) continue
            valueText(v)?.takeIf { it != "нет" }?.let { out += "${label(k)}: $it" }
        }
        return out.distinct()
    }

    private fun roomsText(el: JsonElement?): String = el.arr().orEmpty().mapNotNull { r ->
        val ro = r.obj() ?: return@mapNotNull r.str()
        (ro["number"].str() ?: ro["name"].str())?.trim()?.takeIf { it.isNotBlank() }
    }.distinct().joinToString(", ")

    /** "18-09-2026" -> "18.09" */
    private fun shortDate(s: String): String {
        val p = s.trim().split('-', '.', '/')
        return if (p.size == 3 && p[0].length <= 2) "%02d.%02d".format(p[0].toIntOrNull() ?: 0, p[1].toIntOrNull() ?: 0) else s
    }

    /** Полное ФИО: "Иванов Иван Петрович". */
    internal fun fullName(el: JsonElement): String? {
        val o = el.obj() ?: return el.str()?.takeIf { it.isNotBlank() }
        val parts = listOf(
            o["lastName"].str(), o["firstName"].str(),
            o["middleName"].str() ?: o["patronymic"].str() ?: o["secondName"].str(),
        ).mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (parts.isEmpty()) return (o["fullName"].str() ?: o["name"].str() ?: o["fio"].str())?.trim()
        return parts.joinToString(" ")
    }

    /** "Иванов Иван Петрович" -> "Иванов И. П." */
    internal fun teacherName(el: JsonElement): String? {
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
