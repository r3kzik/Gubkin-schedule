package com.vadik.raspisanie.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.vadik.raspisanie.App
import com.vadik.raspisanie.R
import com.vadik.raspisanie.data.HomeworkDue
import java.time.LocalDate

/** 3×2 — «Домашка»: ближайшие невыполненные задания и сроки. */
class HomeworkWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private const val ROWS = 4

        fun build(ctx: Context): RemoteViews {
            val repo = (ctx.applicationContext as App).repo
            val prefs = repo.prefs()
            val p = WidgetKit.palette(ctx, prefs)
            val v = RemoteViews(ctx.packageName, R.layout.widget_homework)
            WidgetKit.applyBackground(v, p.bg, prefs.widgetOpacity)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(ctx, "subjects"))
            v.removeAllViews(R.id.widget_list)
            v.setTextColor(R.id.widget_title, p.text)
            v.setTextColor(R.id.widget_group, p.muted)
            v.setTextColor(R.id.widget_empty, p.muted)
            val today = LocalDate.now()
            val list = HomeworkDue.pending(repo.homework())
            val urgent = list.count {
                HomeworkDue.urgency(it.due, today).let { u ->
                    u == HomeworkDue.Urgency.OVERDUE || u == HomeworkDue.Urgency.TODAY || u == HomeworkDue.Urgency.TOMORROW
                }
            }
            v.setTextViewText(R.id.widget_title, "Домашка")
            v.setTextViewText(
                R.id.widget_group,
                when {
                    list.isEmpty() -> ""
                    urgent > 0 -> "срочно: $urgent"
                    else -> "всего: ${list.size}"
                },
            )
            v.setTextColor(R.id.widget_group, if (urgent > 0) p.red else p.muted)
            if (list.isEmpty()) {
                v.setTextViewText(R.id.widget_empty, "Всё сделано 🎉\nДобавить ДЗ — во вкладке «Предметы»")
                v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return v
            }
            v.setViewVisibility(R.id.widget_empty, View.GONE)
            list.take(ROWS).forEach { h ->
                val row = RemoteViews(ctx.packageName, R.layout.widget_hw_row)
                val u = HomeworkDue.urgency(h.due, today)
                row.setTextViewText(R.id.row_due, HomeworkDue.label(h.due, today))
                row.setTextColor(
                    R.id.row_due,
                    when (u) {
                        HomeworkDue.Urgency.OVERDUE, HomeworkDue.Urgency.TODAY -> p.red
                        HomeworkDue.Urgency.TOMORROW -> p.accent
                        else -> p.muted
                    },
                )
                row.setTextViewText(R.id.row_subject, h.subject)
                row.setTextColor(R.id.row_subject, p.text)
                row.setTextViewText(R.id.row_text, h.text)
                row.setTextColor(R.id.row_text, p.muted)
                v.addView(R.id.widget_list, row)
            }
            if (list.size > ROWS) {
                val more = RemoteViews(ctx.packageName, R.layout.widget_hw_row)
                more.setTextViewText(R.id.row_due, "")
                more.setTextViewText(R.id.row_subject, "…ещё ${list.size - ROWS}")
                more.setTextColor(R.id.row_subject, p.muted)
                more.setTextViewText(R.id.row_text, "")
                v.addView(R.id.widget_list, more)
            }
            return v
        }
    }
}
