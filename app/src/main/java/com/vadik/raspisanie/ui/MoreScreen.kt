package com.vadik.raspisanie.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Вкладка «Другое»: все разделы приложения и настройка нижней панели. */
@Composable
fun MoreScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    val bar = Tabs.bar(prefs)
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Другое", onBack = null)
        Column(
            Modifier
                .fillMaxSize()
                .fadeEdges(40f, 70f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---------------- разделы: сначала те, которых нет внизу
            SectionTitle("Разделы")
            SettingsCard {
                val sections = Tabs.OPTIONAL.sortedBy { it in bar }
                sections.forEachIndexed { i, id ->
                    if (i > 0) Divider()
                    SectionRow(id) { vm.selectTab(id) }
                }
            }

            // ---------------- нижняя панель
            SectionTitle("Нижняя панель")
            SettingsCard {
                Hint("Выберите до ${Tabs.MAX_EXTRA} разделов. «Расписание» и «Другое» всегда внизу, остальное — здесь.")
                BarPreview(bar, prefs.tabBarShape)
                Label("Форма панели")
                Pills(Tabs.SHAPES, prefs.tabBarShape) { v -> vm.updatePrefs { it.copy(tabBarShape = v) } }
                Tabs.OPTIONAL.forEach { id ->
                    Divider()
                    SwitchRow(Tabs.fullTitle(id), Tabs.subtitle(id), id in prefs.bottomTabs) { on ->
                        if (!vm.setBottomTab(id, on)) {
                            Toast.makeText(
                                ctx,
                                "Внизу помещается до ${Tabs.MAX_EXTRA} разделов — сначала уберите один",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(id: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Tabs.icon(id), contentDescription = null, tint = cs.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(Tabs.fullTitle(id), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(Tabs.subtitle(id), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
    }
}

/** Как будет выглядеть панель: значки в ряд. */
@Composable
private fun BarPreview(bar: List<String>, shape: String) {
    val cs = MaterialTheme.colorScheme
    // превью повторяет выбранную форму: островок — капсула с отступами, закруглённая — скруглён верх
    val (pad, sh) = when (shape) {
        "island" -> 34.dp to RoundedCornerShape(50)
        "rounded" -> 16.dp to RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        else -> 16.dp to RoundedCornerShape(6.dp)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad, vertical = 8.dp)
            .clip(sh)
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        bar.forEach { id ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Tabs.icon(id), contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                Text(Tabs.title(id), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
