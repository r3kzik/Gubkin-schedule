package com.vadik.raspisanie.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
    val (h, s0, _) = hsl(seed.toArgb()).let { Triple(it[0], it[1], it[2]) }
    val s = s0.coerceAtLeast(0.12f)
    val h2 = h + 35f   // вторичный
    val h3 = h - 40f   // третичный
    return if (!dark) lightColorScheme(
        primary = tone(h, s * 0.95f, 0.47f),
        onPrimary = Color.White,
        primaryContainer = tone(h, s * 0.9f, 0.88f),
        onPrimaryContainer = tone(h, s, 0.16f),
        secondary = tone(h2, s * 0.55f, 0.42f),
        onSecondary = Color.White,
        secondaryContainer = tone(h2, s * 0.6f, 0.89f),
        onSecondaryContainer = tone(h2, s * 0.6f, 0.15f),
        tertiary = tone(h3, s * 0.6f, 0.42f),
        onTertiary = Color.White,
        tertiaryContainer = tone(h3, s * 0.65f, 0.89f),
        onTertiaryContainer = tone(h3, s * 0.6f, 0.15f),
        background = tone(h, s * 0.35f, 0.965f),
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
        primary = tone(h, s * 0.9f, 0.76f),
        onPrimary = tone(h, s, 0.14f),
        primaryContainer = tone(h, s * 0.7f, 0.30f),
        onPrimaryContainer = tone(h, s * 0.9f, 0.90f),
        secondary = tone(h2, s * 0.5f, 0.76f),
        onSecondary = tone(h2, s * 0.6f, 0.14f),
        secondaryContainer = tone(h2, s * 0.4f, 0.28f),
        onSecondaryContainer = tone(h2, s * 0.5f, 0.90f),
        tertiary = tone(h3, s * 0.5f, 0.76f),
        onTertiary = tone(h3, s * 0.6f, 0.14f),
        tertiaryContainer = tone(h3, s * 0.4f, 0.28f),
        onTertiaryContainer = tone(h3, s * 0.5f, 0.90f),
        background = tone(h, s * 0.3f, 0.065f),
        onBackground = tone(h, 0.1f, 0.92f),
        surface = tone(h, s * 0.25f, 0.08f),
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
    )
}

private fun glassFor(scheme: ColorScheme, dark: Boolean): GlassColors {
    val p = scheme.primary
    return if (dark) GlassColors(
        dark = true,
        fill = Color.White.copy(alpha = 0.07f),
        fillStrong = Color.White.copy(alpha = 0.12f),
        sheen = Color.White.copy(alpha = 0.10f),
        borderTop = Color.White.copy(alpha = 0.28f),
        borderBottom = Color.White.copy(alpha = 0.04f),
        aurora = listOf(p.copy(alpha = 0.55f), scheme.tertiary.copy(alpha = 0.45f), scheme.secondary.copy(alpha = 0.40f)),
        base = scheme.background,
        changed = Color(0xFFFF7A7A),
    ) else GlassColors(
        dark = false,
        fill = Color.White.copy(alpha = 0.52f),
        fillStrong = Color.White.copy(alpha = 0.72f),
        sheen = Color.White.copy(alpha = 0.45f),
        borderTop = Color.White.copy(alpha = 0.95f),
        borderBottom = Color.White.copy(alpha = 0.25f),
        aurora = listOf(p.copy(alpha = 0.45f), scheme.tertiary.copy(alpha = 0.35f), scheme.secondary.copy(alpha = 0.35f)),
        base = scheme.background,
        changed = Color(0xFFD32F2F),
    )
}

@Composable
fun AppTheme(mode: String = "system", accent: String = "blue", content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val ctx = LocalContext.current
    val target = when {
        accent == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> schemeFromSeed(Color(Accents.seedOf(accent)), dark)
    }
    val scheme = animated(target)
    CompositionLocalProvider(LocalGlass provides glassFor(scheme, dark)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
