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
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.NowStatus
import com.vadik.raspisanie.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * «Текущая пара» в шторке: предмет, аудитория и обратный отсчёт до конца пары,
 * на перемене — какая пара следующая и где. Обновляется будильником на границах пар
 * и раз в несколько минут во время пары (для полоски прогресса).
 */
object LessonNow {
    private const val CHANNEL = "now"
    private const val ID = 5
    private const val REQUEST_CODE = 102
    const val ACTION_NOW = "com.vadik.raspisanie.NOW"
    private const val PROGRESS_STEP_MIN = 5L

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Текущая пара", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Постоянное уведомление: какая пара идёт, где и сколько осталось"
                setShowBadge(false)
            },
        )
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun room(l: Lesson) = l.room?.let { if (it.first().isDigit()) "ауд. $it" else it } ?: "аудитория не указана"

    private fun title(ls: List<Lesson>) = ls.joinToString(" / ") { it.subject }

    fun update(ctx: Context) {
        val repo = (ctx.applicationContext as App).repo
        val prefs = repo.prefs()
        val settings = repo.settings()
        val nm = NotificationManagerCompat.from(ctx)
        if (!prefs.ongoingLesson || settings == null || !canNotify(ctx)) {
            nm.cancel(ID)
            cancelAlarm(ctx)
            return
        }
        val now = LocalDateTime.now()
        val week = repo.cachedWeek(settings.groupId, LocalDate.now())
        val state = NowStatus.at(week, prefs, now)
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 3,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        var nextCheck = NowStatus.nextCheck(week, prefs, now)
        when (state) {
            is NowStatus.State.InLesson -> {
                val l = state.lessons.first()
                val total = Duration.between(state.start, state.end).toMinutes().toInt().coerceAtLeast(1)
                val done = Duration.between(state.start, now).toMinutes().toInt().coerceIn(0, total)
                val details = listOfNotNull(
                    room(l),
                    "до ${l.end}",
                    state.next?.let { "дальше ${it.start}, ${room(it)}" },
                ).joinToString(" · ")
                b.setContentTitle(title(state.lessons))
                    .setContentText(details)
                    .setSubText("Идёт пара")
                    .setWhen(millis(state.end))
                    .setProgress(total, done, false)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        (state.lessons.map { "${it.subject} — ${room(it)}" + (it.teacher?.let { t -> " · $t" } ?: "") } +
                            listOfNotNull(state.next?.let { "Дальше в ${it.start}: ${it.subject} — ${room(it)}" }))
                            .joinToString("\n"),
                    ))
                // полоска прогресса: подвинуть через несколько минут
                val step = now.plusMinutes(PROGRESS_STEP_MIN)
                if (nextCheck == null || step.isBefore(nextCheck)) nextCheck = step
            }
            is NowStatus.State.Before -> {
                val l = state.lessons.first()
                b.setContentTitle("${if (state.first) "Скоро" else "Дальше"}: ${title(state.lessons)}")
                    .setContentText("в ${l.start} · ${room(l)}")
                    .setSubText(if (state.first) "Первая пара" else "Перемена")
                    .setWhen(millis(state.start))
            }
            NowStatus.State.Nothing -> {
                nm.cancel(ID)
                scheduleAlarm(ctx, nextCheck ?: LocalDate.now().plusDays(1).atTime(0, 10))
                return
            }
        }
        try {
            nm.notify(ID, b.build())
        } catch (e: SecurityException) {
        }
        scheduleAlarm(ctx, nextCheck ?: LocalDate.now().plusDays(1).atTime(0, 10))
    }

    private fun alarmIntent(ctx: Context, flags: Int) = PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_NOW),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(ctx: Context, at: LocalDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx, PendingIntent.FLAG_UPDATE_CURRENT)
        val t = millis(at)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC, t, pi)
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, t, pi) }
        }
    }

    private fun cancelAlarm(ctx: Context) {
        val pi = alarmIntent(ctx, PendingIntent.FLAG_NO_CREATE) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }
}
