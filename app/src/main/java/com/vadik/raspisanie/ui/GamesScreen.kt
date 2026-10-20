package com.vadik.raspisanie.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.games.TetrisEngine
import com.vadik.raspisanie.games.UItem
import com.vadik.raspisanie.games.Upgrader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun games(ctx: Context) = ctx.getSharedPreferences("games", Context.MODE_PRIVATE)

/** Вкладка «Игры» (только в бете): меню и сами игры. */
@Composable
fun GamesScreen() {
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    when (game) {
        "tetris" -> {
            BackHandler { game = null }
            TetrisScreen { game = null }
        }
        "upgrader" -> {
            BackHandler { game = null }
            UpgraderScreen { game = null }
        }
        else -> Column(Modifier.fillMaxSize()) {
            GlassTopBar("Игры", onBack = null)
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint("Только в бете и только ради развлечения — перемена ведь не бесконечная 🙂")
                GameCard("🧱", "Тетрис", "Классика: собирай линии, скорость растёт с уровнем") { game = "tetris" }
                GameCard("🎰", "Апгрейдер", "Рискни предметом ради более дорогого. Монеты игровые, ничего настоящего") { game = "upgrader" }
            }
        }
    }
}

@Composable
private fun GameCard(emoji: String, title: String, text: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = skinShape(26), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ================================================================ тетрис

private val PIECE_COLORS = listOf(
    Color.Transparent,
    Color(0xFF22C3E6), Color(0xFFF5C518), Color(0xFFA259FF), Color(0xFF3DDC84),
    Color(0xFFFF5A5F), Color(0xFF4B7BFF), Color(0xFFFF9F43),
)

@Composable
private fun TetrisScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val engine = remember { TetrisEngine() }
    var version by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(games(ctx).getInt("t_best", 0)) }

    fun changed() {
        version++
        if (engine.score > best) {
            best = engine.score
            games(ctx).edit().putInt("t_best", best).apply()
        }
        if (engine.over) running = false
    }

    // гравитация
    LaunchedEffect(running) {
        while (running && !engine.over) {
            delay(engine.speedMs())
            engine.tick()
            changed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Тетрис", onBack = onBack)
        val v = version // перерисовка при каждом ходе
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Счёт ${engine.score}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Линии ${engine.lines} · Уровень ${engine.level} · Рекорд $best",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            // следующая фигура
            Canvas(Modifier.size(52.dp)) {
                val cell = size.width / 4
                engine.cells(engine.next, 0).forEach { (cx, cy) ->
                    drawRoundRect(
                        PIECE_COLORS[engine.next], Offset(cx * cell + 1, cy * cell + 1 + cell / 2),
                        Size(cell - 2, cell - 2), CornerRadius(cell * 0.2f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            var dragX by remember { mutableStateOf(0f) }
            var dragY by remember { mutableStateOf(0f) }
            Canvas(
                Modifier
                    .aspectRatio(0.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerHighest.copy(alpha = 0.6f))
                    .pointerInput(Unit) {
                        detectTapGestures { if (running) { engine.rotate(); changed() } }
                    }
                    .pointerInput(Unit) {
                        val cell = size.width / engine.w.toFloat()
                        detectDragGestures(
                            onDragStart = { dragX = 0f; dragY = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                if (!running) return@detectDragGestures
                                dragX += amount.x
                                dragY += amount.y
                                while (dragX > cell) { engine.move(1); dragX -= cell; changed() }
                                while (dragX < -cell) { engine.move(-1); dragX += cell; changed() }
                                while (dragY > cell) { engine.step(manual = true); dragY -= cell; changed() }
                            },
                        )
                    },
            ) {
                val cell = size.width / engine.w
                val pad = 1.2f
                fun block(x: Int, y: Int, c: Color, ghost: Boolean = false) {
                    if (y < 0) return
                    val o = Offset(x * cell + pad, y * cell + pad)
                    val s = Size(cell - pad * 2, cell - pad * 2)
                    if (ghost) drawRoundRect(c.copy(alpha = 0.55f), o, s, CornerRadius(cell * 0.18f), style = Stroke(2f))
                    else drawRoundRect(c, o, s, CornerRadius(cell * 0.18f))
                }
                // сетка
                for (y in 0 until engine.h) for (x in 0 until engine.w) {
                    drawRoundRect(Color.White.copy(alpha = 0.035f), Offset(x * cell + pad, y * cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.18f))
                }
                for (y in 0 until engine.h) for (x in 0 until engine.w) {
                    val t = engine.board[y][x]
                    if (t != 0) block(x, y, PIECE_COLORS[t])
                }
                if (!engine.over && started) {
                    val gy = engine.ghostY()
                    engine.cells(engine.type, engine.rot).forEach { (cx, cy) -> block(engine.x + cx, gy + cy, PIECE_COLORS[engine.type], ghost = true) }
                    engine.cells(engine.type, engine.rot).forEach { (cx, cy) -> block(engine.x + cx, engine.y + cy, PIECE_COLORS[engine.type]) }
                }
                if (v < 0) drawCircle(Color.Transparent) // держим зависимость от version
            }
            if (!running) {
                GlassCard(Modifier.padding(32.dp), shape = skinShape(24), strong = true) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                engine.over -> "Игра окончена"
                                started -> "Пауза"
                                else -> "Тетрис"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (engine.over) Text("Счёт: ${engine.score}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Нажатие — поворот, свайп — сдвиг и падение",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        GlassPill(if (engine.over) "Ещё раз" else if (started) "Продолжить" else "Играть", selected = true) {
                            if (engine.over) engine.reset()
                            started = true
                            running = true
                            changed()
                        }
                    }
                }
            }
        }
        // кнопки управления
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlButton("◀") { if (running) { engine.move(-1); changed() } }
            ControlButton("⟳") { if (running) { engine.rotate(); changed() } }
            ControlButton("▶") { if (running) { engine.move(1); changed() } }
            ControlButton("▼") { if (running) { engine.step(manual = true); changed() } }
            ControlButton("⤓") { if (running) { engine.hardDrop(); changed() } }
            ControlButton(if (running) "❚❚" else "▷") {
                if (engine.over) return@ControlButton
                if (!started) started = true
                running = !running
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(cs.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
    }
}

// ================================================================ апгрейдер

private val TIER_COLORS = listOf(
    Color(0xFF9E9E9E), Color(0xFF4B69FF), Color(0xFF8847FF), Color(0xFFD32CE6), Color(0xFFEB4B4B), Color(0xFFE4AE39),
)

@Composable
private fun UpgraderScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sp = remember { games(ctx) }
    val scope = rememberCoroutineScope()
    var coins by remember { mutableIntStateOf(sp.getInt("u_coins", Upgrader.START_COINS)) }
    val inventory = remember {
        mutableStateListOf<Int>().apply {
            addAll(sp.getString("u_inv", "")!!.split(',').mapNotNull { it.toIntOrNull() })
        }
    }
    var freeAt by remember { mutableLongStateOf(sp.getLong("u_free", 0L)) }
    var wins by remember { mutableIntStateOf(sp.getInt("u_wins", 0)) }
    var losses by remember { mutableIntStateOf(sp.getInt("u_losses", 0)) }
    var selected by remember { mutableStateOf<Int?>(null) } // индекс в инвентаре
    var target by remember { mutableStateOf<UItem?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val needle = remember { Animatable(0f) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    fun save() {
        sp.edit()
            .putInt("u_coins", coins)
            .putString("u_inv", inventory.joinToString(","))
            .putLong("u_free", freeAt)
            .putInt("u_wins", wins)
            .putInt("u_losses", losses)
            .apply()
    }

    val from = selected?.let { inventory.getOrNull(it) }?.let { Upgrader.item(it) }
    val chance = if (from != null && target != null) Upgrader.chance(from, target!!) else 0.0

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Апгрейдер", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪙 $coins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Апгрейдов: $wins ✓ / $losses ✕", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // ---- колесо шанса
            GlassCard(Modifier.fillMaxWidth(), shape = skinShape(26), strong = true) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 22f
                            val inset = stroke / 2 + 4
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            drawArc(cs.onSurface.copy(alpha = 0.10f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            drawArc(Color(0xFF3DDC84), -90f, (chance * 360).toFloat(), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            rotate(needle.value) {
                                drawLine(cs.primary, center, Offset(center.x, inset - 6), strokeWidth = 7f)
                            }
                            drawCircle(cs.primary, 10f, center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 70.dp)) {
                            Text("%.1f %%".format(chance * 100), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("шанс", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemBadge(from, "Твой предмет")
                        Text("  →  ", fontWeight = FontWeight.Bold)
                        ItemBadge(target, "Цель")
                    }
                    result?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassPill(if (spinning) "Крутим…" else "АПГРЕЙД", selected = from != null && target != null && !spinning) {
                        val f = from ?: return@GlassPill
                        val t = target ?: return@GlassPill
                        val idx = selected ?: return@GlassPill
                        if (spinning) return@GlassPill
                        spinning = true
                        result = null
                        val (win, angle) = Upgrader.roll(Upgrader.chance(f, t))
                        scope.launch {
                            val base = needle.value - needle.value % 360f
                            needle.animateTo(base + 360f * 5 + angle, tween(2600, easing = FastOutSlowInEasing))
                            inventory.removeAt(idx)
                            if (win) {
                                inventory.add(t.id)
                                wins++
                                result = "Успех! ${t.emoji} ${t.name} теперь твой"
                            } else {
                                losses++
                                result = "Не повезло — ${f.emoji} ${f.name} сгорел"
                            }
                            selected = null
                            target = null
                            spinning = false
                            save()
                        }
                    }
                }
            }

            // ---- инвентарь
            SectionTitle("Инвентарь (${inventory.size})")
            if (inventory.isEmpty()) Hint("Пусто — открой ящик ниже.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inventory.forEachIndexed { i, id ->
                    val item = Upgrader.item(id) ?: return@forEachIndexed
                    ItemTile(item, selected = i == selected) {
                        if (spinning) return@ItemTile
                        selected = if (selected == i) null else i
                        target = null
                        result = null
                    }
                }
            }
            from?.let { f ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill("Продать за ${f.value} 🪙", selected = false) {
                        if (spinning) return@GlassPill
                        selected?.let { inventory.removeAt(it) }
                        coins += f.value
                        selected = null
                        target = null
                        result = "Продано: ${f.emoji} ${f.name} (+${f.value} 🪙)"
                        save()
                    }
                }
            }

            // ---- цель
            if (from != null) {
                SectionTitle("Цель")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.5, 2.0, 3.0, 5.0, 10.0).forEach { k ->
                        val t = Upgrader.targetFor(from, k)
                        if (t != null) GlassPill("×${if (k % 1.0 == 0.0) k.toInt() else k}", selected = target == t && !spinning) { if (!spinning) target = t }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Upgrader.ITEMS.filter { it.value > from.value }.forEach { t ->
                        ItemTile(t, selected = target == t) { if (!spinning) target = t }
                    }
                }
            }

            // ---- ящики
            SectionTitle("Ящики")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val left = ((freeAt + Upgrader.FREE_COOLDOWN_MS - now) / 1000).coerceAtLeast(0)
                GlassPill(if (left > 0) "🎁 Бесплатный через $left с" else "🎁 Бесплатный ящик", selected = left == 0L) {
                    if (left > 0 || spinning) return@GlassPill
                    val got = Upgrader.crate(paid = false)
                    inventory.add(got.id)
                    freeAt = System.currentTimeMillis()
                    result = "Из ящика: ${got.emoji} ${got.name} (${got.value} 🪙)"
                    save()
                }
                GlassPill("📦 Ящик за ${Upgrader.CRATE_PRICE} 🪙", selected = coins >= Upgrader.CRATE_PRICE) {
                    if (coins < Upgrader.CRATE_PRICE || spinning) return@GlassPill
                    coins -= Upgrader.CRATE_PRICE
                    val got = Upgrader.crate(paid = true)
                    inventory.add(got.id)
                    result = "Из ящика: ${got.emoji} ${got.name} (${got.value} 🪙)"
                    save()
                }
            }
            Hint("Всё понарошку: монеты и предметы игровые, купить или вывести ничего нельзя. Шанс честный: стрелка останавливается случайно, «заведение» забирает 8 %.")
            GlassPill("Начать заново", selected = false) {
                if (spinning) return@GlassPill
                coins = Upgrader.START_COINS
                inventory.clear()
                wins = 0; losses = 0
                selected = null; target = null; result = "Счёт обнулён"
                save()
            }
        }
    }
}

@Composable
private fun ItemTile(item: UItem, selected: Boolean, onClick: () -> Unit) {
    val c = TIER_COLORS[item.tier]
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.copy(alpha = 0.16f))
            .border(if (selected) 3.dp else 1.dp, if (selected) c else c.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(item.emoji, fontSize = 28.sp)
        Text(item.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        Text("${item.value} 🪙", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
private fun ItemBadge(item: UItem?, placeholder: String) {
    val c = item?.let { TIER_COLORS[it.tier] } ?: MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, c, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(item?.emoji ?: "?", fontSize = 22.sp)
        Text(item?.name ?: placeholder, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        if (item != null) Text("${item.value} 🪙", style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold)
    }
}
