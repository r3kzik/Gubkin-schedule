package com.vadik.raspisanie.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FAF),
    secondary = Color(0xFF3E6A8A),
    tertiary = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    secondary = Color(0xFFA9CBEA),
    tertiary = Color(0xFFF2B8B5),
)

/** true, если должна быть тёмная тема с учётом выбора в настройках. */
@Composable
fun isDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(mode: String = "system", dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
