package com.vadik.raspisanie.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vadik.raspisanie.data.Accents
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val prefs = state.prefs
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Настройки", onBack = null)
        Column(
            Modifier
                .fillMaxSize()
                .fadeEdges(40f, 70f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---------------- оформление
            SectionTitle("Оформление")
            SettingsCard {
                NavRow(
                    "Редактор темы",
                    "${STYLES.firstOrNull { it.id == prefs.style }?.title ?: "Стиль"} · цвет, скругление, иконка",
                ) { vm.openThemeEditor() }
                Divider()
                NavRow(
                    "Нижняя панель",
                    Tabs.bar(prefs).joinToString(" · ") { Tabs.title(it) },
                ) { vm.selectTab(Tabs.MORE) }
            }

            // ---------------- группа
            SectionTitle("Группа")
            SettingsCard {
                NavRow(
                    state.settings?.groupName ?: "Не выбрана",
                    state.settings?.facultyName?.ifBlank { null } ?: "Нажмите, чтобы сменить группу",
                ) { vm.closeSettings(); vm.openPicker() }
                Divider()
                Label("Подгруппа")
                Pills(listOf(0 to "Вся группа", 1 to "1 подгр.", 2 to "2 подгр."), prefs.subgroup) { v ->
                    vm.updatePrefs { it.copy(subgroup = v) }
                }
                val withSub = state.week?.lessons?.count { it.subgroup != null } ?: 0
                Hint(
                    if (state.week == null) "Подгруппы определяются по данным сайта."
                    else if (withSub > 0) "На этой неделе пар по подгруппам: $withSub."
                    else "На этой неделе пар по подгруппам нет."
                )
                AnimatedVisibility(
                    visible = prefs.subgroup != 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Divider()
                        SwitchRow(
                            "Скрывать пары другой подгруппы",
                            if (prefs.hideOtherSubgroup) "Чужие пары не показываются" else "Чужие пары показаны бледными",
                            prefs.hideOtherSubgroup,
                        ) { v -> vm.updatePrefs { it.copy(hideOtherSubgroup = v) } }
                    }
                }
            }

            // ---------------- уведомления
            SectionTitle("Уведомления")
            SettingsCard {
                SwitchRow(
                    "Напоминать о начале пары",
                    "За ${prefs.remindMinutes} мин до начала",
                    prefs.remindEnabled,
                ) { v -> vm.updatePrefs { it.copy(remindEnabled = v) } }
                AnimatedVisibility(
                    visible = prefs.remindEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Pills(listOf(5, 10, 15, 20, 30, 60).map { it to "$it мин" }, prefs.remindMinutes) { v ->
                        vm.updatePrefs { it.copy(remindMinutes = v) }
                    }
                }
                Divider()
                SwitchRow(
                    "Сообщать об изменениях",
                    "Замены, отмены, переносы — проверка раз в 3 часа",
                    prefs.changeNotify,
                ) { v -> vm.updatePrefs { it.copy(changeNotify = v) } }
            }

            // ---------------- виджет
            SectionTitle("Виджет")
            SettingsCard {
                Hint("Добавить: долгое нажатие на рабочем столе → Виджеты → MyGub. Виджетов четыре: расписание 4×2, пары дня 2×3, следующая пара 2×2 и полоска 1×3.")
                var opacity by remember(prefs.widgetOpacity) { mutableFloatStateOf(prefs.widgetOpacity.toFloat()) }
                Label("Непрозрачность фона: ${opacity.roundToInt()} %")
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = {
                        val v = opacity.roundToInt()
                        vm.updatePrefs { it.copy(widgetOpacity = v) }
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Divider()
                Label("Тема виджета")
                Pills(
                    listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"),
                    prefs.widgetTheme,
                ) { v -> vm.updatePrefs { it.copy(widgetTheme = v) } }
            }

            // ---------------- обновления
            UpdatesSection(state, vm)

            // ---------------- помощь
            SectionTitle("Поддержка")
            SettingsCard {
                NavRow("Написать в поддержку", "Вопрос или проблема — ответ в Telegram $AUTHOR_TG") {
                    openTelegram(ctx, "Поддержка MyGub\n${deviceInfo(ctx)}\n\nОпишите проблему: ")
                }
                Divider()
                NavRow("Предложить улучшение", "Идея, чего не хватает в MyGub") {
                    openTelegram(ctx, "Предложение для MyGub (${appVersion(ctx)}): ")
                }
            }

            SectionTitle("Помощь")
            SettingsCard {
                NavRow("Ввести капчу сайта", "Если сайт вуза перестал отдавать расписание") { vm.openCaptcha() }
                Divider()
                NavRow("Отправить данные расписания", "Последний ответ сайта файлом — для диагностики") {
                    shareRaw(ctx, vm.rawFile())
                }
            }

            Spacer(Modifier.height(20.dp))
            AboutCard(ctx)
        }
    }
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = skinShape(28)) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
internal fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Pills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            GlassPill(label, selected = value == selected) { onSelect(value) }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Кружок цвета: выбранный чуть увеличивается с пружинкой и получает галочку. */
@Composable
internal fun ColorDot(fill: Brush, selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "dot",
    )
    Column(
        Modifier.width(62.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(fill)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) cs.onSurface else Color.White.copy(alpha = 0.5f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------- автор и поддержка

const val AUTHOR_TG = "@Bomb0clat67"
private const val AUTHOR_LINK = "https://t.me/Bomb0clat67"

fun appVersion(ctx: Context): String = try {
    "v" + (ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?")
} catch (e: Exception) {
    "v?"
}

private fun deviceInfo(ctx: Context): String =
    "MyGub ${appVersion(ctx)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"

/** Открывает чат с автором в Telegram; шаблон сообщения кладётся в буфер обмена. */
private fun openTelegram(ctx: Context, template: String?) {
    if (template != null) {
        runCatching {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("MyGub", template))
            Toast.makeText(ctx, "Шаблон сообщения скопирован — вставьте его в чат", Toast.LENGTH_LONG).show()
        }
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(AUTHOR_LINK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не нашлось приложения для ссылки. Telegram: $AUTHOR_TG", Toast.LENGTH_LONG).show()
    }
}

/** Карточка «О приложении»: автор, версия, права. */
@Composable
private fun AboutCard(ctx: Context) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(3) { c ->
                                    Box(
                                        Modifier.size(5.dp).clip(RoundedCornerShape(1.5.dp))
                                            .background(if (r == 1 && c == 2) cs.primary else Color(0x332B3445)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("MyGub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(appVersion(ctx), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "Создатель и разработчик",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Text(
                AUTHOR_TG,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onPrimaryContainer,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(cs.primaryContainer)
                    .clickable { openTelegram(ctx, null) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Сделано студентом для студентов Губкинского ❤️",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "© 2026 $AUTHOR_TG. Все права защищены.\nКопирование, изменение и распространение приложения без согласия автора запрещены.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Неофициальное приложение, не связано с РГУ нефти и газа им. И. М. Губкина. Есть лёгкая версия MyGub Lite.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun shareRaw(ctx: Context, raw: File?) {
    if (raw == null) {
        Toast.makeText(ctx, "Данных пока нет — сначала обновите расписание", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "raspisanie_data.json")
        raw.copyTo(out, overwrite = true)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Отправить данные расписания"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не получилось: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
