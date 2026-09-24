package com.vadik.raspisanie.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.UpdateInfo
import com.vadik.raspisanie.data.Updates
import java.util.Locale

// ---------------------------------------------------------------- плашка на экране расписания

@Composable
fun UpdateBanner(state: UiState, vm: MainViewModel) {
    val u = state.update
    val info = u.info
    AnimatedVisibility(
        visible = info != null && !u.bannerHidden,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val cs = MaterialTheme.colorScheme
        GlassCard(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
            shape = skinShape(22),
            tint = cs.primary.copy(alpha = 0.16f),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Доступно обновление", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        info?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                GlassPill("Обновить", selected = true) { vm.openUpdateDialog() }
                Spacer(Modifier.size(6.dp))
                GlassPill("Позже", selected = false) { vm.hideUpdateBanner() }
            }
        }
    }
}

// ---------------------------------------------------------------- окно обновления

private fun sizeText(bytes: Long): String =
    if (bytes <= 0) "" else String.format(Locale.US, "%.1f МБ", bytes / 1024f / 1024f)

/** Текст релиза без разметки GitHub. */
private fun cleanNotes(s: String): String =
    s.replace("**", "").replace("__", "").replace(Regex("(?m)^#+\\s*"), "").trim()

@Composable
fun UpdateDialog(u: UpdateState, vm: MainViewModel) {
    val info: UpdateInfo = u.info ?: return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!u.downloading) vm.closeUpdateDialog() },
        title = { Text("Обновление MyGub") },
        text = {
            Column {
                Text(info.title, fontWeight = FontWeight.SemiBold)
                sizeText(info.sizeBytes).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val notes = cleanNotes(info.notes)
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (u.downloading) {
                    Spacer(Modifier.height(14.dp))
                    if (u.progress >= 0f) {
                        LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Скачивание… ${(u.progress * 100).toInt()} %",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Скачивание…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (u.needPermission) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Чтобы обновляться, MyGub нужно разрешение «Установка неизвестных приложений». " +
                            "Включите его на следующем экране и вернитесь назад — обновление продолжится само.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                u.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Файл берётся из официальных релизов на GitHub и перед установкой проверяется подпись.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            when {
                u.needPermission -> TextButton(onClick = {
                    runCatching { ctx.startActivity(vm.allowInstalls()) }
                }) { Text("Разрешить") }
                else -> TextButton(enabled = !u.downloading, onClick = { vm.startUpdate() }) {
                    Text(if (u.message != null && !u.downloading) "Повторить" else "Обновить")
                }
            }
        },
        dismissButton = {
            if (!u.downloading) TextButton(onClick = { vm.closeUpdateDialog() }) { Text("Позже") }
        },
    )
}

// ---------------------------------------------------------------- раздел в настройках

@Composable
fun UpdatesSection(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    val u = state.update
    SectionTitle("Обновления")
    SettingsCard {
        val channel = when (Updates.channelFor(ctx.packageName)) {
            "beta" -> "бета-канал"
            "lite" -> "Lite"
            else -> "основной канал"
        }
        NavRow(
            when {
                u.checking -> "Проверяю…"
                u.info != null -> "Доступна новая версия"
                else -> "Проверить обновления"
            },
            when {
                u.info != null -> "${u.info.title} · нажмите, чтобы установить"
                u.message != null -> u.message
                else -> "Сейчас ${appVersion(ctx)} · $channel"
            },
        ) { if (u.info != null) vm.openUpdateDialog() else vm.checkUpdatesNow() }
        Divider()
        SwitchRow(
            "Проверять автоматически",
            "Раз в 12 часов, пришлём уведомление о новой версии",
            prefs.autoUpdateCheck,
        ) { v -> vm.updatePrefs { it.copy(autoUpdateCheck = v) } }
        AnimatedVisibility(
            visible = prefs.autoUpdateCheck,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Divider()
                SwitchRow(
                    "Скачивать сразу",
                    "Новая версия скачается в фоне, останется только подтвердить установку",
                    prefs.autoInstall,
                ) { v -> vm.updatePrefs { it.copy(autoInstall = v) } }
            }
        }
        Divider()
        NavRow("Все версии", "Страница релизов MyGub на GitHub") { openUrl(ctx, Updates.RELEASES_PAGE) }
    }
}

internal fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
    }
}

// ---------------------------------------------------------------- неоригинальная копия

/**
 * Показывается вместо приложения, если APK переподписан чужим ключом
 * (например, кто-то убрал автора и выдаёт MyGub за своё).
 */
@Composable
fun TamperScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(cs.error, cs.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "Неоригинальная копия MyGub",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = cs.onBackground,
            )
            Text(
                "Это приложение было изменено и подписано не автором. Такая копия может быть " +
                    "небезопасной: в ней могли убрать защиту или добавить чужой код.\n\n" +
                    "Удалите её и скачайте настоящий MyGub с официальной страницы.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { openUrl(ctx, Updates.RELEASES_PAGE) }, modifier = Modifier.fillMaxWidth()) {
                Text("Скачать оригинал")
            }
            OutlinedButton(onClick = { openUrl(ctx, "https://t.me/Bomb0clat67") }, modifier = Modifier.fillMaxWidth()) {
                Text("Сообщить автору $AUTHOR_TG")
            }
        }
    }
}
