package com.vadik.raspisanie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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
                views.setTextViewText(R.id.widget_title, "Расписание")
                showEmpty(views, "Откройте приложение и выберите группу")
                return views
            }
            val prefs = repo.prefs()
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
                    val accent = ContextCompat.getColor(ctx, R.color.widget_accent)
                    val normal = ContextCompat.getColor(ctx, R.color.widget_text)
                    val muted = ContextCompat.getColor(ctx, R.color.widget_muted)
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
                            l.room?.let { add("ауд. $it") }
                            l.kind?.let { add(it) }
                        }.joinToString(" · ")
                        row.setTextViewText(R.id.row_details, details)
                        val color = when {
                            i == day.currentIndex -> accent
                            inactive -> muted
                            else -> normal
                        }
                        row.setTextColor(R.id.row_time, if (i == day.currentIndex) accent else color)
                        row.setTextColor(R.id.row_subject, color)
                        views.addView(R.id.widget_list, row)
                    }
                    if (day.lessons.size > MAX_ROWS) {
                        val more = RemoteViews(ctx.packageName, R.layout.widget_row)
                        more.setTextViewText(R.id.row_time, "")
                        more.setTextViewText(R.id.row_subject, "…ещё ${day.lessons.size - MAX_ROWS}")
                        more.setTextViewText(R.id.row_details, "")
                        views.addView(R.id.widget_list, more)
                    }
                }
            }
            return views
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
