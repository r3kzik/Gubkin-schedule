package com.vadik.raspisanie.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.CaptchaRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Фоновое обновление: раз в несколько часов скачивает текущую и следующую неделю,
 * сравнивает с сохранённой версией и присылает уведомление, если что-то поменялось.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as App
        val repo = app.repo
        val settings = repo.settings() ?: return@withContext Result.success()
        val today = LocalDate.now()
        val changes = mutableListOf<String>()
        try {
            for ((i, day) in listOf(today, today.plusWeeks(1)).withIndex()) {
                if (i > 0) delay(2_000) // не частим запросами к сайту
                changes += repo.refreshWeek(settings, day, today).changes
            }
            Notifier.clearCaptcha(applicationContext)
        } catch (e: CaptchaRequiredException) {
            maybeNotifyCaptcha()
        } catch (e: Exception) {
            // нет сети / сайт лежит — попробуем в следующий раз
        }
        val prefs = repo.prefs()
        if (prefs.changeNotify) Notifier.scheduleChanged(applicationContext, changes)
        AppSync.afterDataChange(applicationContext)
        Updater.backgroundCheck(applicationContext, prefs.autoUpdateCheck, prefs.autoInstall)
        Result.success()
    }

    /** Напоминание про капчу — не чаще раза в сутки. */
    private fun maybeNotifyCaptcha() {
        val prefs = applicationContext.getSharedPreferences("worker", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("captcha_notified", null) == today) return
        prefs.edit().putString("captcha_notified", today).apply()
        Notifier.captchaNeeded(applicationContext)
    }

    companion object {
        private const val NAME = "schedule-refresh"
        const val INTERVAL_HOURS = 3L

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
