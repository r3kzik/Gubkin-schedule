package com.vadik.raspisanie.games

import kotlin.random.Random

/** Классический тетрис: поле 10×20, 7 фигур, «мешок» из семи, простые отскоки от стен. */
class TetrisEngine(val w: Int = 10, val h: Int = 20, private val rnd: Random = Random.Default) {
    /** 0 — пусто, 1..7 — тип фигуры (для цвета). */
    val board = Array(h) { IntArray(w) }
    var type = 0; private set
    var rot = 0; private set
    var x = 0; private set
    var y = 0; private set
    var next = 0; private set
    var score = 0; private set
    var lines = 0; private set
    var level = 1; private set
    var over = false; private set

    private val bag = ArrayDeque<Int>()

    init { reset() }

    fun reset() {
        board.forEach { it.fill(0) }
        score = 0; lines = 0; level = 1; over = false
        bag.clear()
        next = draw()
        spawn()
    }

    private fun draw(): Int {
        if (bag.isEmpty()) bag.addAll((1..7).shuffled(rnd))
        return bag.removeFirst()
    }

    private fun spawn() {
        type = next
        next = draw()
        rot = 0
        x = (w - size(type)) / 2
        y = if (type == 1) -1 else 0
        if (!fits(type, rot, x, y)) over = true
    }

    /** Клетки фигуры [t] в повороте [r] относительно её угла. */
    fun cells(t: Int, r: Int): List<Pair<Int, Int>> {
        val n = size(t)
        var c = BASE[t - 1]
        repeat(((r % 4) + 4) % 4) { c = c.map { (cx, cy) -> (n - 1 - cy) to cx } }
        return c
    }

    private fun size(t: Int) = when (t) { 1 -> 4; 2 -> 2; else -> 3 }

    fun fits(t: Int, r: Int, px: Int, py: Int): Boolean = cells(t, r).all { (cx, cy) ->
        val bx = px + cx
        val by = py + cy
        bx in 0 until w && by < h && (by < 0 || board[by][bx] == 0)
    }

    fun move(dx: Int): Boolean {
        if (over || !fits(type, rot, x + dx, y)) return false
        x += dx
        return true
    }

    fun rotate(): Boolean {
        if (over) return false
        val r = (rot + 1) % 4
        for (kick in listOf(0, -1, 1, -2, 2)) {
            if (fits(type, r, x + kick, y)) { rot = r; x += kick; return true }
        }
        if (fits(type, r, x, y - 1)) { rot = r; y -= 1; return true }
        return false
    }

    /** Шаг вниз (гравитация или ручной). false — фигура легла. */
    fun step(manual: Boolean = false): Boolean {
        if (over) return false
        if (fits(type, rot, x, y + 1)) {
            y++
            if (manual) score += 1
            return true
        }
        lock()
        return false
    }

    fun tick() = step(false)

    fun hardDrop() {
        if (over) return
        var d = 0
        while (fits(type, rot, x, y + 1)) { y++; d++ }
        score += 2 * d
        lock()
    }

    /** Где фигура окажется при падении (для «тени»). */
    fun ghostY(): Int {
        var gy = y
        while (fits(type, rot, x, gy + 1)) gy++
        return gy
    }

    private fun lock() {
        for ((cx, cy) in cells(type, rot)) {
            val by = y + cy
            if (by < 0) { over = true; return }
            board[by][x + cx] = type
        }
        var cleared = 0
        var row = h - 1
        while (row >= 0) {
            if (board[row].all { it != 0 }) {
                for (r in row downTo 1) board[r] = board[r - 1].copyOf()
                board[0] = IntArray(w)
                cleared++
            } else row--
        }
        if (cleared > 0) {
            score += intArrayOf(0, 100, 300, 500, 800)[cleared] * level
            lines += cleared
            level = lines / 10 + 1
        }
        spawn()
    }

    /** Задержка падения на уровне: быстрее с каждым уровнем. */
    fun speedMs(): Long = maxOf(90L, 800L - (level - 1) * 70L)

    companion object {
        // I, O, T, S, Z, J, L — клетки в своей рамке (I 4×4, O 2×2, остальные 3×3)
        private val BASE = listOf(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
        )
    }
}
