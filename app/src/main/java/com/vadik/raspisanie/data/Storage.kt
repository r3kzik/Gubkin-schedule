package com.vadik.raspisanie.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
                    )
                },
                fetchedAt = o["fetchedAt"].str()?.toLongOrNull() ?: 0L,
            )
        }
    }
}
