package com.vadik.raspisanie.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.vadik.raspisanie.data.Accents

/** Цвета «стекла» и фона, которые нужны поверх Material-схемы. */
@Immutable
data class GlassColors(
    val dark: Boolean,
    /** Заливка стеклянной карточки. */
    val fill: Color,
    /** Более плотная заливка (шапка, выбранные элементы). */
    val fillStrong: Color,
    /** Светлый блик сверху карточки. */
    val sheen: Color,
    /** Рамка: сверху светлее, снизу прозрачнее. */
    val borderTop: Color,
    val borderBottom: Color,
    /** Три цвета пятен «северного сияния». */
    val aurora: List<Color>,
    val base: Color,
    /** Красный для изменений в расписании. */
    val changed: Color,
    /** Стиль: glass | night | ios | material */
    val style: String = "glass",
    /** Однотонный фон без «северного сияния». */
    val flat: Boolean = false,
    /** Множитель скругления углов (стиль × настройка пользователя). */
    val cornerScale: Float = 1f,
    /** Насколько карточка «проседает» при нажатии. */
    val pressScale: Float = 0.965f,
    /** Фон — яркий градиент во весь экран (стиль «Градиент»). */
    val gradient: Boolean = false,
)

val LocalGlass = staticCompositionLocalOf {
    GlassColors(
        false, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.15f),
        listOf(Color(0xFF3D7BFF), Color(0xFF7C5CFF), Color(0xFF14B8B0)), Color(0xFFF3F5FB), Color(0xFFD32F2F),
    )
}

/** true, если должна быть тёмная тема с учётом выбора в настройках. */
@Composable
fun isDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

// ---------------------------------------------------------------- генерация схемы из одного цвета

private fun hsl(argb: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }

private fun tone(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Своя Material-схема из «семечка» цвета: оттенки подобраны вручную для светлой и тёмной темы. */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val (h, s0, l0) = hsl(seed.toArgb()).let { Triple(it[0], it[1], it[2]) }
    val s = s0.coerceAtLeast(0.12f)
    // «Тёмные» цвета (тёмно-зелёный, бордо…) не осветляем до среднего тона — интерфейс остаётся глубоким
    val deep = l0 < 0.33f
    val lp = if (deep) l0.coerceIn(0.18f, 0.3f) + 0.02f else 0.47f   // основной (светлая тема)
    val ls = if (deep) 0.30f else 0.42f                              // вторичные (светлая тема)
    val dp = if (deep) 0.66f else 0.76f                              // основной (тёмная тема)
    val tint = if (deep) 0.55f else 0.3f                             // насколько фон окрашен в цвет
    val h2 = h + 35f   // вторичный
    val h3 = h - 40f   // третичный
    return if (!dark) lightColorScheme(
        primary = tone(h, s * 0.95f, lp),
        onPrimary = Color.White,
        primaryContainer = tone(h, s * 0.9f, 0.88f),
        onPrimaryContainer = tone(h, s, 0.16f),
        secondary = tone(h2, s * 0.55f, ls),
        onSecondary = Color.White,
        secondaryContainer = tone(h2, s * 0.6f, 0.89f),
        onSecondaryContainer = tone(h2, s * 0.6f, 0.15f),
        tertiary = tone(h3, s * 0.6f, ls),
        onTertiary = Color.White,
        tertiaryContainer = tone(h3, s * 0.65f, 0.89f),
        onTertiaryContainer = tone(h3, s * 0.6f, 0.15f),
        background = tone(h, s * (tint + 0.05f), if (deep) 0.95f else 0.965f),
        onBackground = tone(h, 0.15f, 0.11f),
        surface = tone(h, s * 0.3f, 0.975f),
        onSurface = tone(h, 0.15f, 0.11f),
        surfaceVariant = tone(h, s * 0.25f, 0.91f),
        onSurfaceVariant = tone(h, 0.1f, 0.35f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(h, s * 0.3f, 0.96f),
        surfaceContainer = tone(h, s * 0.3f, 0.945f),
        surfaceContainerHigh = tone(h, s * 0.3f, 0.93f),
        surfaceContainerHighest = tone(h, s * 0.3f, 0.91f),
        outline = tone(h, 0.1f, 0.55f),
        outlineVariant = tone(h, 0.15f, 0.82f),
        error = Color(0xFFD32F2F),
    ) else darkColorScheme(
        primary = tone(h, s * 0.9f, dp),
        onPrimary = tone(h, s, 0.14f),
        primaryContainer = tone(h, s * 0.7f, if (deep) 0.24f else 0.30f),
        onPrimaryContainer = tone(h, s * 0.9f, 0.90f),
        secondary = tone(h2, s * 0.5f, 0.76f),
        onSecondary = tone(h2, s * 0.6f, 0.14f),
        secondaryContainer = tone(h2, s * 0.4f, 0.28f),
        onSecondaryContainer = tone(h2, s * 0.5f, 0.90f),
        tertiary = tone(h3, s * 0.5f, 0.76f),
        onTertiary = tone(h3, s * 0.6f, 0.14f),
        tertiaryContainer = tone(h3, s * 0.4f, 0.28f),
        onTertiaryContainer = tone(h3, s * 0.5f, 0.90f),
        background = tone(h, s * tint, if (deep) 0.055f else 0.065f),
        onBackground = tone(h, 0.1f, 0.92f),
        surface = tone(h, s * (tint - 0.05f), 0.08f),
        onSurface = tone(h, 0.1f, 0.92f),
        surfaceVariant = tone(h, s * 0.2f, 0.19f),
        onSurfaceVariant = tone(h, 0.1f, 0.74f),
        surfaceContainerLowest = tone(h, s * 0.25f, 0.05f),
        surfaceContainerLow = tone(h, s * 0.25f, 0.10f),
        surfaceContainer = tone(h, s * 0.25f, 0.12f),
        surfaceContainerHigh = tone(h, s * 0.25f, 0.15f),
        surfaceContainerHighest = tone(h, s * 0.25f, 0.18f),
        outline = tone(h, 0.1f, 0.5f),
        outlineVariant = tone(h, 0.12f, 0.28f),
        error = Color(0xFFFF7A7A),
    )
}

/** Плавная смена цветов при переключении темы или акцента. */
@Composable
private fun a(c: Color, label: String): Color = animateColorAsState(c, tween(600), label = label).value

@Composable
private fun animated(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = a(target.primary, "p"),
        onPrimary = a(target.onPrimary, "op"),
        primaryContainer = a(target.primaryContainer, "pc"),
        onPrimaryContainer = a(target.onPrimaryContainer, "opc"),
        secondary = a(target.secondary, "s"),
        secondaryContainer = a(target.secondaryContainer, "sc"),
        onSecondaryContainer = a(target.onSecondaryContainer, "osc"),
        tertiary = a(target.tertiary, "t"),
        tertiaryContainer = a(target.tertiaryContainer, "tc"),
        onTertiaryContainer = a(target.onTertiaryContainer, "otc"),
        background = a(target.background, "b"),
        onBackground = a(target.onBackground, "ob"),
        surface = a(target.surface, "su"),
        onSurface = a(target.onSurface, "osu"),
        surfaceVariant = a(target.surfaceVariant, "sv"),
        onSurfaceVariant = a(target.onSurfaceVariant, "osv"),
        outlineVariant = a(target.outlineVariant, "ov"),
        surfaceContainer = a(target.surfaceContainer, "scn"),
        surfaceContainerHigh = a(target.surfaceContainerHigh, "sch"),
        surfaceContainerHighest = a(target.surfaceContainerHighest, "schh"),
    )
}

/** Все стили оформления приложения. */
data class StyleInfo(val id: String, val title: String, val description: String)

val STYLES = listOf(
    StyleInfo("material", "Material You", "Стиль Android: цвета из обоев, тональные карточки"),
    StyleInfo("glass", "Жидкое стекло", "Полупрозрачные карточки и живой цветной фон"),
    StyleInfo("night", "Ночное стекло", "Тёмное дымчатое стекло с неоновым свечением"),
    StyleInfo("ios", "Как в iOS", "Чистые сгруппированные списки, как в «Настройках» iPhone"),
    StyleInfo("gradient", "Градиент", "Сочный переливающийся фон и белые матовые карточки"),
    StyleInfo("amoled", "AMOLED", "Чистый чёрный с тонкой цветной обводкой — бережёт батарею"),
    StyleInfo("paper", "Бумага", "Тёплый кремовый фон и спокойные карточки, как в заметках"),
    StyleInfo("catppuccin", "Catppuccin", "Мягкая пастельная палитра: Latte днём, Mocha ночью"),
    StyleInfo("nord", "Nord", "Холодные северные тона — спокойно для глаз"),
    StyleInfo("sunset", "Закат", "Персиковый, коралловый и сиреневый градиент"),
    StyleInfo("ocean", "Океан", "Глубокое тёмное стекло с бирюзовым свечением"),
    StyleInfo("pastel", "Зефир", "Нежные пастельные переливы и светлые карточки"),
    StyleInfo("mono", "Минимализм", "Только чёрное и белое, тонкие линии, ничего лишнего"),
)

/** Стили со своей палитрой: выбранный цвет интерфейса в них не применяется. */
val FIXED_PALETTE_STYLES = setOf("catppuccin", "nord", "sunset", "ocean", "pastel", "mono")

/** Стили с полупрозрачным стеклом (для них есть настройка плотности и «живой фон»). */
val GLASSY_STYLES = setOf("glass", "night", "gradient", "ocean", "sunset", "pastel")

/** Тёмная ли тема с учётом стиля: «Ночное стекло» всегда тёмное. */
@Composable
fun effectiveDark(style: String, mode: String): Boolean =
    style == "night" || style == "amoled" || style == "ocean" || isDark(mode)

/** iOS: нейтральные системные серые вместо тонированных поверхностей. */
private fun iosNeutral(base: ColorScheme, dark: Boolean): ColorScheme = if (!dark) base.copy(
    background = Color(0xFFF2F2F7), surface = Color.White, onSurface = Color(0xFF111114),
    onBackground = Color(0xFF111114), surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C72),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF2F2F7),
    surfaceContainerLow = Color.White, outlineVariant = Color(0xFFD1D1D6),
) else base.copy(
    background = Color.Black, surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7),
    onBackground = Color(0xFFF2F2F7), surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceContainerLow = Color(0xFF1C1C1E), outlineVariant = Color(0xFF38383A),
)

/** Ночное стекло: фон почти чёрный с оттенком акцента. */
private fun nightScheme(base: ColorScheme): ColorScheme {
    val h = FloatArray(3).also { ColorUtils.colorToHSL(base.primary.toArgb(), it) }[0]
    val bg = Color(ColorUtils.HSLToColor(floatArrayOf(h, 0.35f, 0.035f)))
    return base.copy(background = bg, surface = bg, onBackground = Color(0xFFEDEDF4), onSurface = Color(0xFFEDEDF4))
}

private fun c(hex: Long) = Color(hex)

/** Готовые палитры стилей, которые не зависят от выбранного цвета. */
private fun fixedScheme(style: String, dark: Boolean): ColorScheme? = when (style) {
    // Catppuccin: Latte / Mocha
    "catppuccin" -> if (dark) darkColorScheme(
        primary = c(0xFFCBA6F7), onPrimary = c(0xFF11111B), primaryContainer = c(0xFF45385F), onPrimaryContainer = c(0xFFEBDDFF),
        secondary = c(0xFF89B4FA), onSecondary = c(0xFF11111B), secondaryContainer = c(0xFF2F3B5C), onSecondaryContainer = c(0xFFD6E4FF),
        tertiary = c(0xFFF5C2E7), onTertiary = c(0xFF11111B), tertiaryContainer = c(0xFF5A3A55), onTertiaryContainer = c(0xFFFFE3F6),
        background = c(0xFF1E1E2E), onBackground = c(0xFFCDD6F4), surface = c(0xFF1E1E2E), onSurface = c(0xFFCDD6F4),
        surfaceVariant = c(0xFF313244), onSurfaceVariant = c(0xFFA6ADC8), surfaceContainerLowest = c(0xFF11111B),
        surfaceContainerLow = c(0xFF181825), surfaceContainer = c(0xFF26263A), surfaceContainerHigh = c(0xFF313244),
        surfaceContainerHighest = c(0xFF45475A), outline = c(0xFF6C7086), outlineVariant = c(0xFF45475A), error = c(0xFFF38BA8),
    ) else lightColorScheme(
        primary = c(0xFF8839EF), onPrimary = Color.White, primaryContainer = c(0xFFE8DAFF), onPrimaryContainer = c(0xFF3A1170),
        secondary = c(0xFF1E66F5), onSecondary = Color.White, secondaryContainer = c(0xFFDCE6FF), onSecondaryContainer = c(0xFF0B2A6B),
        tertiary = c(0xFFEA76CB), onTertiary = Color.White, tertiaryContainer = c(0xFFFBE0F3), onTertiaryContainer = c(0xFF5E1B4D),
        background = c(0xFFEFF1F5), onBackground = c(0xFF4C4F69), surface = c(0xFFF8F9FB), onSurface = c(0xFF4C4F69),
        surfaceVariant = c(0xFFCCD0DA), onSurfaceVariant = c(0xFF6C6F85), surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0xFFF4F5F8), surfaceContainer = c(0xFFE6E9EF), surfaceContainerHigh = c(0xFFFBFBFD),
        surfaceContainerHighest = c(0xFFDCE0E8), outline = c(0xFF9CA0B0), outlineVariant = c(0xFFBCC0CC), error = c(0xFFD20F39),
    )
    // Nord: Snow Storm / Polar Night + Frost
    "nord" -> if (dark) darkColorScheme(
        primary = c(0xFF88C0D0), onPrimary = c(0xFF2E3440), primaryContainer = c(0xFF3E5663), onPrimaryContainer = c(0xFFD8EEF4),
        secondary = c(0xFF81A1C1), onSecondary = c(0xFF2E3440), secondaryContainer = c(0xFF3D4B60), onSecondaryContainer = c(0xFFDCE6F2),
        tertiary = c(0xFFB48EAD), onTertiary = c(0xFF2E3440), tertiaryContainer = c(0xFF524559), onTertiaryContainer = c(0xFFF0E1EE),
        background = c(0xFF2E3440), onBackground = c(0xFFECEFF4), surface = c(0xFF2E3440), onSurface = c(0xFFECEFF4),
        surfaceVariant = c(0xFF434C5E), onSurfaceVariant = c(0xFFB8C1D1), surfaceContainerLowest = c(0xFF272C36),
        surfaceContainerLow = c(0xFF323846), surfaceContainer = c(0xFF353B49), surfaceContainerHigh = c(0xFF3B4252),
        surfaceContainerHighest = c(0xFF434C5E), outline = c(0xFF7B879C), outlineVariant = c(0xFF4C566A), error = c(0xFFBF616A),
    ) else lightColorScheme(
        primary = c(0xFF5E81AC), onPrimary = Color.White, primaryContainer = c(0xFFD6E2F0), onPrimaryContainer = c(0xFF1F3450),
        secondary = c(0xFF81A1C1), onSecondary = Color.White, secondaryContainer = c(0xFFDDE7F2), onSecondaryContainer = c(0xFF22364D),
        tertiary = c(0xFFB48EAD), onTertiary = Color.White, tertiaryContainer = c(0xFFEFE2EC), onTertiaryContainer = c(0xFF4A3346),
        background = c(0xFFE5E9F0), onBackground = c(0xFF2E3440), surface = c(0xFFF2F4F8), onSurface = c(0xFF2E3440),
        surfaceVariant = c(0xFFD8DEE9), onSurfaceVariant = c(0xFF4C566A), surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0xFFF5F7FA), surfaceContainer = c(0xFFECEFF4), surfaceContainerHigh = c(0xFFF7F8FB),
        surfaceContainerHighest = c(0xFFD8DEE9), outline = c(0xFF8A94A8), outlineVariant = c(0xFFD8DEE9), error = c(0xFFBF616A),
    )
    // Закат: персик → коралл → сирень
    "sunset" -> if (dark) darkColorScheme(
        primary = c(0xFFFF9A76), onPrimary = c(0xFF3A1206), primaryContainer = c(0xFF6B2C23), onPrimaryContainer = c(0xFFFFDBCF),
        secondary = c(0xFFE7A6D8), onSecondary = c(0xFF3D1033), secondaryContainer = c(0xFF3E2459), onSecondaryContainer = c(0xFFF3DAFF),
        tertiary = c(0xFFFFC27A), onTertiary = c(0xFF3D2300), tertiaryContainer = c(0xFF5E2344), onTertiaryContainer = c(0xFFFFD9E6),
        background = c(0xFF1A0F14), onBackground = c(0xFFFBE9E4), surface = c(0xFF22141A), onSurface = c(0xFFFBE9E4),
        surfaceVariant = c(0xFF3A262D), onSurfaceVariant = c(0xFFCDB0AA), surfaceContainerLowest = c(0xFF140A0F),
        surfaceContainerLow = c(0xFF22141A), surfaceContainer = c(0xFF2A1920), surfaceContainerHigh = c(0xFF331E26),
        surfaceContainerHighest = c(0xFF3C232C), outline = c(0xFF8C6E70), outlineVariant = c(0xFF4A2F37), error = c(0xFFFF8A80),
    ) else lightColorScheme(
        primary = c(0xFFE0533D), onPrimary = Color.White, primaryContainer = c(0xFFFFD6B8), onPrimaryContainer = c(0xFF4A1405),
        secondary = c(0xFFB5528F), onSecondary = Color.White, secondaryContainer = c(0xFFE4C1F9), onSecondaryContainer = c(0xFF3C0F35),
        tertiary = c(0xFFD9822B), onTertiary = Color.White, tertiaryContainer = c(0xFFFFB3B8), onTertiaryContainer = c(0xFF4A1016),
        background = c(0xFFFFF4EC), onBackground = c(0xFF2A1A1A), surface = c(0xFFFFF9F4), onSurface = c(0xFF2A1A1A),
        surfaceVariant = c(0xFFF6DED5), onSurfaceVariant = c(0xFF6E5555), surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0xFFFFF6F0), surfaceContainer = c(0xFFFFF1E8), surfaceContainerHigh = c(0xFFFFFAF6),
        surfaceContainerHighest = c(0xFFF8E6DC), outline = c(0xFFA88C86), outlineVariant = c(0xFFEBCFC4), error = c(0xFFC62828),
    )
    // Океан: всегда тёмный, бирюза и синева
    "ocean" -> darkColorScheme(
        primary = c(0xFF4DD0E1), onPrimary = c(0xFF00363D), primaryContainer = c(0xFF004F58), onPrimaryContainer = c(0xFF9EEFFD),
        secondary = c(0xFF82B1FF), onSecondary = c(0xFF0A2350), secondaryContainer = c(0xFF16324D), onSecondaryContainer = c(0xFFD3E3FF),
        tertiary = c(0xFF64FFDA), onTertiary = c(0xFF003828), tertiaryContainer = c(0xFF0D3B34), onTertiaryContainer = c(0xFFB9FFEC),
        background = c(0xFF03111C), onBackground = c(0xFFE3F4F8), surface = c(0xFF03111C), onSurface = c(0xFFE3F4F8),
        surfaceVariant = c(0xFF10303D), onSurfaceVariant = c(0xFF9DB8C2), surfaceContainerLowest = c(0xFF020B12),
        surfaceContainerLow = c(0xFF071A26), surfaceContainer = c(0xFF0B2230), surfaceContainerHigh = c(0xFF0F2A3A),
        surfaceContainerHighest = c(0xFF143345), outline = c(0xFF5E7F8C), outlineVariant = c(0xFF1D3F4E), error = c(0xFFFF8A80),
    )
    // Зефир: пастельные сирень, роза и мята
    "pastel" -> if (dark) darkColorScheme(
        primary = c(0xFFBFB1FF), onPrimary = c(0xFF251A5C), primaryContainer = c(0xFF2E2750), onPrimaryContainer = c(0xFFE6DEFF),
        secondary = c(0xFF8FD9C0), onSecondary = c(0xFF00382A), secondaryContainer = c(0xFF1D3A33), onSecondaryContainer = c(0xFFCFF3E6),
        tertiary = c(0xFFF4A8CF), onTertiary = c(0xFF4A1233), tertiaryContainer = c(0xFF43243A), onTertiaryContainer = c(0xFFFFD9EC),
        background = c(0xFF14121C), onBackground = c(0xFFEEEAF7), surface = c(0xFF1B1826), onSurface = c(0xFFEEEAF7),
        surfaceVariant = c(0xFF2C2840), onSurfaceVariant = c(0xFFB6AFCB), surfaceContainerLowest = c(0xFF0F0D16),
        surfaceContainerLow = c(0xFF1B1826), surfaceContainer = c(0xFF211E2E), surfaceContainerHigh = c(0xFF282438),
        surfaceContainerHighest = c(0xFF302B42), outline = c(0xFF7D7596), outlineVariant = c(0xFF3A3450), error = c(0xFFFF8A9A),
    ) else lightColorScheme(
        primary = c(0xFF7C6BD6), onPrimary = Color.White, primaryContainer = c(0xFFE6DEFF), onPrimaryContainer = c(0xFF2A1F63),
        secondary = c(0xFF4FA387), onSecondary = Color.White, secondaryContainer = c(0xFFCFF3E6), onSecondaryContainer = c(0xFF0D3A2C),
        tertiary = c(0xFFD16BA5), onTertiary = Color.White, tertiaryContainer = c(0xFFFFD9EC), onTertiaryContainer = c(0xFF4A1233),
        background = c(0xFFFAF7FF), onBackground = c(0xFF221F2E), surface = Color.White, onSurface = c(0xFF221F2E),
        surfaceVariant = c(0xFFECE6F8), onSurfaceVariant = c(0xFF635D78), surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0xFFFBF9FF), surfaceContainer = c(0xFFF6F2FF), surfaceContainerHigh = Color.White,
        surfaceContainerHighest = c(0xFFEFE9FB), outline = c(0xFFA39CB8), outlineVariant = c(0xFFE2DBF3), error = c(0xFFD32F4F),
    )
    // Минимализм: чёрное и белое
    "mono" -> if (dark) darkColorScheme(
        primary = Color.White, onPrimary = Color.Black, primaryContainer = c(0xFF2A2A2A), onPrimaryContainer = Color.White,
        secondary = c(0xFFBDBDBD), onSecondary = Color.Black, secondaryContainer = c(0xFF262626), onSecondaryContainer = Color.White,
        tertiary = c(0xFF9E9E9E), onTertiary = Color.Black, tertiaryContainer = c(0xFF222222), onTertiaryContainer = Color.White,
        background = c(0xFF0B0B0B), onBackground = c(0xFFF5F5F5), surface = c(0xFF0B0B0B), onSurface = c(0xFFF5F5F5),
        surfaceVariant = c(0xFF1E1E1E), onSurfaceVariant = c(0xFF9A9A9A), surfaceContainerLowest = Color.Black,
        surfaceContainerLow = c(0xFF111111), surfaceContainer = c(0xFF141414), surfaceContainerHigh = c(0xFF161616),
        surfaceContainerHighest = c(0xFF1E1E1E), outline = c(0xFF5C5C5C), outlineVariant = c(0xFF2C2C2C), error = c(0xFFFF6B6B),
    ) else lightColorScheme(
        primary = c(0xFF111111), onPrimary = Color.White, primaryContainer = c(0xFFE6E6E6), onPrimaryContainer = c(0xFF111111),
        secondary = c(0xFF444444), onSecondary = Color.White, secondaryContainer = c(0xFFEDEDED), onSecondaryContainer = c(0xFF111111),
        tertiary = c(0xFF777777), onTertiary = Color.White, tertiaryContainer = c(0xFFF0F0F0), onTertiaryContainer = c(0xFF111111),
        background = c(0xFFF4F4F4), onBackground = c(0xFF0A0A0A), surface = Color.White, onSurface = c(0xFF0A0A0A),
        surfaceVariant = c(0xFFEAEAEA), onSurfaceVariant = c(0xFF6B6B6B), surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0xFFFAFAFA), surfaceContainer = c(0xFFF7F7F7), surfaceContainerHigh = Color.White,
        surfaceContainerHighest = c(0xFFEDEDED), outline = c(0xFF9E9E9E), outlineVariant = c(0xFFDDDDDD), error = c(0xFFD32F2F),
    )
    else -> null
}

/** Цветовая схема для стиля. */
@Composable
fun schemeFor(style: String, dark: Boolean, accent: String): ColorScheme {
    fixedScheme(style, dark)?.let { return it }
    val ctx = LocalContext.current
    val base = when {
        accent == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> schemeFromSeed(Color(Accents.seedOf(accent)), dark)
    }
    return when (style) {
        "ios" -> iosNeutral(
            if (accent == "dynamic") base else base.copy(
                primary = Color(Accents.seedOf(accent)).let { if (dark) lerpToWhite(it, 0.12f) else it },
                onPrimary = Color.White,
            ),
            dark,
        )
        "night" -> nightScheme(base)
        "amoled" -> base.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF0E0E10), surfaceContainerHigh = Color(0xFF151518),
            surfaceContainerHighest = Color(0xFF1C1C20),
        )
        "paper" -> if (!dark) base.copy(
            background = Color(0xFFF5EFE3), surface = Color(0xFFFFFCF6), onSurface = Color(0xFF2B2620),
            onBackground = Color(0xFF2B2620), surfaceVariant = Color(0xFFEDE5D5), onSurfaceVariant = Color(0xFF6E6456),
            surfaceContainer = Color(0xFFFBF6EC), surfaceContainerHigh = Color(0xFFFFFCF6),
            surfaceContainerHighest = Color(0xFFF1EADC), outlineVariant = Color(0xFFE2D8C4),
        ) else base.copy(
            background = Color(0xFF1B1914), surface = Color(0xFF24211B), onSurface = Color(0xFFEDE6D8),
            onBackground = Color(0xFFEDE6D8), surfaceVariant = Color(0xFF2E2A22), onSurfaceVariant = Color(0xFFB5AB98),
            surfaceContainer = Color(0xFF211E18), surfaceContainerHigh = Color(0xFF2A261F),
            surfaceContainerHighest = Color(0xFF332E25), outlineVariant = Color(0xFF3D372C),
        )
        else -> base
    }
}

private fun lerpToWhite(c: Color, f: Float): Color =
    Color(ColorUtils.blendARGB(c.toArgb(), android.graphics.Color.WHITE, f))

/** «Кожа» стиля: заливки карточек, рамки, фон. */
fun skinFor(style: String, scheme: ColorScheme, dark: Boolean, cornerPercent: Int, glassPercent: Int): GlassColors {
    val p = scheme.primary
    val k = glassPercent.coerceIn(50, 150) / 100f
    val corner = cornerPercent.coerceIn(50, 150) / 100f
    fun al(x: Float) = (x * k).coerceIn(0f, 1f)
    return when (style) {
        "night" -> GlassColors(
            dark = true,
            fill = Color(0xFF05060C).copy(alpha = al(0.42f)),
            fillStrong = Color(0xFF05060C).copy(alpha = al(0.58f)),
            sheen = Color.White.copy(alpha = 0.06f),
            borderTop = p.copy(alpha = 0.55f),
            borderBottom = p.copy(alpha = 0.06f),
            aurora = listOf(p.copy(alpha = 0.75f), scheme.tertiary.copy(alpha = 0.6f), scheme.secondary.copy(alpha = 0.55f)),
            base = scheme.background,
            changed = Color(0xFFFF7A7A),
            style = "night", cornerScale = corner,
        )
        "ios" -> GlassColors(
            dark = dark,
            fill = scheme.surface,
            fillStrong = scheme.surface,
            sheen = Color.Transparent,
            borderTop = Color.Transparent,
            borderBottom = Color.Transparent,
            aurora = emptyList(),
            base = scheme.background,
            changed = if (dark) Color(0xFFFF6961) else Color(0xFFFF3B30),
            style = "ios", flat = true, cornerScale = 0.62f * corner, pressScale = 0.985f,
        )
        "amoled" -> GlassColors(
            dark = true,
            fill = Color(0xFF0C0C0E),
            fillStrong = Color(0xFF131316),
            sheen = Color.Transparent,
            borderTop = p.copy(alpha = 0.45f),
            borderBottom = p.copy(alpha = 0.12f),
            aurora = emptyList(),
            base = Color.Black,
            changed = Color(0xFFFF7A7A),
            style = "amoled", flat = true, cornerScale = 0.9f * corner, pressScale = 0.975f,
        )
        "paper" -> GlassColors(
            dark = dark,
            fill = scheme.surface,
            fillStrong = scheme.surfaceContainerHigh,
            sheen = Color.Transparent,
            borderTop = scheme.outlineVariant,
            borderBottom = scheme.outlineVariant,
            aurora = emptyList(),
            base = scheme.background,
            changed = if (dark) Color(0xFFFF8A80) else Color(0xFFC62828),
            style = "paper", flat = true, cornerScale = 0.75f * corner, pressScale = 0.98f,
        )
        "catppuccin", "nord", "mono" -> GlassColors(
            dark = dark,
            fill = scheme.surfaceContainerHigh,
            fillStrong = scheme.surfaceContainerHighest,
            sheen = Color.Transparent,
            // «Минимализм» — тонкая обводка вместо заливки разными тонами
            borderTop = if (style == "mono") scheme.outlineVariant else Color.Transparent,
            borderBottom = if (style == "mono") scheme.outlineVariant else Color.Transparent,
            aurora = emptyList(),
            base = scheme.background,
            changed = scheme.error,
            style = style, flat = true,
            cornerScale = corner * when (style) { "mono" -> 0.6f; "nord" -> 0.85f; else -> 1f },
            pressScale = 0.975f,
        )
        "ocean" -> GlassColors(
            dark = true,
            fill = Color(0xFF02101A).copy(alpha = al(0.40f)),
            fillStrong = Color(0xFF02101A).copy(alpha = al(0.56f)),
            sheen = Color.White.copy(alpha = 0.06f),
            borderTop = p.copy(alpha = 0.50f),
            borderBottom = p.copy(alpha = 0.06f),
            aurora = listOf(p.copy(alpha = 0.70f), scheme.tertiary.copy(alpha = 0.50f), scheme.secondary.copy(alpha = 0.55f)),
            base = scheme.background,
            changed = scheme.error,
            style = "ocean", cornerScale = corner,
        )
        "gradient", "sunset", "pastel" -> GlassColors(
            dark = dark,
            fill = (if (dark) Color.Black else Color.White).copy(alpha = al(if (dark) 0.30f else 0.62f)),
            fillStrong = (if (dark) Color.Black else Color.White).copy(alpha = al(if (dark) 0.42f else 0.78f)),
            sheen = Color.White.copy(alpha = if (dark) 0.06f else 0.35f),
            borderTop = Color.White.copy(alpha = if (dark) 0.25f else 0.85f),
            borderBottom = Color.White.copy(alpha = 0.1f),
            // пятна поверх градиента — светлые блики
            aurora = listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.35f), Color.White.copy(alpha = if (dark) 0.06f else 0.25f), scheme.tertiary.copy(alpha = 0.35f)),
            base = scheme.background,
            changed = if (dark) Color(0xFFFF8A80) else Color(0xFFC62828),
            style = style, cornerScale = corner, gradient = true,
        )
        "material" -> GlassColors(
            dark = dark,
            fill = scheme.surfaceContainerHigh,
            fillStrong = scheme.surfaceContainerHighest,
            sheen = Color.Transparent,
            borderTop = Color.Transparent,
            borderBottom = Color.Transparent,
            aurora = emptyList(),
            base = scheme.surface,
            changed = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
            style = "material", flat = true, cornerScale = 0.9f * corner, pressScale = 0.975f,
        )
        else -> if (dark) GlassColors(
            dark = true,
            fill = Color.White.copy(alpha = al(0.07f)),
            fillStrong = Color.White.copy(alpha = al(0.12f)),
            sheen = Color.White.copy(alpha = 0.10f),
            borderTop = Color.White.copy(alpha = 0.28f),
            borderBottom = Color.White.copy(alpha = 0.04f),
            aurora = listOf(p.copy(alpha = 0.55f), scheme.tertiary.copy(alpha = 0.45f), scheme.secondary.copy(alpha = 0.40f)),
            base = scheme.background,
            changed = Color(0xFFFF7A7A),
            style = "glass", cornerScale = corner,
        ) else GlassColors(
            dark = false,
            fill = Color.White.copy(alpha = al(0.52f)),
            fillStrong = Color.White.copy(alpha = al(0.72f)),
            sheen = Color.White.copy(alpha = 0.45f),
            borderTop = Color.White.copy(alpha = 0.95f),
            borderBottom = Color.White.copy(alpha = 0.25f),
            aurora = listOf(p.copy(alpha = 0.45f), scheme.tertiary.copy(alpha = 0.35f), scheme.secondary.copy(alpha = 0.35f)),
            base = scheme.background,
            changed = Color(0xFFD32F2F),
            style = "glass", cornerScale = corner,
        )
    }
}

@Composable
fun AppTheme(userPrefs: com.vadik.raspisanie.data.Prefs, content: @Composable () -> Unit) {
    val prefs = com.vadik.raspisanie.data.Edition.effective(userPrefs)
    val dark = effectiveDark(prefs.style, prefs.theme)
    val scheme = animated(schemeFor(prefs.style, dark, prefs.accent))
    val skin = skinFor(prefs.style, scheme, dark, prefs.cornerPercent, prefs.glassPercent)
    CompositionLocalProvider(LocalGlass provides skin) {
        MaterialTheme(colorScheme = scheme) {
            // цвет «по умолчанию» для любого текста и иконок — из темы, а не чёрный
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/** Тема для превью другого стиля (в редакторе темы) — без анимаций. */
@Composable
fun PreviewTheme(style: String, prefs: com.vadik.raspisanie.data.Prefs, content: @Composable () -> Unit) {
    val dark = effectiveDark(style, prefs.theme)
    val scheme = schemeFor(style, dark, prefs.accent)
    val skin = skinFor(style, scheme, dark, prefs.cornerPercent, prefs.glassPercent)
    CompositionLocalProvider(LocalGlass provides skin) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
