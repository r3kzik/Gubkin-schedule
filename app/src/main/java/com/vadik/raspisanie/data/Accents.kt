package com.vadik.raspisanie.data

/** Готовые цвета интерфейса. seed — ARGB. */
data class Accent(val id: String, val title: String, val seed: Long)

object Accents {
    val presets = listOf(
        Accent("blue", "Синий", 0xFF3D7BFF),
        Accent("violet", "Фиолетовый", 0xFF7C5CFF),
        Accent("pink", "Розовый", 0xFFFF4F9A),
        Accent("red", "Красный", 0xFFFF5A5F),
        Accent("orange", "Оранжевый", 0xFFFF8A3D),
        Accent("amber", "Янтарный", 0xFFF5B301),
        Accent("green", "Зелёный", 0xFF22B573),
        Accent("teal", "Бирюзовый", 0xFF14B8B0),
        Accent("sky", "Голубой", 0xFF2FA8F5),
        Accent("graphite", "Графит", 0xFF7D8799),
    )

    /** Тёмные насыщенные цвета: интерфейс остаётся глубоким и в светлой теме. */
    val dark = listOf(
        Accent("forest", "Тёмно-зелёный", 0xFF1F5C3B),
        Accent("bottle", "Бутылочный", 0xFF0E4B3A),
        Accent("pine", "Хвойный", 0xFF2E4A36),
        Accent("olive", "Оливковый", 0xFF4B5320),
        Accent("petrol", "Петроль", 0xFF0F4C5C),
        Accent("navy", "Тёмно-синий", 0xFF1C2E5A),
        Accent("indigo", "Индиго", 0xFF2E2A6B),
        Accent("plum", "Баклажан", 0xFF4A2458),
        Accent("wine", "Бордо", 0xFF6B1E34),
        Accent("cocoa", "Шоколад", 0xFF4E3024),
        Accent("slate", "Сланец", 0xFF2F343C),
    )

    /** Цвета, для которых есть свой вариант иконки приложения. */
    val withIcons get() = presets + dark

    /** «Цвет года» Pantone — подпись: год и название. */
    val pantone = listOf(
        Accent("p2025", "2025 · Mocha Mousse", 0xFFA47864),
        Accent("p2024", "2024 · Peach Fuzz", 0xFFFFBE98),
        Accent("p2023", "2023 · Viva Magenta", 0xFFBB2649),
        Accent("p2022", "2022 · Very Peri", 0xFF6667AB),
        Accent("p2021", "2021 · Illuminating", 0xFFF5DF4D),
        Accent("p2020", "2020 · Classic Blue", 0xFF0F4C81),
        Accent("p2019", "2019 · Living Coral", 0xFFFF6F61),
        Accent("p2018", "2018 · Ultra Violet", 0xFF5F4B8B),
        Accent("p2017", "2017 · Greenery", 0xFF88B04B),
        Accent("p2016", "2016 · Serenity", 0xFF92A8D1),
        Accent("p2015", "2015 · Marsala", 0xFF955251),
        Accent("p2014", "2014 · Radiant Orchid", 0xFFB163A3),
        Accent("p2013", "2013 · Emerald", 0xFF009473),
    )

    private val all = presets + dark + pantone

    fun seedOf(id: String): Long = all.firstOrNull { it.id == id }?.seed ?: presets[0].seed

    /** Для иконки: ближайший по оттенку базовый цвет (иконки есть только для базовых). */
    fun nearestPresetId(id: String): String {
        if (withIcons.any { it.id == id }) return id
        val seed = all.firstOrNull { it.id == id }?.seed ?: return "blue"
        fun hsv(c: Long): FloatArray {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            val h = when {
                d == 0f -> 0f
                max == r -> 60f * (((g - b) / d) % 6f)
                max == g -> 60f * (((b - r) / d) + 2f)
                else -> 60f * (((r - g) / d) + 4f)
            }.let { if (it < 0) it + 360f else it }
            return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
        }
        val t = hsv(seed)
        if (t[1] < 0.2f) return "graphite"
        if (t[0] in 70f..170f) return "green"
        return presets.filter { it.id != "graphite" }.minByOrNull { p ->
            val h = hsv(p.seed)[0]
            val dh = kotlin.math.abs(h - t[0])
            minOf(dh, 360f - dh)
        }?.id ?: "blue"
    }
}
