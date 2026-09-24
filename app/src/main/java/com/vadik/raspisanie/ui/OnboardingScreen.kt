package com.vadik.raspisanie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Repository
import kotlin.math.sin
import androidx.compose.foundation.lazy.grid.items as gridItems

/** Мастер первого запуска: привет → факультет → группа → подгруппа. */
@Composable
fun OnboardingScreen(ob: OnboardingState, vm: MainViewModel) {
    BackHandler(enabled = ob.step > 0) { vm.onboardingBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // ---------------- шапка: назад + точки прогресса
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp)) {
                if (ob.step > 0) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { vm.onboardingBack() }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(4) { i -> ProgressDot(active = i == ob.step, done = i < ob.step) }
            }
            Spacer(Modifier.size(44.dp))
        }

        AnimatedContent(
            targetState = ob.step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(380)) { dir * it / 3 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(380)) { -dir * it / 4 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { step ->
            when (step) {
                0 -> WelcomeStep { vm.onboardingNext() }
                1 -> FacultyStep(ob, vm)
                2 -> GroupStep(ob, vm)
                else -> SubgroupStep(ob, vm)
            }
        }
    }
}

@Composable
private fun ProgressDot(active: Boolean, done: Boolean) {
    val cs = MaterialTheme.colorScheme
    val w by animateDpAsState(if (active) 26.dp else 8.dp, spring(dampingRatio = 0.7f), label = "dotW")
    val c by animateColorAsState(
        if (active || done) cs.primary else cs.onSurface.copy(alpha = 0.2f), tween(250), label = "dotC",
    )
    Box(Modifier.padding(horizontal = 3.dp).width(w).height(8.dp).clip(CircleShape).background(c))
}

/** Большая кнопка с градиентом. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val btnAlpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(200), label = "btn")
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { alpha = btnAlpha }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = cs.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================ шаг 0: привет

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    val inf = rememberInfiniteTransition(label = "float")
    val t by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        // логотип: листок календаря, «парит» и появляется с пружинкой
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = 0.5f + 0.5f * appear.value
                    scaleY = 0.5f + 0.5f * appear.value
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationY = sin(t) * 10f
                    rotationZ = sin(t) * 3f
                }
                .size(120.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(3) { c ->
                                Box(
                                    Modifier.size(11.dp).clip(RoundedCornerShape(3.dp))
                                        .background(if (r == 1 && c == 2) cs.primary else Color(0x332B3445)),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Привет! 👋", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Это MyGub — расписание РГУ нефти и газа им. Губкина. Давай за минуту настроим его под тебя.",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Feature("📅", "Пары, аудитории и замены — как на сайте, но быстрее")
                Feature("🔔", "Напоминание перед парой и уведомления об изменениях")
                Feature("📝", "Домашние задания со сроками по каждому предмету")
                Feature("🎨", "Стили оформления, цвета и виджет на рабочий стол")
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Начать", modifier = Modifier.padding(bottom = 10.dp), onClick = onStart)
        Text(
            "Автор: $AUTHOR_TG",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun Feature(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ============================================================ шаг 1: факультет

@Composable
private fun FacultyStep(ob: OnboardingState, vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        StepTitle("Твой факультет", "Выбери из списка — он загружается прямо с сайта вуза.")
        when {
            ob.faculties.isEmpty() && ob.loading -> LoadingBox("Загружаю факультеты…")
            ob.faculties.isEmpty() -> ErrorBox(ob.message ?: "Список пуст") { vm.onboardingRetry() }
            else -> {
                SearchField(query, "Поиск факультета") { query = it }
                val list = ob.faculties.filter { it.name.contains(query.trim(), ignoreCase = true) }
                LazyColumn(
                    Modifier.fillMaxSize().fadeEdges(40f, 70f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { f ->
                        val sel = ob.faculty?.id == f.id
                        GlassCard(
                            Modifier.fillMaxWidth(),
                            tint = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else null,
                            onClick = { vm.onboardingFaculty(f) },
                        ) {
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ шаг 2: группа

@Composable
private fun GroupStep(ob: OnboardingState, vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        StepTitle("Твоя группа", ob.faculty?.name ?: "")
        when {
            ob.loading -> LoadingBox("Загружаю группы…")
            ob.groups.isEmpty() -> ErrorBox(ob.message ?: "На этом факультете нет групп с расписанием") {
                ob.faculty?.let { vm.onboardingFaculty(it) }
            }
            else -> {
                SearchField(query, "Поиск, например КВ-26-02") { query = it }
                val q = Repository.normalizeCode(query)
                val list = ob.groups.filter { Repository.normalizeCode(it.code).contains(q) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxSize().fadeEdges(40f, 70f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(list, key = { it.id }) { g ->
                        val sel = ob.group?.id == g.id
                        GlassCard(
                            Modifier.fillMaxWidth(),
                            tint = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else null,
                            onClick = { vm.onboardingGroup(g) },
                        ) {
                            Text(
                                g.code,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ шаг 3: подгруппа

@Composable
private fun SubgroupStep(ob: OnboardingState, vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        StepTitle(
            "Подгруппа",
            "Лабораторные часто идут по подгруппам. Не знаешь свою — выбери «Вся группа», поменять можно в настройках.",
        )
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(0, "Вся группа", "Показывать пары всех подгрупп"),
                Triple(1, "1 подгруппа", "Пары 2-й подгруппы будут скрыты"),
                Triple(2, "2 подгруппа", "Пары 1-й подгруппы будут скрыты"),
            ).forEach { (n, title, sub) ->
                SubgroupOption(title, sub, selected = ob.subgroup == n) { vm.onboardingSubgroup(n) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Группа: ${ob.group?.code ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
        )
        PrimaryButton(
            "Готово",
            enabled = ob.group != null,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) { vm.finishOnboarding() }
    }
}

@Composable
private fun SubgroupOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) cs.primary else cs.outline, tween(200), label = "ring")
    val scale by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "check",
    )
    GlassCard(
        Modifier.fillMaxWidth(),
        tint = if (selected) cs.primary.copy(alpha = 0.14f) else null,
        onClick = onClick,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ============================================================ общие куски

@Composable
private fun SearchField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = skinShape(22),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    )
}

@Composable
private fun LoadingBox(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBox(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("😕", fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center)
        Text(
            "Проверьте интернет — данные берутся с сайта вуза.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        GlassPill("Повторить", selected = true, onClick = onRetry)
    }
}
