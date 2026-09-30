package com.vadik.raspisanie.work

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vadik.raspisanie.App
import com.vadik.raspisanie.R
import com.vadik.raspisanie.data.Morning
import com.vadik.raspisanie.data.timeKey
import com.vadik.raspisanie.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Утренняя сводка: одно уведомление в выбранное время — какие сегодня пары, где первая, что сдавать. */
object MorningSummary {
    private const val CHANNEL = "morning"
    private const val ID = 6
    private const val REQUEST_CODE = 103
    const val ACTION = "com.vadik.raspisanie.MORNING"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Утренняя сводка", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Раз в день: пары, первая аудитория и ДЗ на сегодня" },
        )
    }

    private fun intent(ctx: Context, flags: Int) = PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Следующий момент сводки: сегодня в выбранное время или завтра. */
    fun nextTime(time: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val m = timeKey(time).takeIf { it < 24 * 60 } ?: (7 * 60 + 30)
        val today = now.toLocalDate().atTime(m / 60, m % 60)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Поставить (или снять) будильник сводки. */
    fun schedule(ctx: Context) {
        val prefs = (ctx.applicationContext as App).repo.prefs()
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (!prefs.morningEnabled) {
            intent(ctx, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
            return
        }
        val at = nextTime(prefs.morningTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = intent(ctx, PendingIntent.FLAG_UPDATE_CURRENT)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    /** Показать сводку. [force] — даже если сегодня нечего сообщать (кнопка «Показать пример»). */
    fun show(ctx: Context, force: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val repo = (ctx.applicationContext as App).repo
        val settings = repo.settings() ?: return false
        val today = LocalDate.now()
        val summary = Morning.build(repo.cachedWeek(settings.groupId, today), repo.prefs(), repo.homework(), today)
            ?: if (force) Morning.Summary("Сегодня пар нет 🎉", listOf("Так будет выглядеть утренняя сводка")) else return false
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 4,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle(summary.title)
            .setContentText(summary.lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 60 * 1000L)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(ID, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
