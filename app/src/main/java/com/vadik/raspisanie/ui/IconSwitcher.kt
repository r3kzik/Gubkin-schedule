package com.vadik.raspisanie.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vadik.raspisanie.data.Accents
import com.vadik.raspisanie.data.Prefs

/**
 * Иконка приложения в цвет темы. В манифесте объявлены ярлыки запуска (activity-alias)
 * LauncherBlue, LauncherViolet, … — включаем нужный и выключаем остальные.
 * Вызывается при уходе приложения в фон, чтобы не мешать работе.
 */
object IconSwitcher {
    private const val NS = "com.vadik.raspisanie"

    private fun cls(id: String) = "$NS.Launcher" + id.replaceFirstChar { it.uppercase() }

    fun apply(ctx: Context, prefs: Prefs) {
        val target = if (!prefs.iconFollowsAccent || prefs.accent == "dynamic") "blue"
        else Accents.nearestPresetId(prefs.accent)
        val pm = ctx.packageManager
        fun enabled(id: String): Boolean {
            val st = pm.getComponentEnabledSetting(ComponentName(ctx.packageName, cls(id)))
            return when (st) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> id == "blue"
                else -> false
            }
        }
        try {
            // сначала включаем нужный ярлык, потом выключаем остальные — так ярлык есть всегда
            if (!enabled(target)) {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, cls(target)),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            Accents.presets.map { it.id }.filter { it != target && enabled(it) }.forEach { id ->
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, cls(id)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (e: Exception) {
            // на некоторых оболочках смена ярлыка запрещена — просто оставляем как есть
        }
    }
}
