package com.vadik.raspisanie.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate

/** Хранение настроек и сохранённых недель в файлах приложения (работает без интернета). */
class Storage(private val dir: File) {

    init {
        dir.mkdirs()
    }

    private val settingsFile get() = File(dir, "settings.json")

    fun loadSettings(): Settings? = try {
        val o = Json.parseToJsonElement(settingsFile.readText()).obj()
        val id = o?.get("groupId").str()
        if (o == null || id.isNullOrBlank()) null else Settings(
            groupId = id,
            groupName = o["groupName"].str().orEmpty(),
            facultyName = o["facultyName"].str().orEmpty(),
        )
    } catch (e: Exception) {
        null
    }

    fun saveSettings(s: Settings) {
        val o = buildJsonObject {
            put("groupId", s.groupId)
            put("groupName", s.groupName)
            put("facultyName", s.facultyName)
        }
        writeAtomic(settingsFile, o.toString())
    }

    private val prefsFile get() = File(dir, "prefs.json")
    private val rawFile get() = File(dir, "last_raw.json")

    fun loadPrefs(): Prefs = try {
        val o = Json.parseToJsonElement(prefsFile.readText()).obj()
        val d = Prefs()
        if (o == null) d else Prefs(
            subgroup = o["subgroup"].int() ?: d.subgroup,
            hideOtherSubgroup = o["hideOther"]?.let { it.truthy() } ?: d.hideOtherSubgroup,
            remindEnabled = o["remind"]?.let { it.truthy() } ?: d.remindEnabled,
            remindMinutes = o["remindMinutes"].int() ?: d.remindMinutes,
            changeNotify = o["changeNotify"]?.let { it.truthy() } ?: d.changeNotify,
            theme = o["theme"].str() ?: d.theme,
            dynamicColor = o["dynamic"]?.let { it.truthy() } ?: d.dynamicColor,
            widgetOpacity = o["widgetOpacity"].int()?.coerceIn(0, 100) ?: d.widgetOpacity,
            widgetTheme = o["widgetTheme"].str() ?: d.widgetTheme,
        )
    } catch (e: Exception) {
        Prefs()
    }

    fun savePrefs(p: Prefs) {
        val o = buildJsonObject {
            put("subgroup", p.subgroup)
            put("hideOther", p.hideOtherSubgroup)
            put("remind", p.remindEnabled)
            put("remindMinutes", p.remindMinutes)
            put("changeNotify", p.changeNotify)
            put("theme", p.theme)
            put("dynamic", p.dynamicColor)
            put("widgetOpacity", p.widgetOpacity)
            put("widgetTheme", p.widgetTheme)
        }
        writeAtomic(prefsFile, o.toString())
    }

    private val historyFile get() = File(dir, "history.json")

    /** Журнал изменений, замеченных приложением (последние ~2 месяца). */
    fun loadHistory(): List<Change> = try {
        Json.parseToJsonElement(historyFile.readText()).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Change(
                date = LocalDate.parse(o["date"].str() ?: return@mapNotNull null),
                start = o["start"].str().orEmpty(),
                subject = o["subject"].str().orEmpty(),
                text = o["text"].str().orEmpty(),
                line = o["line"].str().orEmpty(),
                noticedAt = o["at"].str()?.toLongOrNull() ?: 0L,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun appendHistory(items: List<Change>) {
        if (items.isEmpty()) return
        val border = LocalDate.now().minusDays(60)
        val all = (loadHistory() + items).filter { !it.date.isBefore(border) }.takeLast(500)
        val arr = buildJsonArray {
            all.forEach { c ->
                add(buildJsonObject {
                    put("date", c.date.toString())
                    put("start", c.start)
                    put("subject", c.subject)
                    put("text", c.text)
                    put("line", c.line)
                    put("at", c.noticedAt)
                })
            }
        }
        writeAtomic(historyFile, arr.toString())
    }

    /** Последний «сырой» ответ сайта — для диагностики (например, если подгруппы не распознаются). */
    fun saveRaw(text: String) = runCatching { writeAtomic(rawFile, text) }

    fun rawFile(): File? = rawFile.takeIf { it.exists() }

    private fun weekFile(groupId: String, monday: LocalDate) =
        File(dir, "week_${groupId}_$monday.json")

    fun loadWeek(groupId: String, monday: LocalDate): WeekSchedule? = try {
        decodeWeek(weekFile(groupId, monday).readText())
    } catch (e: Exception) {
        null
    }

    fun saveWeek(w: WeekSchedule) {
        writeAtomic(weekFile(w.groupId, w.monday), encodeWeek(w).toString())
        cleanup(w.groupId)
    }

    /** Удаляем недели старше двух месяцев, чтобы файлы не копились. */
    private fun cleanup(groupId: String) {
        val border = LocalDate.now().minusWeeks(8)
        dir.listFiles()?.forEach { f ->
            val m = Regex("""week_(.+)_(\d{4}-\d{2}-\d{2})\.json""").matchEntire(f.name)
                ?: return@forEach
            val date = runCatching { LocalDate.parse(m.groupValues[2]) }.getOrNull()
            if (m.groupValues[1] != groupId || (date != null && date.isBefore(border))) f.delete()
        }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun encodeWeek(w: WeekSchedule): JsonObject = buildJsonObject {
            put("groupId", w.groupId)
            put("monday", w.monday.toString())
            w.weekType?.let { put("weekType", it) }
            put("fetchedAt", w.fetchedAt)
            put("days", buildJsonArray {
                w.days.forEach { d ->
                    add(buildJsonObject {
                        put("date", d.date)
                        put("n", d.weekDayNumber)
                    })
                }
            })
            put("lessons", buildJsonArray {
                w.lessons.forEach { l ->
                    add(buildJsonObject {
                        put("wd", l.weekDay)
                        put("start", l.start)
                        put("end", l.end)
                        put("subject", l.subject)
                        l.kind?.let { put("kind", it) }
                        l.room?.let { put("room", it) }
                        l.teacher?.let { put("teacher", it) }
                        put("cancelled", l.cancelled)
                        put("moved", l.moved)
                        put("changed", l.changed)
                        l.subgroup?.let { put("sub", it) }
                        l.teacherFull?.let { put("tf", it) }
                        if (l.changeLines.isNotEmpty()) {
                            put("chg", buildJsonArray { l.changeLines.forEach { add(JsonPrimitive(it)) } })
                        }
                        l.raw?.let { put("raw", it) }
                        if (l.roomChanged) put("rc", true)
                        l.oldRoom?.let { put("or", it) }
                        if (l.teacherChanged) put("tc", true)
                        l.oldTeacher?.let { put("ot", it) }
                        l.movedFrom?.let { put("mf", it) }
                        l.movedTo?.let { put("mt", it) }
                        l.info?.let { put("info", it) }
                        l.department?.let { put("dep", it) }
                    })
                }
            })
        }

        fun decodeWeek(text: String): WeekSchedule? {
            val o = Json.parseToJsonElement(text).obj() ?: return null
            return WeekSchedule(
                groupId = o["groupId"].str() ?: return null,
                monday = LocalDate.parse(o["monday"].str() ?: return null),
                weekType = o["weekType"].str(),
                days = o["days"].arr().orEmpty().mapNotNull { d ->
                    val x = d.obj() ?: return@mapNotNull null
                    WeekDay(x["date"].str() ?: return@mapNotNull null, x["n"].int() ?: 0)
                },
                lessons = o["lessons"].arr().orEmpty().mapNotNull { e ->
                    val x = e.obj() ?: return@mapNotNull null
                    Lesson(
                        weekDay = x["wd"].int() ?: return@mapNotNull null,
                        start = x["start"].str().orEmpty(),
                        end = x["end"].str().orEmpty(),
                        subject = x["subject"].str().orEmpty(),
                        kind = x["kind"].str(),
                        room = x["room"].str(),
                        teacher = x["teacher"].str(),
                        cancelled = x["cancelled"].truthy(),
                        moved = x["moved"].truthy(),
                        changed = x["changed"].truthy(),
                        subgroup = x["sub"].int(),
                        teacherFull = x["tf"].str(),
                        changeLines = x["chg"].arr().orEmpty().mapNotNull { it.str() },
                        raw = x["raw"].str(),
                        roomChanged = x["rc"].truthy(),
                        oldRoom = x["or"].str(),
                        teacherChanged = x["tc"].truthy(),
                        oldTeacher = x["ot"].str(),
                        movedFrom = x["mf"].str(),
                        movedTo = x["mt"].str(),
                        info = x["info"].str(),
                        department = x["dep"].str(),
                    )
                },
                fetchedAt = o["fetchedAt"].str()?.toLongOrNull() ?: 0L,
            )
        }
    }
}
