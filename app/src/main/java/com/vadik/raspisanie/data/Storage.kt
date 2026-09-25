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
            accent = o["accent"].str() ?: d.accent,
            animatedBackground = o["anim"]?.let { it.truthy() } ?: d.animatedBackground,
            style = o["style"].str() ?: d.style,
            cornerPercent = o["corner"].int()?.coerceIn(50, 150) ?: d.cornerPercent,
            glassPercent = o["glassPct"].int()?.coerceIn(50, 150) ?: d.glassPercent,
            iconFollowsAccent = o["iconAccent"]?.let { it.truthy() } ?: d.iconFollowsAccent,
            autoUpdateCheck = o["updCheck"]?.let { it.truthy() } ?: d.autoUpdateCheck,
            autoInstall = o["updAuto"]?.let { it.truthy() } ?: d.autoInstall,
            tabBarShape = o["barShape"].str() ?: d.tabBarShape,
            ongoingLesson = o["nowNotif2"]?.let { it.truthy() } ?: d.ongoingLesson,
            bottomTabs = o["tabs2"].str()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.bottomTabs,
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
            put("accent", p.accent)
            put("anim", p.animatedBackground)
            put("style", p.style)
            put("corner", p.cornerPercent)
            put("glassPct", p.glassPercent)
            put("iconAccent", p.iconFollowsAccent)
            put("updCheck", p.autoUpdateCheck)
            put("updAuto", p.autoInstall)
            put("tabs2", p.bottomTabs.joinToString(","))
            put("nowNotif2", p.ongoingLesson)
            put("barShape", p.tabBarShape)
        }
        writeAtomic(prefsFile, o.toString())
    }

    private val homeworkFile get() = File(dir, "homework.json")

    fun loadHomework(): List<Homework> = try {
        Json.parseToJsonElement(homeworkFile.readText()).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Homework(
                id = o["id"].str() ?: return@mapNotNull null,
                subject = o["subject"].str().orEmpty(),
                text = o["text"].str().orEmpty(),
                due = o["due"].str()?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                done = o["done"].truthy(),
                createdAt = o["at"].str()?.toLongOrNull() ?: 0L,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveHomework(items: List<Homework>) {
        val arr = buildJsonArray {
            items.forEach { h ->
                add(buildJsonObject {
                    put("id", h.id)
                    put("subject", h.subject)
                    put("text", h.text)
                    h.due?.let { put("due", it.toString()) }
                    put("done", h.done)
                    put("at", h.createdAt)
                })
            }
        }
        writeAtomic(homeworkFile, arr.toString())
    }

    /** Все сохранённые недели группы (для списка предметов и поиска следующей пары). */
    fun allWeeks(groupId: String): List<WeekSchedule> =
        dir.listFiles().orEmpty()
            .filter { it.name.startsWith("week_${groupId}_") && it.name.endsWith(".json") }
            .mapNotNull { runCatching { decodeWeek(it.readText()) }.getOrNull() }
            .sortedBy { it.monday }

    // ------------------------------------------------------------ преподаватели

    private val probeFile get() = File(dir, "api_probe.json")

    /** Какие адреса API подошли: ключ -> номер варианта (-1 — ни один) и время проверки. */
    fun loadProbe(): Map<String, Long> = try {
        Json.parseToJsonElement(probeFile.readText()).obj().orEmpty()
            .mapNotNull { (k, v) -> v.str()?.toLongOrNull()?.let { k to it } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveProbe(m: Map<String, Long>) {
        writeAtomic(probeFile, buildJsonObject { m.forEach { (k, v) -> put(k, v) } }.toString())
    }

    private val teacherRaw get() = File(dir, "teacher_raw.txt")

    fun saveTeacherRaw(text: String) = runCatching { writeAtomic(teacherRaw, text) }

    fun teacherRawFile(): File? = teacherRaw.takeIf { it.exists() }

    private val teachersFile get() = File(dir, "teachers.json")

    /** Сохранённый список преподавателей и время загрузки. */
    fun loadTeachers(): Pair<List<Teacher>, Long>? = try {
        val o = Json.parseToJsonElement(teachersFile.readText()).obj()!!
        val list = o["list"].arr().orEmpty().mapNotNull { e ->
            val t = e.obj() ?: return@mapNotNull null
            Teacher(
                id = t["id"].str() ?: return@mapNotNull null,
                fullName = t["full"].str().orEmpty(),
                shortName = t["short"].str().orEmpty(),
                department = t["dep"].str(),
                divisionId = t["div"].str(),
            )
        }
        list to (o["at"].str()?.toLongOrNull() ?: 0L)
    } catch (e: Exception) {
        null
    }

    fun saveTeachers(list: List<Teacher>, at: Long) {
        val o = buildJsonObject {
            put("at", at)
            put("list", buildJsonArray {
                list.forEach { t ->
                    add(buildJsonObject {
                        put("id", t.id); put("full", t.fullName); put("short", t.shortName)
                        t.department?.let { put("dep", it) }
                        t.divisionId?.let { put("div", it) }
                    })
                }
            })
        }
        writeAtomic(teachersFile, o.toString())
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
