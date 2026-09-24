package com.vadik.raspisanie.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Upcoming
import com.vadik.raspisanie.widget.ScheduleWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** После любого изменения данных или настроек: перепланировать напоминание и обновить виджет. */
object AppSync {
    fun afterDataChange(ctx: Context) {
        runCatching { ReminderScheduler.schedule(ctx) }
        runCatching { com.vadik.raspisanie.widget.WidgetKit.updateAll(ctx) }
    }
}

/**
 * Напоминания о начале пары. В каждый момент запланирован ровно один будильник —
 * на ближайшую пару; когда он срабатывает, планируется следующий.
 */
object ReminderScheduler {
    private const val REQUEST_CODE = 100
    const val ACTION_REMIND = "com.vadik.raspisanie.REMIND"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    fun schedule(ctx: Context) {
        val app = ctx.applicationContext as App
        val repo = app.repo
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = repo.prefs()
        val settings = repo.settings()
        if (!prefs.remindEnabled || settings == null) {
            cancel(ctx, am)
            return
        }
        val today = LocalDate.now()
        val weeks = listOfNotNull(
            repo.cachedWeek(settings.groupId, today),
            repo.cachedWeek(settings.groupId, today.plusWeeks(1)),
        )
        val next = Upcoming.nextReminder(weeks, prefs, LocalDateTime.now())
        if (next == null) {
            cancel(ctx, am)
            return
        }
        val minutes = java.time.Duration.between(next.at, next.start).toMinutes()
        val title = if (minutes > 0) "Через $minutes мин: ${titleOf(next.lessons)}" else titleOf(next.lessons)
        val text = next.lessons.joinToString("\n") { describe(it) }
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
        val pi = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, am: AlarmManager) {
        val intent = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        val pi = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun titleOf(lessons: List<Lesson>): String =
        if (lessons.size == 1) lessons[0].subject else lessons.joinToString(" / ") { it.subject }

    private fun describe(l: Lesson): String = buildList {
        add("${l.start}–${l.end}".trimEnd('–'))
        l.kind?.let { add(it) }
        l.room?.let { add("ауд. $it") }
        l.teacher?.let { add(it) }
        l.subgroup?.let { add("$it подгр.") }
    }.joinToString(" · ")
}

/** Срабатывает по будильнику: показывает напоминание и планирует следующее. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_REMIND) {
            val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Скоро пара"
            val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty()
            Notifier.lessonReminder(context, title, text)
        }
        AppSync.afterDataChange(context)
    }
}

/** После перезагрузки телефона, обновления приложения или смены времени будильники нужно ставить заново. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppSync.afterDataChange(context)
    }
}
