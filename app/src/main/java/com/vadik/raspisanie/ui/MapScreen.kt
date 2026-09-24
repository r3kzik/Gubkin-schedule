package com.vadik.raspisanie.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Poi
import com.vadik.raspisanie.data.RoomLocation
import com.vadik.raspisanie.data.timeKey
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================ геометрия схемы (условные единицы 1000 × 820)

private const val MAP_W = 1000f
private const val MAP_H = 820f

/** Части зданий: у одного корпуса может быть несколько прямоугольников. */
private data class Part(val id: String, val rect: Rect, val depth: Float = 14f)

private val PARTS = listOf(
    Part("k2", Rect(560f, 70f, 970f, 200f), 20f),
    Part("k2", Rect(770f, 200f, 830f, 248f), 8f),   // спортзал № 3
    Part("k2", Rect(838f, 200f, 898f, 248f), 8f),   // спортзал № 2
    Part("k2", Rect(906f, 200f, 966f, 248f), 8f),   // спортзал № 1
    Part("dk", Rect(600f, 300f, 760f, 500f), 18f),
    Part("lib", Rect(740f, 470f, 930f, 650f), 18f),
    Part("main", Rect(290f, 420f, 570f, 520f), 18f),
    Part("main", Rect(290f, 520f, 380f, 690f), 18f),
    Part("main", Rect(480f, 520f, 570f, 690f), 18f),
    Part("canteen", Rect(215f, 445f, 290f, 525f), 10f),
    Part("k3", Rect(40f, 380f, 215f, 640f), 22f),
)

/** Переходы между зданиями (без подписей — только схема). */
private val BRIDGES = listOf(
    Rect(570f, 445f, 600f, 470f),
    Rect(660f, 200f, 690f, 300f),
)

private val SQUARE = Rect(320f, 210f, 560f, 390f)
private val ROAD = Rect(0f, 740f, 1000f, 800f)

/** Где рисовать подпись и «булавку» для здания. */
private val LABEL_AT = mapOf(
    "k2" to Offset(700f, 135f),
    "dk" to Offset(680f, 385f),
    "lib" to Offset(835f, 575f),
    "main" to Offset(430f, 470f),
    "canteen" to Offset(252f, 485f),
    "k3" to Offset(127f, 510f),
)
private val LABELS = mapOf(
    "k2" to "2 корпус",
    "dk" to "ДК\n«Губкинец»",
    "lib" to "Библиотека",
    "main" to "Главный\nкорпус",
    "canteen" to "🍽",
    "k3" to "3 корпус",
)

private fun hit(p: Offset): String? = PARTS.lastOrNull { it.rect.contains(p) }?.id

// ============================================================ экран

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(state: UiState, vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Деканаты") }
    // фокус из расписания («Где это?»)
    val focus = state.mapFocus
    LaunchedEffect(state.mapFocusSeq) { focus?.let { selected = it.building.id } }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Карта кампуса", onBack = null)

        CampusMap(
            selected = selected,
            pinFloor = focus?.takeIf { it.building.id == selected }?.floorLabel,
            focusSeq = state.mapFocusSeq,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) { id -> selected = if (selected == id) null else id }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- поиск
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Аудитория или место: 2358, медпункт…") },
                singleLine = true,
                shape = skinShape(22),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            val loc = Campus.locate(query)
            val found = Campus.searchPois(query)
            AnimatedVisibility(visible = loc != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                loc?.let { RoomResult(it) { vm.showOnMap(query) } }
            }
            found.take(8).forEach { p -> PoiRow(p) { vm.showBuildingOnMap(p.buildingId) } }
            if (query.isNotBlank() && loc == null && found.isEmpty()) {
                Text("Ничего не нашлось", color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }

            // ---------------- выбранное здание
            val b = selected?.let { id -> Campus.buildings.firstOrNull { it.id == id } }
            if (b != null) {
                BuildingCard(b.id, state, onClose = { selected = null }, onPoi = {})
            } else if (query.isBlank()) {
                Text(
                    "Нажмите на здание на схеме, чтобы узнать, что в нём. Схему можно приближать двумя пальцами.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            // ---------------- места по категориям
            SectionTitle("Места")
            val cats = Campus.pois.map { it.category }.distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c -> GlassPill(c, selected = c == category) { category = c } }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Campus.pois.filter { it.category == category }.forEach { p ->
                        PoiLine(p) { vm.showBuildingOnMap(p.buildingId) }
                    }
                }
            }

            // ---------------- как добраться
            SectionTitle("Как добраться")
            InfoCard {
                Text(Campus.UNIVERSITY_ADDRESS, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Campus.transport.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill("Открыть в Картах", selected = true) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("РГУ нефти и газа им. Губкина, " + Campus.UNIVERSITY_ADDRESS))),
                            )
                        }
                    }
                    GlassPill("Позвонить", selected = false) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:84995078888"))) }
                    }
                }
                Text(
                    "${Campus.PHONE} · ${Campus.EMAIL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ---------------- студгородок
            SectionTitle("Студенческий городок")
            InfoCard {
                Campus.dormAddresses.forEach { Text("🏠 $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Campus.dormServices.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }

            // ---------------- памятка
            SectionTitle("Памятка первокурснику")
            InfoCard {
                Campus.memo.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
                Text(
                    "По «Краткому путеводителю для первокурсника по Губкинскому университету».",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun RoomResult(loc: RoomLocation, onShow: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), strong = true, tint = cs.primary.copy(alpha = 0.12f), onClick = onShow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Аудитория ${loc.room}", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
            Text(loc.summary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            loc.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            loc.building.numbering?.let {
                Text("Нумерация: $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text("Показать на схеме →", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PoiRow(p: Poi, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { PoiText(p) }
    }
}

@Composable
private fun PoiLine(p: Poi, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)) { PoiText(p) }
}

@Composable
private fun PoiText(p: Poi) {
    val cs = MaterialTheme.colorScheme
    Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Text(
        listOfNotNull(Campus.building(p.buildingId).short, p.floor, p.room?.let { "ауд. $it" }).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = cs.primary,
    )
}

/** Карточка здания: описание, нумерация, места по этажам, мои пары в нём на этой неделе. */
@Composable
private fun BuildingCard(id: String, state: UiState, onClose: () -> Unit, onPoi: (Poi) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val b = Campus.building(id)
    val dm = remember { DateTimeFormatter.ofPattern("EE dd.MM", Locale.forLanguageTag("ru")) }
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GlassPill("✕", selected = false, onClick = onClose)
            }
            Text(b.description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            b.numbering?.let {
                Text(
                    "Нумерация аудиторий: $it",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(skinShape(12))
                        .background(cs.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // мои пары в этом здании на открытой неделе
            val week = state.week
            val mine = if (week == null) emptyList() else (0L until 7L).flatMap { i ->
                val d = week.monday.plusDays(i)
                week.lessonsOn(d)
                    .filter { state.prefs.shows(it) && !it.cancelled && Campus.locate(it.room)?.building?.id == id }
                    .map { d to it }
            }.sortedWith(compareBy({ it.first }, { timeKey(it.second.start) }))
            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Мои пары здесь на этой неделе", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                mine.take(8).forEach { (d, l) ->
                    val loc = Campus.locate(l.room)
                    Text(
                        "${d.format(dm)}, ${l.start} — ${l.subject} · ауд. ${loc?.room ?: l.room}" + (loc?.floorLabel?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            val pois = Campus.pois.filter { it.buildingId == id }
            if (pois.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Что здесь", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                // сортируем по этажу: цокольный, 1, 2, …, потом прочее
                fun floorKey(f: String?): Int = when {
                    f == null -> 99
                    f.startsWith("цоколь") -> 0
                    else -> f.takeWhile { it.isDigit() }.toIntOrNull() ?: 50
                }
                pois.groupBy { it.floor ?: "" }.toList().sortedBy { floorKey(it.first) }.forEach { (floor, list) ->
                    Text(
                        floor.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    list.forEach { p ->
                        Text(
                            "• ${p.title}" + (p.room?.let { " — ауд. $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp).clickable { onPoi(p) },
                        )
                    }
                }
            }
        }
    }
}

// ============================================================ сама схема

/**
 * Схема кампуса: «2,5D»-здания в цветах темы. Двумя пальцами — приблизить/подвинуть,
 * нажатие — выбрать здание. Выбранное здание подсвечено, над ним «булавка» с этажом.
 */
@Composable
private fun CampusMap(
    selected: String?,
    pinFloor: String?,
    focusSeq: Int,
    modifier: Modifier = Modifier,
    onTap: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val onTapState = rememberUpdatedState(onTap)

    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val pulse by rememberInfiniteTransition(label = "pin").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )

    GlassCard(modifier.fillMaxWidth(), strong = true) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wPx = with(density) { maxWidth.toPx() }
            val base = wPx / MAP_W
            val hPx = MAP_H * base
            val hDp = with(density) { hPx.toDp() }

            fun clamp(o: Offset, s: Float): Offset = Offset(
                o.x.coerceIn(wPx - wPx * s, 0f),
                o.y.coerceIn(hPx - hPx * s, 0f),
            )

            // плавный «перелёт» к выбранному зданию
            LaunchedEffect(selected, focusSeq, base) {
                val id = selected ?: return@LaunchedEffect
                val c = LABEL_AT[id] ?: return@LaunchedEffect
                val s0 = zoomLevel
                val o0 = offset
                val s1 = if (zoomLevel < 1.6f) 1.8f else zoomLevel
                val o1 = clamp(Offset(wPx / 2 - c.x * base * s1, hPx / 2 - c.y * base * s1), s1)
                animate(0f, 1f, animationSpec = tween(650)) { t, _ ->
                    zoomLevel = s0 + (s1 - s0) * t
                    offset = Offset(o0.x + (o1.x - o0.x) * t, o0.y + (o1.y - o0.y) * t)
                }
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(hDp)
                    .clip(skinShape(26))
                    .pointerInput(base) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val ns = (zoomLevel * zoom).coerceIn(1f, 4f)
                            val o = centroid - (centroid - offset) * (ns / zoomLevel) + pan
                            zoomLevel = ns
                            offset = clamp(o, ns)
                        }
                    }
                    .pointerInput(base) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoomLevel = 1f
                                offset = Offset.Zero
                            },
                        ) { p ->
                            val mp = (p - offset) / (zoomLevel * base)
                            hit(mp)?.let { onTapState.value(it) }
                        }
                    },
            ) {
                drawRect(lerp(g.base, cs.surfaceVariant, 0.35f))
                withTransform({
                    translate(offset.x, offset.y)
                    scale(zoomLevel * base, zoomLevel * base, pivot = Offset.Zero)
                }) {
                    drawGround(cs.onSurface, g.dark)
                    // переходы
                    BRIDGES.forEach { r ->
                        drawRoundRect(cs.outline.copy(alpha = 0.55f), r.topLeft, r.size, CornerRadius(6f))
                    }
                    // здания: сначала «боковины» (тень-объём), потом крыши
                    val order = PARTS.sortedBy { it.rect.bottom }
                    order.forEach { part ->
                        val top = buildingColor(part.id, selected, cs)
                        val side = lerp(top, Color.Black, if (g.dark) 0.45f else 0.28f)
                        drawRoundRect(
                            side,
                            Offset(part.rect.left, part.rect.top + part.depth),
                            part.rect.size,
                            CornerRadius(14f),
                        )
                    }
                    order.forEach { part ->
                        val top = buildingColor(part.id, selected, cs)
                        drawRoundRect(top, part.rect.topLeft, part.rect.size, CornerRadius(14f))
                        // «окна» — тонкие полосы для объёма
                        val lines = ((part.rect.height - 20f) / 22f).toInt()
                        for (i in 1..lines) {
                            val y = part.rect.top + 10f + i * 22f
                            if (y < part.rect.bottom - 8f) {
                                drawLine(
                                    Color.White.copy(alpha = if (g.dark) 0.06f else 0.22f),
                                    Offset(part.rect.left + 10f, y), Offset(part.rect.right - 10f, y), 2f,
                                )
                            }
                        }
                    }
                    // подписи
                    LABEL_AT.forEach { (id, c) ->
                        val sel = id == selected
                        val color = if (sel) cs.onPrimary else labelColor(id, cs)
                        val style = TextStyle(
                            color = color,
                            fontSize = if (id == "canteen") 18.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp,
                        )
                        val layout = measurer.measure(LABELS[id].orEmpty(), style)
                        val k = 1f / base   // текст меряется в пикселях — переводим в единицы схемы
                        withTransform({ scale(k, k, pivot = c) }) {
                            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                        }
                    }
                    // значки: самолёт на 2 корпусе, вход в главный
                    emoji(measurer, "✈️", Offset(600f, 110f), base)
                    emoji(measurer, "⬆", Offset(430f, 715f), base)
                    // булавка над выбранным зданием
                    selected?.let { id ->
                        val c = LABEL_AT[id] ?: return@let
                        val pin = Offset(c.x, c.y - 70f)
                        drawCircle(cs.primary.copy(alpha = (1f - pulse) * 0.45f), radius = 18f + pulse * 30f, center = pin)
                        val path = Path().apply {
                            moveTo(pin.x - 16f, pin.y)
                            lineTo(pin.x, pin.y + 34f)
                            lineTo(pin.x + 16f, pin.y)
                            close()
                        }
                        drawPath(path, cs.primary)
                        drawCircle(cs.primary, radius = 20f, center = pin)
                        drawCircle(cs.onPrimary, radius = 8f, center = pin)
                        pinFloor?.let { f ->
                            val lay = measurer.measure(
                                f,
                                TextStyle(color = cs.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            )
                            val k = 1f / base
                            val w = lay.size.width * k + 20f
                            val h = lay.size.height * k + 10f
                            val tl = Offset(pin.x - w / 2, pin.y - 28f - h)
                            drawRoundRect(cs.primary, tl, Size(w, h), CornerRadius(h / 2))
                            withTransform({ scale(k, k, pivot = Offset(tl.x + 10f, tl.y + 5f)) }) {
                                drawText(lay, topLeft = Offset(tl.x + 10f, tl.y + 5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.emoji(measurer: androidx.compose.ui.text.TextMeasurer, text: String, at: Offset, base: Float) {
    val lay = measurer.measure(text, TextStyle(fontSize = 18.sp))
    val k = 1f / base
    withTransform({ scale(k, k, pivot = at) }) {
        drawText(lay, topLeft = Offset(at.x - lay.size.width / 2f, at.y - lay.size.height / 2f))
    }
}

/** Земля: сквер с деревьями, дорожки, проспект. */
private fun DrawScope.drawGround(ink: Color, dark: Boolean) {
    val green = if (dark) Color(0xFF2E5A3A) else Color(0xFFBFE3C3)
    val tree = if (dark) Color(0xFF3F7A4E) else Color(0xFF7CC08A)
    // дорожки
    val path = ink.copy(alpha = 0.07f)
    drawRect(path, Offset(0f, 700f), Size(MAP_W, 30f))
    drawRect(path, Offset(270f, 180f), Size(24f, 520f))
    drawRect(path, Offset(270f, 180f), Size(700f, 24f))
    // проспект
    drawRect(ink.copy(alpha = 0.12f), ROAD.topLeft, ROAD.size)
    for (x in 0 until 1000 step 60) {
        drawRect(Color.White.copy(alpha = 0.5f), Offset(x + 10f, ROAD.center.y - 2f), Size(30f, 4f))
    }
    // сквер
    drawRoundRect(green, SQUARE.topLeft, SQUARE.size, CornerRadius(30f))
    listOf(
        Offset(360f, 250f), Offset(410f, 235f), Offset(510f, 250f), Offset(350f, 345f),
        Offset(520f, 350f), Offset(470f, 360f), Offset(390f, 300f), Offset(170f, 700f),
        Offset(110f, 690f), Offset(620f, 690f), Offset(960f, 700f), Offset(900f, 290f), Offset(960f, 330f),
    ).forEach { c ->
        drawCircle(tree, radius = 17f, center = c)
        drawCircle(Color.White.copy(alpha = 0.18f), radius = 6f, center = c + Offset(-5f, -5f))
    }
    // «качалка» в сквере
    drawCircle(ink.copy(alpha = 0.25f), radius = 12f, center = Offset(450f, 300f), style = Stroke(4f))
    drawLine(ink.copy(alpha = 0.3f), Offset(430f, 285f), Offset(475f, 305f), 5f)
}

private fun buildingColor(id: String, selected: String?, cs: androidx.compose.material3.ColorScheme): Color = when {
    id == selected -> cs.primary
    id == "main" -> cs.primaryContainer
    id == "k3" -> cs.tertiaryContainer
    id == "k2" -> cs.secondaryContainer
    id == "lib" -> lerp(cs.tertiaryContainer, cs.surfaceContainerHighest, 0.5f)
    else -> cs.surfaceContainerHighest
}

private fun labelColor(id: String, cs: androidx.compose.material3.ColorScheme): Color = when (id) {
    "main" -> cs.onPrimaryContainer
    "k3" -> cs.onTertiaryContainer
    "k2" -> cs.onSecondaryContainer
    "lib" -> cs.onTertiaryContainer
    else -> cs.onSurface
}
