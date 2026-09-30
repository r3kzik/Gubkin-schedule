package com.vadik.raspisanie.work

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.CalEvent
import com.vadik.raspisanie.data.CalendarExport
import java.time.LocalDate
import java.time.ZoneId

/** Запись пар и своих дел в календарь телефона (Google, Samsung, Xiaomi…). */
object CalendarSync {

    data class Calendar(val id: Long, val name: String, val account: String)

    fun hasPermission(ctx: Context) = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Календари, в которые можно писать. */
    fun calendars(ctx: Context): List<Calendar> {
        if (!hasPermission(ctx)) return emptyList()
        val out = mutableListOf<Calendar>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(3)
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                out += Calendar(c.getLong(0), c.getString(1) ?: "Календарь", c.getString(2) ?: "")
            }
        }
        return out
    }

    private fun millis(t: java.time.LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Удалить будущие события MyGub из календаря. Возвращает, сколько удалено. */
    fun clear(ctx: Context, calendarId: Long, from: LocalDate = LocalDate.now()): Int {
        if (!hasPermission(ctx)) return 0
        return ctx.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ?",
            arrayOf(calendarId.toString(), "%${CalendarExport.TAG}%", millis(from.atStartOfDay()).toString()),
        )
    }

    /** Заменить будущие события MyGub свежими. Возвращает, сколько записано. */
    fun export(ctx: Context, calendarId: Long, events: List<CalEvent>): Int {
        if (!hasPermission(ctx)) return 0
        clear(ctx, calendarId)
        val tz = ZoneId.systemDefault().id
        var n = 0
        for (e in events) {
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, e.title)
                put(CalendarContract.Events.DTSTART, millis(e.start))
                put(CalendarContract.Events.DTEND, millis(e.end))
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                e.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                put(CalendarContract.Events.DESCRIPTION, e.description)
            }
            if (ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) != null) n++
        }
        return n
    }

    /** Автообновление календаря после изменений расписания — только если события правда поменялись. */
    fun autoSync(ctx: Context) {
        val repo = (ctx.applicationContext as App).repo
        val prefs = repo.prefs()
        val settings = repo.settings() ?: return
        if (!prefs.calendarAuto || prefs.calendarId < 0 || !hasPermission(ctx)) return
        val events = repo.calendarEvents(settings.groupId)
        val hash = events.hashCode()
        val sp = ctx.getSharedPreferences("calendar", Context.MODE_PRIVATE)
        if (sp.getInt("hash", 0) == hash && sp.getString("day", "") == LocalDate.now().toString()) return
        runCatching { export(ctx, prefs.calendarId, events) }.onSuccess {
            sp.edit().putInt("hash", hash).putString("day", LocalDate.now().toString()).apply()
        }
    }
}
