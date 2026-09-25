package com.vadik.raspisanie.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.vadik.raspisanie.App
import com.vadik.raspisanie.R
import com.vadik.raspisanie.data.Accents
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Prefs
import com.vadik.raspisanie.data.Settings
import com.vadik.raspisanie.data.Upcoming
import com.vadik.raspisanie.data.timeKey
import com.vadik.raspisanie.ui.MainActivity
import com.vadik.raspisanie.work.ReminderReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Общее для всех виджетов: цвета, фон, данные о ближайшей паре, обновление по расписанию. */
object WidgetKit {

    class Palette(val bg: Int, val text: Int, val muted: Int, val accent: Int, val red: Int)

    private val DAY_DM: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM", Locale.forLanguageTag("ru"))
    const val ACTION_TICK = "com.vadik.raspisanie.WIDGET_TICK"

    fun palette(ctx: Context, prefs: Prefs?): Palette {
        val theme = prefs?.widgetTheme ?: "system"
        val seed = Accents.seedOf(prefs?.accent ?: "blue").toInt()
        val systemDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (theme) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed, it) }
        val deep = hsl[2] < 0.33f
        return if (dark) {
            Palette(
                0xFF1F2226.toInt(), 0xFFE3E2E6.toInt(), 0xFF9AA0A8.toInt(),
                ColorUtils.blendARGB(seed, 0xFFFFFFFF.toInt(), if (deep) 0.55f else 0.35f), 0xFFFF7A7A.toInt(),
            )
        } else {
            Palette(
                0xFFFFFFFF.toInt(), 0xFF1A1C1E.toInt(), 0xFF6B7280.toInt(),
                if (deep) seed else ColorUtils.blendARGB(seed, 0xFF000000.toInt(), 0.15f), 0xFFD32F2F.toInt(),
            )
        }
    }

    fun applyBackground(views: RemoteViews, color: Int, opacityPercent: Int) {
        views.setInt(R.id.widget_bg, "setColorFilter", color)
        views.setInt(R.id.widget_bg, "setImageAlpha", (opacityPercent.coerceIn(0, 100) * 255) / 100)
    }

    /** Открыть приложение; [tab] — сразу нужный раздел (например, «subjects»). */
    fun openApp(ctx: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, if (tab == null) 1 else 1 + tab.hashCode().and(0xFFFF),
        Intent(ctx, MainActivity::class.java).apply {
            tab?.let {
                putExtra(MainActivity.EXTRA_TAB, it)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** «3 корп., 3 эт.» по номеру аудитории. */
    fun shortPlace(room: String?): String? = Campus.locate(room)?.let { loc ->
        val b = when (loc.building.id) {
            "main" -> "гл. корп."
            "k2" -> "2 корп."
            "k3" -> "3 корп."
            "lib" -> "библ."
            else -> loc.building.short
        }
        listOfNotNull(b, loc.floor?.let { if (it == 0) "цок." else "$it эт." }).joinToString(", ")
    }

    /** «ауд. 2358 · 3 корп., 3 эт.» */
    fun placeLine(l: Lesson): String = listOfNotNull(
        l.room?.let { "ауд. ${it.substringBefore(" - ")}" + if (l.roomChanged) " (замена)" else "" },
        shortPlace(l.room),
    ).joinToString(" · ")

    class Ctx(val settings: Settings?, val prefs: Prefs?, val day: Upcoming.WidgetDay?, val now: LocalDateTime)

    fun load(ctx: Context): Ctx {
        val repo = (ctx.applicationContext as App).repo
        val settings = repo.settings()
        val prefs = repo.prefs()
        val now = LocalDateTime.now()
        val day = settings?.let { s -> Upcoming.widgetDay({ d -> repo.cachedWeek(s.groupId, d) }, prefs, now) }
        return Ctx(settings, prefs, day, now)
    }

    /** Текущая или ближайшая пара и подпись к ней («СЕЙЧАС · до 11:45», «ДАЛЕЕ · в 12:00», «ЗАВТРА · 8:30»). */
    class Next(val label: String, val lesson: Lesson?, val empty: String?)

    fun next(c: Ctx): Next {
        val prefs = c.prefs ?: Prefs()
        val day = c.day ?: return Next("MyGub", null, "Откройте приложение и выберите группу")
        if (!day.known) return Next("MyGub", null, "Нет данных — откройте MyGub")
        val today = c.now.toLocalDate()
        val nowMin = c.now.hour * 60 + c.now.minute
        val active = day.lessons.filter { !it.cancelled && !it.moved && !prefs.isOtherSubgroup(it) }
        if (day.date == today) {
            if (day.currentIndex >= 0) {
                val l = day.lessons[day.currentIndex]
                return Next("СЕЙЧАС · до ${l.end.ifBlank { l.start }}", l, null)
            }
            active.firstOrNull { timeKey(it.start) > nowMin }?.let { l ->
                val mins = timeKey(l.start) - nowMin
                val inTxt = if (mins < 60) "через $mins мин" else "в ${l.start}"
                return Next("ДАЛЕЕ · $inTxt", l, null)
            }
        }
        val first = active.firstOrNull() ?: return Next(dayWord(day.date, today).uppercase(), null, "Пар нет 🎉")
        return Next("${dayWord(day.date, today).uppercase()} · ${first.start}", first, null)
    }

    fun dayWord(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "сегодня"
        today.plusDays(1) -> "завтра"
        else -> date.format(DAY_DM)
    }

    // ---------------------------------------------------------------- обновление

    private val providers = listOf(
        ScheduleWidget::class.java,
        NextLessonWidget::class.java,
        CompactDayWidget::class.java,
        StripWidget::class.java,
        HomeworkWidget::class.java,
    )

    fun updateAll(ctx: Context) {
        val manager = AppWidgetManager.getInstance(ctx)
        for (p in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(ctx, p))
            if (ids.isEmpty()) continue
            when (p) {
                ScheduleWidget::class.java -> ScheduleWidget.updateAll(ctx)
                NextLessonWidget::class.java -> manager.updateAppWidget(ids, NextLessonWidget.build(ctx))
                CompactDayWidget::class.java -> manager.updateAppWidget(ids, CompactDayWidget.build(ctx))
                StripWidget::class.java -> manager.updateAppWidget(ids, StripWidget.build(ctx))
                HomeworkWidget::class.java -> manager.updateAppWidget(ids, HomeworkWidget.build(ctx))
            }
        }
        scheduleTick(ctx)
    }

    /**
     * Следующее «событие» для виджетов — начало или конец ближайшей пары (или полночь):
     * в этот момент виджеты перерисуются, и «СЕЙЧАС/ДАЛЕЕ» всегда актуальны.
     */
    private fun scheduleTick(ctx: Context) {
        val c = runCatching { load(ctx) }.getOrNull() ?: return
        val today = c.now.toLocalDate()
        val nowMin = c.now.hour * 60 + c.now.minute
        val marks = c.day?.takeIf { it.date == today }?.lessons.orEmpty()
            .flatMap { listOf(timeKey(it.start), timeKey(it.end.ifBlank { it.start })) }
            .filter { it in (nowMin + 1)..(24 * 60 - 1) }
        val at = marks.minOrNull()?.let { m -> today.atTime(m / 60, m % 60) }
            ?: today.plusDays(1).atTime(0, 5)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 101, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }
    }
}
