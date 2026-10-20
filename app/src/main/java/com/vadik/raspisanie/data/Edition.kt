package com.vadik.raspisanie.data

import android.content.Context

/**
 * Редакция приложения. MyGub Lite — тот же код, но без стекла, живого фона
 * и анимаций переходов: для старых и слабых телефонов.
 */
object Edition {
    /** true в MyGub Lite. Задаётся один раз при запуске и больше не меняется. */
    @Volatile
    var lite: Boolean = false
        private set

    /** Стили без полупрозрачного стекла — только они доступны в Lite. */
    val LITE_STYLES = setOf("material", "ios", "amoled", "paper", "catppuccin", "nord", "mono")

    /** true в MyGub Beta — только там есть вкладка «Игры». */
    @Volatile
    var beta: Boolean = false
        private set

    fun init(ctx: Context) {
        lite = ctx.packageName.endsWith(".lite")
        beta = ctx.packageName.endsWith(".beta")
    }

    /** Настройки с учётом редакции: в Lite стеклянные стили заменяются на Material. */
    fun effective(p: Prefs): Prefs =
        if (!lite) p
        else p.copy(
            style = if (p.style in LITE_STYLES) p.style else "material",
            animatedBackground = false,
        )
}
