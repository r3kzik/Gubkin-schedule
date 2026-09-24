package com.vadik.raspisanie.data

import kotlinx.serialization.json.Json

/** Найденное обновление приложения (релиз на GitHub). */
data class UpdateInfo(
    val version: Long,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

/**
 * Разбор списка релизов GitHub. Каналы:
 *   stable — теги build-N (не pre-release), beta — beta-N, lite — lite-N.
 * Номер N совпадает с versionCode сборки (номер запуска GitHub Actions).
 */
object Updates {
    const val REPO = "r3kzik/Gubkin-schedule"
    const val RELEASES_API = "https://api.github.com/repos/$REPO/releases?per_page=30"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    fun channelFor(packageName: String): String = when {
        packageName.endsWith(".beta") -> "beta"
        packageName.endsWith(".lite") -> "lite"
        else -> "stable"
    }

    private fun prefix(channel: String) = when (channel) {
        "beta" -> "beta-"
        "lite" -> "lite-"
        else -> "build-"
    }

    /** Самый свежий релиз канала новее [current] с APK-файлом, или null. */
    fun pick(releasesJson: String, channel: String, current: Long): UpdateInfo? {
        val arr = Json.parseToJsonElement(releasesJson).arr() ?: return null
        val pre = prefix(channel)
        return arr.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            if (o["draft"].truthy()) return@mapNotNull null
            val tag = o["tag_name"].str() ?: return@mapNotNull null
            if (!tag.startsWith(pre)) return@mapNotNull null
            if (channel == "stable" && o["prerelease"].truthy()) return@mapNotNull null
            val v = tag.removePrefix(pre).toLongOrNull() ?: return@mapNotNull null
            val asset = o["assets"].arr().orEmpty().mapNotNull { it.obj() }
                .firstOrNull { it["name"].str()?.endsWith(".apk") == true } ?: return@mapNotNull null
            UpdateInfo(
                version = v,
                title = o["name"].str() ?: tag,
                notes = o["body"].str().orEmpty(),
                apkUrl = asset["browser_download_url"].str() ?: return@mapNotNull null,
                sizeBytes = asset["size"].str()?.toLongOrNull() ?: 0L,
                pageUrl = o["html_url"].str() ?: RELEASES_PAGE,
            )
        }.filter { it.version > current }.maxByOrNull { it.version }
    }
}
