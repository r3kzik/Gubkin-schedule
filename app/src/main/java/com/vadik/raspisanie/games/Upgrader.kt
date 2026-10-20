package com.vadik.raspisanie.games

import kotlin.random.Random

/** Предмет симулятора: всё ненастоящее, монеты игровые. */
data class UItem(val id: Int, val name: String, val emoji: String, val value: Int, val tier: Int)

/** Симулятор апгрейдера: ставишь свой предмет ради более дорогого, шанс = цена твоего / цена цели. */
object Upgrader {
    /** Предметы в «нефтегазовом» духе — по возрастанию цены. */
    val ITEMS = listOf(
        UItem(0, "Ржавая гайка", "🔩", 3, 0),
        UItem(1, "Рабочие перчатки", "🧤", 6, 0),
        UItem(2, "Каска", "⛑", 10, 0),
        UItem(3, "Рулетка геодезиста", "📏", 18, 1),
        UItem(4, "Манометр", "⏲", 30, 1),
        UItem(5, "Задвижка", "🔧", 50, 1),
        UItem(6, "Буровое долото", "⚙", 90, 2),
        UItem(7, "Насос ЭЦН", "🛢", 160, 2),
        UItem(8, "Газовый компрессор", "🌀", 300, 3),
        UItem(9, "Буровая установка", "🏗", 550, 3),
        UItem(10, "Нефтепровод", "🚰", 1000, 4),
        UItem(11, "Танкер", "🚢", 2000, 4),
        UItem(12, "НПЗ", "🏭", 5000, 5),
        UItem(13, "Месторождение", "💎", 12000, 5),
    )

    fun item(id: Int) = ITEMS.firstOrNull { it.id == id }

    /** Доля «заведения» — как на настоящих апгрейдерах, чуть меньше честного шанса. */
    private const val EDGE = 0.92

    /** Шанс успеха, 1–90 %. */
    fun chance(from: UItem, to: UItem): Double = (from.value.toDouble() / to.value * EDGE).coerceIn(0.01, 0.90)

    /** Крутим: стрелка останавливается на угле 0–360; успех, если попала в зелёный сектор. */
    fun roll(chance: Double, rnd: Random = Random.Default): Pair<Boolean, Float> {
        val angle = rnd.nextFloat() * 360f
        return (angle < chance * 360f) to angle
    }

    /** Цель для множителя ×k: самый дешёвый предмет не дешевле value·k. */
    fun targetFor(from: UItem, k: Double): UItem? = ITEMS.firstOrNull { it.value >= from.value * k && it.value > from.value }

    const val CRATE_PRICE = 50
    const val FREE_COOLDOWN_MS = 30_000L
    const val START_COINS = 100

    /** Ящик: бесплатный — в основном мелочь, платный — средняя цена чуть ниже стоимости ящика. */
    fun crate(paid: Boolean, rnd: Random = Random.Default): UItem {
        val weights = if (paid) intArrayOf(0, 0, 0, 80, 56, 34, 18, 8, 3, 1, 0, 0, 0, 0)
        else intArrayOf(40, 28, 18, 8, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0)
        var r = rnd.nextInt(weights.sum())
        for (i in weights.indices) {
            r -= weights[i]
            if (r < 0) return ITEMS[i]
        }
        return ITEMS[0]
    }
}
