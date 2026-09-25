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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
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
    if (g.flat) {
        Box(Modifier.fillMaxSize().background(g.base), content = content)
        return
    }
    val cs = MaterialTheme.colorScheme
    // «Градиент»: сочный фон из оттенков темы (светлые контейнеры днём, тёмные — ночью)
    val bg = if (g.gradient) Brush.linearGradient(
        listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer),
        start = Offset.Zero, end = Offset.Infinite,
    ) else androidx.compose.ui.graphics.SolidColor(g.base)
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
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
    shape: Shape,
    strong: Boolean = false,
    tint: Color? = null,
): Modifier = this
    .clip(shape)
    .background(if (strong) g.fillStrong else g.fill)
    .then(if (tint != null) Modifier.background(tint) else Modifier)
    .then(
        if (g.sheen.alpha > 0f) Modifier.background(Brush.verticalGradient(listOf(g.sheen, Color.Transparent), endY = 220f))
        else Modifier,
    )
    .then(
        if (g.borderTop.alpha > 0f) Modifier.border(1.dp, Brush.verticalGradient(listOf(g.borderTop, g.borderBottom)), shape)
        else Modifier,
    )

/** Скругление с учётом стиля и настройки «скругление углов». */
@Composable
fun skinShape(base: Int): Shape = RoundedCornerShape((base * LocalGlass.current.cornerScale).dp)

/** Плавное растворение верхнего края прокручиваемого списка (вместо резкого обреза). */
fun Modifier.fadeTopEdge(fadePx: Float = 60f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, fadePx / size.height.coerceAtLeast(1f) to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** Растворение и верхнего, и нижнего края списка (под шапкой и над панелью вкладок). */
fun Modifier.fadeEdges(topPx: Float = 60f, bottomPx: Float = 70f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height.coerceAtLeast(1f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (topPx / h).coerceAtMost(0.4f) to Color.Black,
                (1f - bottomPx / h).coerceAtLeast(0.6f) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Стеклянная карточка; при нажатии слегка «проседает» с пружинкой. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlass.current
    val sh = shape ?: skinShape(26)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) g.pressScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glass(g, sh, strong, tint)
                .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier),
            content = content,
        )
    }
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

// ============================================================ нижняя панель вкладок

data class TabItem(val title: String, val icon: ImageVector)

/**
 * Панель вкладок в духе выбранного стиля:
 * стекло — парящая «пилюля» с переезжающим выделением, iOS — полоса с тонкой линией сверху,
 * Material — стандартная NavigationBar.
 */
@Composable
fun StyledTabBar(items: List<TabItem>, selected: Int, shape: String = "auto", onSelect: (Int) -> Unit) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    // «auto» — как задумано стилем: у Material / iOS / Бумаги — панель во всю ширину, у стеклянных — островок
    val mode = when (shape) {
        "rounded", "island", "flat" -> shape
        else -> if (g.style == "material" || g.style == "ios" || g.style == "paper" || g.style == "mono") "flat" else "island"
    }
    val top = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    when {
        mode == "island" -> IslandTabBar(items, selected, onSelect)

        g.style == "material" -> NavigationBar(
            modifier = if (mode == "rounded") Modifier.clip(top) else Modifier,
            containerColor = cs.surfaceContainer,
        ) {
            items.forEachIndexed { i, item ->
                NavigationBarItem(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title) },
                )
            }
        }

        mode == "rounded" && g.style != "ios" && g.style != "paper" ->
            // стеклянные стили: матовая панель с закруглённым верхом
            GlassCard(Modifier.fillMaxWidth(), shape = top, strong = true) {
                PlainTabRow(items, selected, Modifier.navigationBarsPadding().padding(top = 10.dp, bottom = 6.dp), onSelect)
            }

        else -> Column(
            Modifier
                .fillMaxWidth()
                .then(if (mode == "rounded") Modifier.clip(top) else Modifier)
                .background(if (mode == "rounded") cs.surfaceContainerHigh else cs.surface.copy(alpha = 0.94f)),
        ) {
            if (mode != "rounded") Box(Modifier.fillMaxWidth().height(0.5.dp).background(cs.outlineVariant))
            PlainTabRow(
                items, selected,
                Modifier.navigationBarsPadding().padding(top = if (mode == "rounded") 10.dp else 6.dp, bottom = 4.dp),
                onSelect,
            )
        }
    }
}

/** Ряд значков с подписями — для панели во всю ширину. */
@Composable
private fun PlainTabRow(items: List<TabItem>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            val c by androidx.compose.animation.animateColorAsState(
                if (i == selected) cs.primary else cs.onSurfaceVariant, tween(200), label = "tab",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, contentDescription = item.title, tint = c, modifier = Modifier.size(26.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = c,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** «Островок»: парящая капсула над нижним краем с бегающей подсветкой выбранной вкладки. */
@Composable
private fun IslandTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, bottom = 10.dp, top = 4.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(50), strong = true) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                val cell = maxWidth / items.size
                val x by androidx.compose.animation.core.animateDpAsState(
                    cell * selected, spring(dampingRatio = 0.72f, stiffness = 380f), label = "tabInd",
                )
                Box(
                    Modifier
                        .offset(x = x)
                        .width(cell)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(cs.primary.copy(alpha = if (g.dark) 0.30f else 0.16f)),
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { i, item ->
                        val c by androidx.compose.animation.animateColorAsState(
                            if (i == selected) cs.primary else cs.onSurfaceVariant, tween(200), label = "tab",
                        )
                        Column(
                            Modifier
                                .width(cell)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(item.icon, contentDescription = item.title, tint = c, modifier = Modifier.size(22.dp))
                            Text(
                                item.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = c,
                                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}
