package com.vadik.raspisanie.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vadik.raspisanie.data.Accents
import com.vadik.raspisanie.data.Prefs
import kotlin.math.roundToInt

/** Отдельный экран «Редактор темы»: стиль, цвет, скругление, стекло, иконка — с живым предпросмотром. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(state: UiState, vm: MainViewModel) {
    val prefs = state.prefs
    val cs = MaterialTheme.colorScheme
    val glassy = prefs.style == "glass" || prefs.style == "night" || prefs.style == "gradient"
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Редактор темы", onBack = { vm.closeThemeEditor() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // ---------------- живой предпросмотр текущей темы
            SamplePreview(state.settings?.groupName ?: "КВ-26-02")

            // ---------------- стиль
            SectionTitle("Стиль")
            STYLES.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { st ->
                        StyleTile(
                            st,
                            prefs,
                            selected = prefs.style == st.id,
                            modifier = Modifier.weight(1f),
                        ) { vm.updatePrefs { it.copy(style = st.id) } }
                    }
                }
            }

            // ---------------- цвет
            SectionTitle("Цвет")
            SettingsCard {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        ColorDot(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF5A5F), Color(0xFFF5B301), Color(0xFF22B573),
                                    Color(0xFF2FA8F5), Color(0xFF7C5CFF), Color(0xFFFF5A5F),
                                ),
                            ),
                            selected = prefs.accent == "dynamic",
                            label = "Обои",
                        ) { vm.updatePrefs { it.copy(accent = "dynamic") } }
                    }
                    Accents.presets.forEach { a ->
                        val c = Color(a.seed)
                        ColorDot(
                            Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f))),
                            selected = prefs.accent == a.id,
                            label = a.title,
                        ) { vm.updatePrefs { it.copy(accent = a.id) } }
                    }
                }
                Divider()
                Label("Pantone · цвета года")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Accents.pantone.forEach { a ->
                        val c = Color(a.seed)
                        ColorDot(
                            Brush.linearGradient(listOf(c, c.copy(alpha = 0.75f))),
                            selected = prefs.accent == a.id,
                            label = a.title.substringBefore(" ·"),
                        ) { vm.updatePrefs { it.copy(accent = a.id) } }
                    }
                }
                Accents.pantone.firstOrNull { it.id == prefs.accent }?.let {
                    Hint("Выбран: ${it.title.substringAfter("· ")} — цвет года Pantone ${it.title.substringBefore(" ·")}")
                }
                Divider()
                Label("Режим")
                if (prefs.style == "night" || prefs.style == "amoled") {
                    Hint("Этот стиль всегда тёмный.")
                } else {
                    Pills(
                        listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"),
                        prefs.theme,
                    ) { v -> vm.updatePrefs { it.copy(theme = v) } }
                }
            }

            // ---------------- форма
            SectionTitle("Форма и стекло")
            SettingsCard {
                PercentSlider("Скругление углов", prefs.cornerPercent) { v -> vm.updatePrefs { it.copy(cornerPercent = v) } }
                AnimatedVisibility(
                    visible = glassy,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Divider()
                        PercentSlider("Плотность стекла", prefs.glassPercent) { v ->
                            vm.updatePrefs { it.copy(glassPercent = v) }
                        }
                        Hint("Меньше — прозрачнее, больше — более матовое.")
                        Divider()
                        SwitchRow(
                            "Живой фон",
                            "Плавно переливающиеся цветные пятна за стеклом",
                            prefs.animatedBackground,
                        ) { v -> vm.updatePrefs { it.copy(animatedBackground = v) } }
                    }
                }
            }

            // ---------------- иконка
            SectionTitle("Иконка")
            SettingsCard {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconPreview(Color(Accents.seedOf(if (prefs.accent == "dynamic") "blue" else prefs.accent)))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Иконка на рабочем столе перекрашивается в цвет интерфейса. " +
                            "Смена применяется, когда вы выходите из приложения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                SwitchRow(
                    "Иконка в цвет темы",
                    "На Android 13+ также работают «Тематические значки» системы",
                    prefs.iconFollowsAccent,
                ) { v -> vm.updatePrefs { it.copy(iconFollowsAccent = v) } }
            }
        }
    }
}

@Composable
private fun PercentSlider(title: String, value: Int, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Label("$title: ${v.roundToInt()} %")
    Slider(
        value = v,
        onValueChange = { v = it },
        onValueChangeFinished = { onDone(v.roundToInt()) },
        valueRange = 50f..150f,
        steps = 19,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Мини-экран расписания в текущей теме. */
@Composable
private fun SamplePreview(group: String) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth().padding(top = 4.dp), strong = true) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Предпросмотр", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(group, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Пн" to "21", "Вт" to "22", "Ср" to "23", "Чт" to "24", "Пт" to "25").forEachIndexed { i, (d, n) ->
                    val sel = i == 3
                    Column(
                        Modifier
                            .clip(skinShape(16))
                            .background(if (sel) cs.primary else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d, style = MaterialTheme.typography.labelSmall, color = if (sel) cs.onPrimary else cs.onSurfaceVariant)
                        Text(n, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (sel) cs.onPrimary else cs.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Box(Modifier.width(4.dp).height(64.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.width(46.dp)) {
                        Text("10:15", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("11:45", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Column {
                        Text("Аналитическая геометрия", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Filled.Place, null, Modifier.size(14.dp), tint = changedColor())
                            Text(" ауд. 2358", style = MaterialTheme.typography.bodySmall, color = changedColor(), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Filled.Person, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                            Text(" Агеенко К. В.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** Плитка стиля: рисуется в своём собственном стиле (настоящий предпросмотр). */
@Composable
private fun StyleTile(st: StyleInfo, prefs: Prefs, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val outer = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) outer.primary else Color.Transparent, tween(250), label = "tileBorder")
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .border(2.5.dp, border, RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        PreviewTheme(st.id, prefs) {
            val cs = MaterialTheme.colorScheme
            Box(Modifier.fillMaxWidth().height(128.dp)) {
                AuroraBackground(animated = false) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassCard(Modifier.fillMaxWidth().height(34.dp), shape = skinShape(14), strong = true) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(4) { i ->
                                    Box(
                                        Modifier
                                            .padding(end = 5.dp)
                                            .size(width = 18.dp, height = 20.dp)
                                            .clip(skinShape(8))
                                            .background(if (i == 1) cs.primary else cs.onSurface.copy(alpha = 0.12f)),
                                    )
                                }
                            }
                        }
                        repeat(2) { i ->
                            GlassCard(Modifier.fillMaxWidth().height(32.dp), shape = skinShape(14)) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (i == 0) cs.primary else cs.tertiary))
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Box(Modifier.width(60.dp).height(6.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.7f)))
                                        Spacer(Modifier.height(4.dp))
                                        Box(Modifier.width(38.dp).height(5.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.3f)))
                                    }
                                }
                            }
                        }
                    }
                }
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(outer.surfaceContainerHigh.copy(alpha = 0.6f)).padding(10.dp)) {
            Text(st.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(st.description, style = MaterialTheme.typography.bodySmall, color = outer.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Как будет выглядеть иконка приложения. */
@Composable
private fun IconPreview(seed: Color) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(seed, seed.copy(red = seed.red * 0.7f, green = seed.green * 0.7f, blue = seed.blue * 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { c ->
                            Box(
                                Modifier.size(5.dp).clip(RoundedCornerShape(1.5.dp))
                                    .background(if (r == 1 && c == 2) Color(0xFF2B3445) else Color(0x332B3445)),
                            )
                        }
                    }
                }
            }
        }
    }
}
