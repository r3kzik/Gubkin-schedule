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

    fun seedOf(id: String): Long = presets.firstOrNull { it.id == id }?.seed ?: presets[0].seed
}
