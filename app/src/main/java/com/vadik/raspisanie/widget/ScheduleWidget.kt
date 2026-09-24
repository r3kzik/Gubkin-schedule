package com.vadik.raspisanie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.content.res.Configuration
import com.vadik.raspisanie.App
import com.vadik.raspisanie.R
import com.vadik.raspisanie.data.Upcoming
import com.vadik.raspisanie.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Виджет рабочего стола: пары на сегодня, а после последней пары — на следующий учебный день. */
class ScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, manager, appWidgetIds)
    }

    companion object {
        private const val MAX_ROWS = 7
        private val DAY_NAMES = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, ScheduleWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, manager, ids)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = build(ctx)
            manager.updateAppWidget(ids, views)
        }

        private fun build(ctx: Context): RemoteViews {
            val app = ctx.applicationContext as App
            val repo = app.repo
            val views = RemoteViews(ctx.packageName, R.layout.widget_schedule)
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.removeAllViews(R.id.widget_list)

            val settings = repo.settings()
            if (settings == null) {
                val c0 = palette(ctx, "system")
                applyBackground(views, c0.bg, 100)
                views.setTextViewText(R.id.widget_title, "Расписание")
                showEmpty(views, "Откройте приложение и выберите группу")
                return views
            }
            val prefs = repo.prefs()
            val c = palette(ctx, prefs.widgetTheme)
            applyBackground(views, c.bg, prefs.widgetOpacity)
            views.setTextColor(R.id.widget_title, c.text)
            views.setTextColor(R.id.widget_group, c.muted)
            views.setTextColor(R.id.widget_empty, c.muted)
            val now = LocalDateTime.now()
            val day = Upcoming.widgetDay({ d -> repo.cachedWeek(settings.groupId, d) }, prefs, now)
            views.setTextViewText(R.id.widget_title, title(day.date, now.toLocalDate()))
            views.setTextViewText(R.id.widget_group, settings.groupName +
                if (prefs.subgroup != 0) " · ${prefs.subgroup} подгр." else "")

            when {
                !day.known -> showEmpty(views, "Нет данных — откройте приложение, чтобы загрузить")
                day.lessons.isEmpty() -> showEmpty(views, "Пар нет 🎉")
                else -> {
                    views.setViewVisibility(R.id.widget_empty, View.GONE)
                    val accent = c.accent
                    val normal = c.text
                    val muted = c.muted
                    day.lessons.take(MAX_ROWS).forEachIndexed { i, l ->
                        val row = RemoteViews(ctx.packageName, R.layout.widget_row)
                        val inactive = l.cancelled || l.moved || prefs.isOtherSubgroup(l)
                        row.setTextViewText(R.id.row_time, l.start)
                        val subject = buildString {
                            if (l.cancelled) append("✕ ")
                            append(l.subject)
                            l.subgroup?.let { append(" ($it)") }
                        }
                        row.setTextViewText(R.id.row_subject, subject)
                        val details = buildList {
                            if (l.cancelled) add("отменена")
                            if (l.moved) add("перенесена")
                            l.movedFrom?.let { add("перенесено с $it") }
                            l.room?.let {
                                add("ауд. ${it.substringBefore(" - ")}" + if (l.roomChanged) " (замена)" else "")
                            }
                            if (l.teacherChanged) l.teacher?.let { add("преп. $it (замена)") }
                            l.kind?.let { add(it) }
                        }.joinToString(" · ")
                        val hasChange = l.roomChanged || l.teacherChanged || l.movedFrom != null
                        row.setTextViewText(R.id.row_details, details)
                        val color = when {
                            i == day.currentIndex -> accent
                            inactive -> muted
                            else -> normal
                        }
                        row.setTextColor(R.id.row_time, if (i == day.currentIndex) accent else color)
                        row.setTextColor(R.id.row_subject, color)
                        row.setTextColor(R.id.row_details, if (hasChange && !inactive) c.red else muted)
                        views.addView(R.id.widget_list, row)
                    }
                    if (day.lessons.size > MAX_ROWS) {
                        val more = RemoteViews(ctx.packageName, R.layout.widget_row)
                        more.setTextViewText(R.id.row_time, "")
                        more.setTextViewText(R.id.row_subject, "…ещё ${day.lessons.size - MAX_ROWS}")
                        more.setTextViewText(R.id.row_details, "")
                        more.setTextColor(R.id.row_subject, muted)
                        views.addView(R.id.widget_list, more)
                    }
                }
            }
            return views
        }

        private class Palette(val bg: Int, val text: Int, val muted: Int, val accent: Int, val red: Int)

        /** Цвета виджета: своя тема (светлая/тёмная) или как в системе. */
        private fun palette(ctx: Context, theme: String): Palette {
            val systemDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (theme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            return if (dark) {
                Palette(0xFF1F2226.toInt(), 0xFFE3E2E6.toInt(), 0xFF9AA0A8.toInt(), 0xFFA6C8FF.toInt(), 0xFFFF7A7A.toInt())
            } else {
                Palette(0xFFFFFFFF.toInt(), 0xFF1A1C1E.toInt(), 0xFF6B7280.toInt(), 0xFF1F5FAF.toInt(), 0xFFD32F2F.toInt())
            }
        }

        /** Перекрашиваем белую фигуру фона и задаём прозрачность (0–100 %). */
        private fun applyBackground(views: RemoteViews, color: Int, opacityPercent: Int) {
            views.setInt(R.id.widget_bg, "setColorFilter", color)
            views.setInt(R.id.widget_bg, "setImageAlpha", (opacityPercent.coerceIn(0, 100) * 255) / 100)
        }

        private fun showEmpty(views: RemoteViews, text: String) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, text)
        }

        private fun title(date: LocalDate, today: LocalDate): String {
            val base = "${DAY_NAMES[date.dayOfWeek.value - 1]} ${date.format(DM)}"
            return when (date) {
                today -> "Сегодня, $base"
                today.plusDays(1) -> "Завтра, $base"
                else -> base.replaceFirstChar { it.uppercase() }
            }
        }
    }
}
