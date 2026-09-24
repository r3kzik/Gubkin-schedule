package com.vadik.raspisanie.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import com.vadik.raspisanie.data.UpdateInfo
import com.vadik.raspisanie.data.Updates
import com.vadik.raspisanie.security.Integrity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Автообновление: ищет новый релиз своего канала на GitHub, скачивает APK,
 * проверяет, что он подписан тем же ключом, и ставит через системный установщик.
 */
object Updater {

    /** Результат установки для экрана: null — ничего, иначе текст (ошибка или «отменено»). */
    val installMessage = MutableStateFlow<String?>(null)

    /** Открыт ли сейчас экран приложения (из фона Android не даёт показать окно установки). */
    @Volatile
    var inForeground = false

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun currentVersion(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (e: Exception) {
        0L
    }

    /** Есть ли новая версия. null — обновлений нет. Ошибки сети пробрасываются. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(Updates.RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MyGub-Android")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub ответил ${r.code}")
            val body = r.body?.string() ?: throw IOException("Пустой ответ GitHub")
            Updates.pick(body, Updates.channelFor(ctx.packageName), currentVersion(ctx))
        }
    }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** Скачивает APK; [progress] получает долю 0..1 (или -1, если размер неизвестен). */
    suspend fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            // старые файлы не копим
            dir(ctx).listFiles()?.forEach { it.delete() }
            val target = File(dir(ctx), "mygub-${info.version}.apk")
            val part = File(dir(ctx), "mygub-${info.version}.part")
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", "MyGub-Android").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Не удалось скачать (${r.code})")
                val body = r.body ?: throw IOException("Пустой файл")
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastShown) { lastShown = pct; progress(done.toFloat() / total) }
                            } else progress(-1f)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить файл")
            verify(ctx, target, info)
            target
        }

    /** Проверка скачанного файла: тот же пакет, версия новее, та же подпись. */
    @Suppress("DEPRECATION")
    private fun verify(ctx: Context, apk: File, info: UpdateInfo) {
        val pi = ctx.packageManager.getPackageArchiveInfo(apk.path, 0)
        if (pi == null) {
            apk.delete()
            throw IOException("Файл обновления повреждён")
        }
        if (pi.packageName != ctx.packageName) {
            apk.delete()
            throw SecurityException("Файл обновления от другого приложения")
        }
        if (PackageInfoCompat.getLongVersionCode(pi) <= currentVersion(ctx)) {
            apk.delete()
            throw IOException("В релизе ${info.version} лежит не новая версия")
        }
        if (Integrity.apkMatchesSelf(ctx, apk) == false) {
            apk.delete()
            throw SecurityException("Подпись обновления не совпадает — установка отменена")
        }
    }

    /** Разрешено ли приложению ставить APK (Android 8+ спрашивает отдельно). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** Экран системных настроек «Установка неизвестных приложений» для MyGub. */
    fun installPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Передаёт APK системному установщику. Итог придёт в [InstallResultReceiver]. */
    fun install(ctx: Context, apk: File) {
        installMessage.value = null
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: если MyGub сам ставил прошлую версию, обновление пройдёт без лишних окон
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("mygub.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(
                    ctx, id,
                    Intent(ctx, InstallResultReceiver::class.java).setPackage(ctx.packageName),
                    flags,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    // ------------------------------------------------------------ фоновая проверка

    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L

    /** Вызывается из фонового обновления расписания: не чаще раза в 12 часов. */
    suspend fun backgroundCheck(ctx: Context, autoCheck: Boolean, autoInstall: Boolean) {
        if (!autoCheck) return
        val sp = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last_check", 0L) < CHECK_EVERY_MS) return
        val info = try {
            check(ctx)
        } catch (e: Exception) {
            return // нет сети — попробуем в следующий раз
        }
        sp.edit().putLong("last_check", now).apply()
        if (info == null || sp.getLong("notified", 0L) == info.version) return
        if (autoInstall && canInstall(ctx)) {
            try {
                install(ctx, download(ctx, info))
                sp.edit().putLong("notified", info.version).apply()
                return
            } catch (e: Exception) {
                // не вышло — хотя бы расскажем про обновление
            }
        }
        Notifier.updateAvailable(ctx, info)
        sp.edit().putLong("notified", info.version).apply()
    }
}

/** Ответ системного установщика. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Updater.inForeground) {
                    try {
                        ctx.startActivity(confirm)
                        return
                    } catch (e: Exception) {
                        // покажем уведомлением
                    }
                }
                Notifier.updateConfirm(ctx, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.installMessage.value = null
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.installMessage.value = "Установка отменена"
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Updater.installMessage.value =
                    "Android не принял обновление: эта копия подписана другим ключом. " +
                        "Удалите приложение и установите заново с GitHub."
            PackageInstaller.STATUS_FAILURE_STORAGE -> Updater.installMessage.value = "Не хватает места для установки"
            else -> Updater.installMessage.value =
                "Не удалось установить: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "код $status")
        }
    }
}

