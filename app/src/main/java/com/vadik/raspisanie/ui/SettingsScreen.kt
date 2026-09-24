package com.vadik.raspisanie.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val prefs = state.prefs
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeSettings() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ---------------- группа
            Section("Группа")
            ListItem(
                headlineContent = { Text(state.settings?.groupName ?: "Не выбрана") },
                supportingContent = { Text(state.settings?.facultyName?.ifBlank { null } ?: "Нажмите, чтобы сменить группу") },
                modifier = Modifier.clickable { vm.closeSettings(); vm.openPicker() },
            )
            Label("Подгруппа")
            Choices(
                options = listOf(0 to "Вся группа", 1 to "1 подгруппа", 2 to "2 подгруппа"),
                selected = prefs.subgroup,
            ) { v -> vm.updatePrefs { it.copy(subgroup = v) } }
            val withSub = state.week?.lessons?.count { it.subgroup != null } ?: 0
            Hint(
                if (state.week == null) "Подгруппы определяются по данным сайта."
                else if (withSub > 0) "На этой неделе пар с подгруппами: $withSub. Они отмечены меткой «1 подгр.» / «2 подгр.»."
                else "На этой неделе пар с подгруппами не найдено. Если на сайте они есть, нажмите «Отправить данные» внизу."
            )
            SwitchRow(
                title = "Скрывать пары другой подгруппы",
                subtitle = if (prefs.hideOtherSubgroup) "Чужие пары не показываются" else "Чужие пары показаны бледными",
                checked = prefs.hideOtherSubgroup,
                enabled = prefs.subgroup != 0,
            ) { v -> vm.updatePrefs { it.copy(hideOtherSubgroup = v) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---------------- уведомления
            Section("Уведомления")
            SwitchRow(
                title = "Напоминать о начале пары",
                subtitle = "Уведомление за ${prefs.remindMinutes} мин до начала",
                checked = prefs.remindEnabled,
            ) { v -> vm.updatePrefs { it.copy(remindEnabled = v) } }
            if (prefs.remindEnabled) {
                Label("За сколько минут")
                Choices(
                    options = listOf(5, 10, 15, 20, 30, 60).map { it to "$it мин" },
                    selected = prefs.remindMinutes,
                ) { v -> vm.updatePrefs { it.copy(remindMinutes = v) } }
            }
            SwitchRow(
                title = "Сообщать об изменениях",
                subtitle = "Отмены, переносы, смена аудитории — проверка раз в 3 часа",
                checked = prefs.changeNotify,
            ) { v -> vm.updatePrefs { it.copy(changeNotify = v) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---------------- оформление
            Section("Оформление")
            Label("Тема")
            Choices(
                options = listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"),
                selected = prefs.theme,
            ) { v -> vm.updatePrefs { it.copy(theme = v) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    title = "Цвета обоев (Material You)",
                    subtitle = "Выключите, чтобы использовать синюю палитру приложения",
                    checked = prefs.dynamicColor,
                ) { v -> vm.updatePrefs { it.copy(dynamicColor = v) } }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---------------- виджет
            Section("Виджет")
            Hint("Добавить: долгое нажатие на рабочем столе → Виджеты → «Расписание».")
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
            Hint("100 % — сплошной фон, 0 % — полностью прозрачный.")
            Label("Тема виджета")
            Choices(
                options = listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"),
                selected = prefs.widgetTheme,
            ) { v -> vm.updatePrefs { it.copy(widgetTheme = v) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---------------- диагностика
            Section("Помощь")
            ListItem(
                headlineContent = { Text("Отправить данные расписания") },
                supportingContent = { Text("Последний ответ сайта файлом — пригодится, если что-то отображается неправильно") },
                modifier = Modifier.clickable { shareRaw(ctx, vm.rawFile()) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---------------- поддержка
            Section("Поддержка")
            ListItem(
                headlineContent = { Text("Написать в поддержку") },
                supportingContent = { Text("Вопрос или проблема — ответ в Telegram $AUTHOR_TG") },
                modifier = Modifier.clickable { openTelegram(ctx) },
            )
            ListItem(
                headlineContent = { Text("Предложить улучшение") },
                supportingContent = { Text("Идея, чего не хватает в MyGub") },
                modifier = Modifier.clickable { openTelegram(ctx) },
            )

            // ---------------- автор
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("MyGub Lite", style = MaterialTheme.typography.titleMedium)
                Text("Создатель и разработчик", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    AUTHOR_TG,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openTelegram(ctx) }.padding(4.dp),
                )
                Text(
                    "© 2026 $AUTHOR_TG. Все права защищены. Копирование и распространение без согласия автора запрещены. " +
                        "Неофициальное приложение, не связано с РГУ им. Губкина.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

const val AUTHOR_TG = "@Bomb0clat67"

private fun openTelegram(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/Bomb0clat67")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "Telegram: $AUTHOR_TG", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
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
