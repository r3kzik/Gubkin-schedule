package com.vadik.raspisanie.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ============================================================ фон «северное сияние»

/**
 * Мягкие цветные пятна, медленно плавающие по экрану. Поверх них полупрозрачные
 * карточки выглядят как матовое стекло.
 */
@Composable
fun AuroraBackground(animated: Boolean, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    // фазу читаем внутри drawBehind — анимация перерисовывает только фон, без рекомпозиции
    val phase: State<Float>? = if (animated) {
        val inf = rememberInfiniteTransition(label = "aurora")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    } else null
    Box(
        Modifier
            .fillMaxSize()
            .background(g.base)
            .drawBehind {
                val t = phase?.value ?: 0.8f
                val w = size.width
                val h = size.height
                val r = maxOf(w, h) * 0.55f
                val points = listOf(
                    Offset(w * (0.15f + 0.12f * cos(t)), h * (0.12f + 0.06f * sin(t * 2))),
                    Offset(w * (0.90f + 0.10f * sin(t)), h * (0.40f + 0.10f * cos(t))),
                    Offset(w * (0.30f + 0.15f * sin(t + 1f)), h * (0.88f + 0.06f * cos(t * 2 + 1f))),
                )
                points.forEachIndexed { i, c ->
                    val color = g.aurora[i % g.aurora.size]
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = c, radius = r),
                        radius = r,
                        center = c,
                    )
                }
            },
        content = content,
    )
}

// ============================================================ стекло

/** Модификатор «матового стекла»: полупрозрачная заливка, блик сверху, светлая рамка. */
fun Modifier.glass(
    g: GlassColors,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    tint: Color? = null,
): Modifier = this
    .clip(shape)
    .background(if (strong) g.fillStrong else g.fill)
    .then(if (tint != null) Modifier.background(tint) else Modifier)
    .background(Brush.verticalGradient(listOf(g.sheen, Color.Transparent), endY = 220f))
    .border(1.dp, Brush.verticalGradient(listOf(g.borderTop, g.borderBottom)), shape)

/** Стеклянная карточка; при нажатии слегка «проседает» с пружинкой. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlass.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(g, shape, strong, tint)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Круглая стеклянная кнопка с иконкой. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    GlassCard(modifier.size(size), shape = CircleShape, onClick = onClick) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp)
                .graphicsLayer { rotationZ = iconRotation },
        )
    }
}

/** Пилюля-переключатель (для выбора темы, подгруппы и т. п.). */
@Composable
fun GlassPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) cs.primary else Color.Transparent, tween(250), label = "pillBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        if (selected) cs.onPrimary else cs.onSurface, tween(250), label = "pillFg",
    )
    GlassCard(modifier, shape = RoundedCornerShape(50), onClick = onClick) {
        Box(Modifier.background(bg).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Text(
                text,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Шапка экрана: круглая стеклянная кнопка «назад» и крупный заголовок. */
@Composable
fun GlassTopBar(title: String, onBack: (() -> Unit)?, backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(backIcon, "Назад", onClick = onBack)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

/** Заголовок раздела над стеклянной карточкой. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Цвет для изменённых данных (замена аудитории/преподавателя). */
@Composable
fun changedColor(): Color = LocalGlass.current.changed
