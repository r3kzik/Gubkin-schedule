package com.vadik.raspisanie.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Защита от подделок: приложение знает отпечаток своего настоящего ключа подписи.
 * Если APK изменили (например, убрали автора) и переподписали чужим ключом — отпечаток
 * не совпадёт, и приложение покажет «неоригинальная копия».
 * Тем же способом проверяется скачанное обновление перед установкой.
 */
object Integrity {
    // SHA-256 сертификата MyGub (частями и задом наперёд, чтобы не находился простым поиском)
    private val P = arrayOf("7C427AF572A1991E", "A9FB635D8DAC0854", "743A259D22887833", "A0D503A953511ECC")
    private val expected: String get() = P.joinToString("") { it.reversed() }

    private fun sha256(sig: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }

    @Suppress("DEPRECATION")
    private fun signaturesOf(ctx: Context): List<Signature> {
        val pm = ctx.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
    }

    /** true — установлена оригинальная сборка MyGub. */
    fun isOriginal(ctx: Context): Boolean = try {
        signaturesOf(ctx).any { sha256(it) == expected }
    } catch (e: Exception) {
        true // не смогли проверить — не мешаем пользоваться
    }

    /**
     * Подписан ли скачанный APK тем же ключом, что и установленное приложение.
     * null — проверить не удалось (тогда полагаемся на проверку самого Android при установке).
     */
    @Suppress("DEPRECATION")
    fun apkMatchesSelf(ctx: Context, apk: File): Boolean? = try {
        val pm = ctx.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners?.toList()
        } else {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)?.signatures?.toList()
        }
        if (sigs.isNullOrEmpty()) null
        else {
            val mine = signaturesOf(ctx).map { sha256(it) }.toSet()
            sigs.any { sha256(it) in mine }
        }
    } catch (e: Exception) {
        null
    }
}
