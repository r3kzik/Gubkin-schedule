package com.vadik.raspisanie.work

import android.Manifest
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
import com.vadik.raspisanie.R
import com.vadik.raspisanie.ui.MainActivity

object Notifier {
    private const val CHANNEL_CHANGES = "changes"
    private const val CHANNEL_SERVICE = "service"
    private const val ID_CHANGES = 1
    private const val ID_CAPTCHA = 2

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHANGES, "Изменения в расписании", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Отмены, переносы, смена аудитории или преподавателя" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Служебные", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Например, когда сайт просит ввести капчу" }
        )
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun scheduleChanged(ctx: Context, changes: List<String>) {
        if (changes.isEmpty() || !canNotify(ctx)) return
        val shown = changes.take(8)
        val text = shown.joinToString("\n") +
            if (changes.size > shown.size) "\n…и ещё ${changes.size - shown.size}" else ""
        val n = NotificationCompat.Builder(ctx, CHANNEL_CHANGES)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle("Расписание изменилось")
            .setContentText(shown.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_CHANGES, n)
        } catch (e: SecurityException) {
            // разрешение отозвали — просто молчим
        }
    }

    fun captchaNeeded(ctx: Context) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle("Сайт вуза просит капчу")
            .setContentText("Откройте приложение и введите код, чтобы расписание снова обновлялось")
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_CAPTCHA, n)
        } catch (e: SecurityException) {
        }
    }

    fun clearCaptcha(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CAPTCHA)
    }
}
