package com.vadik.raspisanie.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.vadik.raspisanie.R

/** 2×2 — «Следующая пара»: крупно время, предмет, аудитория с корпусом и этажом. */
class NextLessonWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val c = WidgetKit.load(ctx)
            val p = WidgetKit.palette(ctx, c.prefs)
            val v = RemoteViews(ctx.packageName, R.layout.widget_next)
            WidgetKit.applyBackground(v, p.bg, c.prefs?.widgetOpacity ?: 100)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(ctx))
            val n = WidgetKit.next(c)
            v.setTextViewText(R.id.w_label, n.label)
            v.setTextColor(R.id.w_label, p.accent)
            val l = n.lesson
            if (l == null) {
                v.setTextViewText(R.id.w_time, "")
                v.setTextViewText(R.id.w_subject, n.empty ?: "")
                v.setTextColor(R.id.w_subject, p.text)
                v.setTextViewText(R.id.w_place, "")
                v.setTextViewText(R.id.w_teacher, "")
                v.setViewVisibility(R.id.w_time, View.GONE)
            } else {
                v.setViewVisibility(R.id.w_time, View.VISIBLE)
                v.setTextViewText(R.id.w_time, if (l.end.isNotBlank()) "${l.start}–${l.end}" else l.start)
                v.setTextColor(R.id.w_time, p.text)
                v.setTextViewText(R.id.w_subject, l.subject)
                v.setTextColor(R.id.w_subject, p.text)
                v.setTextViewText(R.id.w_place, WidgetKit.placeLine(l))
                v.setTextColor(R.id.w_place, if (l.roomChanged) p.red else p.muted)
                v.setTextViewText(R.id.w_teacher, listOfNotNull(l.kind, l.teacher).joinToString(" · "))
                v.setTextColor(R.id.w_teacher, if (l.teacherChanged) p.red else p.muted)
            }
            return v
        }
    }
}

/** 1×3 — «Полоска»: одна строка с ближайшей парой. */
class StripWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val c = WidgetKit.load(ctx)
            val p = WidgetKit.palette(ctx, c.prefs)
            val v = RemoteViews(ctx.packageName, R.layout.widget_strip)
            WidgetKit.applyBackground(v, p.bg, c.prefs?.widgetOpacity ?: 100)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(ctx))
            val n = WidgetKit.next(c)
            val l = n.lesson
            if (l == null) {
                v.setTextViewText(R.id.w_time, "📅")
                v.setTextViewText(R.id.w_subject, n.empty ?: "")
                v.setTextViewText(R.id.w_place, n.label.lowercase().replaceFirstChar { it.uppercase() })
            } else {
                v.setTextViewText(R.id.w_time, l.start)
                v.setTextViewText(R.id.w_subject, l.subject)
                v.setTextViewText(
                    R.id.w_place,
                    listOf(n.label.substringBefore(" ·").lowercase().replaceFirstChar { it.uppercase() }, WidgetKit.placeLine(l))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
            v.setTextColor(R.id.w_time, p.accent)
            v.setTextColor(R.id.w_subject, p.text)
            v.setTextColor(R.id.w_place, if (l?.roomChanged == true) p.red else p.muted)
            return v
        }
    }
}

/** 2×3 — «Пары дня (компактный)»: до трёх пар дня. */
class CompactDayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private const val ROWS = 3

        fun build(ctx: Context): RemoteViews {
            val c = WidgetKit.load(ctx)
            val p = WidgetKit.palette(ctx, c.prefs)
            val v = RemoteViews(ctx.packageName, R.layout.widget_compact)
            WidgetKit.applyBackground(v, p.bg, c.prefs?.widgetOpacity ?: 100)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(ctx))
            v.removeAllViews(R.id.widget_list)
            v.setTextColor(R.id.widget_title, p.text)
            v.setTextColor(R.id.widget_group, p.muted)
            v.setTextColor(R.id.widget_empty, p.muted)
            val day = c.day
            val prefs = c.prefs
            if (c.settings == null || day == null || prefs == null) {
                v.setTextViewText(R.id.widget_title, "MyGub")
                v.setTextViewText(R.id.widget_empty, "Откройте приложение и выберите группу")
                v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return v
            }
            val today = c.now.toLocalDate()
            v.setTextViewText(R.id.widget_title, WidgetKit.dayWord(day.date, today).replaceFirstChar { it.uppercase() })
            v.setTextViewText(R.id.widget_group, c.settings.groupName)
            val nowMin = c.now.hour * 60 + c.now.minute
            // на сегодня показываем с текущей/ближайшей пары, прошедшие не тратят место
            val list = if (day.date == today) {
                day.lessons.filter { com.vadik.raspisanie.data.timeKey(it.end.ifBlank { it.start }) > nowMin }
            } else day.lessons
            when {
                !day.known -> {
                    v.setTextViewText(R.id.widget_empty, "Нет данных — откройте MyGub")
                    v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                }
                list.isEmpty() -> {
                    v.setTextViewText(R.id.widget_empty, "Пар нет 🎉")
                    v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                }
                else -> {
                    v.setViewVisibility(R.id.widget_empty, View.GONE)
                    list.take(ROWS).forEach { l ->
                        val row = RemoteViews(ctx.packageName, R.layout.widget_row)
                        val isNow = day.date == today && day.currentIndex >= 0 && day.lessons[day.currentIndex] == l
                        val inactive = l.cancelled || l.moved || prefs.isOtherSubgroup(l)
                        row.setTextViewText(R.id.row_time, l.start)
                        row.setTextViewText(R.id.row_subject, (if (l.cancelled) "✕ " else "") + l.subject)
                        row.setTextViewText(R.id.row_details, WidgetKit.placeLine(l))
                        row.setTextColor(R.id.row_time, if (isNow) p.accent else p.text)
                        row.setTextColor(R.id.row_subject, if (inactive) p.muted else if (isNow) p.accent else p.text)
                        row.setTextColor(R.id.row_details, if (l.roomChanged && !inactive) p.red else p.muted)
                        v.addView(R.id.widget_list, row)
                    }
                    if (list.size > ROWS) {
                        val more = RemoteViews(ctx.packageName, R.layout.widget_row)
                        more.setTextViewText(R.id.row_time, "")
                        more.setTextViewText(R.id.row_subject, "…ещё ${list.size - ROWS}")
                        more.setTextViewText(R.id.row_details, "")
                        more.setTextColor(R.id.row_subject, p.muted)
                        v.addView(R.id.widget_list, more)
                    }
                }
            }
            return v
        }
    }
}
