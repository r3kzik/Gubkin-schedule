package com.vadik.raspisanie.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Lesson
import java.io.File
import java.time.LocalDate

/** Цвета для картинки — берутся из текущей темы приложения. */
class ImageColors(
    val primary: Int,
    val onPrimary: Int,
    val tertiary: Int,
    val background: Int,
    val card: Int,
    val text: Int,
    val muted: Int,
    val red: Int,
)

/** Картинка «расписание на день» для отправки в мессенджер. */
object DayImage {
    private const val W = 1080
    private const val PAD = 56f
    private val DAYS = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layout(text: String, p: TextPaint, width: Int, maxLines: Int = 3): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    private fun roomText(l: Lesson) = l.room?.let { if (it.first().isDigit()) "ауд. $it" else it } ?: "аудитория не указана"

    fun render(date: LocalDate, lessons: List<Lesson>, group: String?, c: ImageColors): Bitmap {
        val inner = (W - PAD * 2).toInt()
        val timeW = 190
        val textW = inner - timeW - 48 - 40

        val titleP = paint(64f, c.onPrimary, bold = true)
        val subP = paint(34f, c.onPrimary)
        val timeP = paint(42f, c.text, bold = true)
        val endP = paint(32f, c.muted)
        val roomP = paint(40f, c.primary, bold = true)
        val subjP = paint(36f, c.text)
        val metaP = paint(29f, c.muted)
        val footP = paint(28f, c.muted)

        // --- размеры карточек
        class Row(val l: Lesson, val room: StaticLayout, val subj: StaticLayout, val meta: StaticLayout?, val h: Float)
        val rows = lessons.map { l ->
            val place = Campus.locate(l.room)?.summary
            val room = layout(roomText(l) + (place?.let { " · $it" } ?: ""), TextPaint(roomP).apply { color = if (l.roomChanged) c.red else c.primary }, textW, 2)
            val subj = layout(l.subject, TextPaint(subjP).apply { isStrikeThruText = l.cancelled }, textW, 3)
            val metaText = listOfNotNull(
                l.kind, l.teacher, l.subgroup?.let { "$it подгр." },
                when {
                    l.cancelled -> "ОТМЕНЕНА"
                    l.moved -> "ПЕРЕНЕСЕНА" + (l.movedTo?.let { " на $it" } ?: "")
                    else -> null
                },
            ).joinToString(" · ")
            val meta = metaText.takeIf { it.isNotBlank() }?.let { layout(it, metaP, textW, 2) }
            val h = 40f + room.height + 10f + subj.height + (meta?.let { 10f + it.height } ?: 0f) + 40f
            Row(l, room, subj, meta, maxOf(h, 150f))
        }
        val headerH = 250f
        val listH = if (rows.isEmpty()) 220f else rows.sumOf { it.h.toDouble() }.toFloat() + (rows.size - 1) * 24f
        val height = (headerH + 40f + listH + 40f + 90f).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(c.background)

        // --- шапка с градиентом
        val header = RectF(PAD, PAD * 0.8f, W - PAD, PAD * 0.8f + headerH - 40f)
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(header.left, header.top, header.right, header.bottom, c.primary, c.tertiary, Shader.TileMode.CLAMP)
        }
        cv.drawRoundRect(header, 44f, 44f, hp)
        val today = LocalDate.now()
        val dayWord = when (date) {
            today -> " · сегодня"
            today.plusDays(1) -> " · завтра"
            else -> ""
        }
        cv.drawText(DAYS[date.dayOfWeek.value - 1], header.left + 44f, header.top + 92f, titleP)
        cv.drawText(
            "%02d.%02d".format(date.dayOfMonth, date.monthValue) + dayWord + (group?.let { " · $it" } ?: ""),
            header.left + 44f, header.top + 150f, subP,
        )

        // --- пары
        var y = header.bottom + 40f
        val cardP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.card }
        if (rows.isEmpty()) {
            val r = RectF(PAD, y, W - PAD, y + listH)
            cv.drawRoundRect(r, 36f, 36f, cardP)
            val p = paint(44f, c.text, bold = true).apply { textAlign = Paint.Align.CENTER }
            cv.drawText("Пар нет 🎉", W / 2f, r.centerY() + 16f, p)
            y += listH
        }
        rows.forEach { row ->
            val r = RectF(PAD, y, W - PAD, y + row.h)
            cv.drawRoundRect(r, 36f, 36f, cardP)
            // цветная полоска слева
            val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (row.l.cancelled || row.l.moved) c.muted else c.primary
            }
            cv.drawRoundRect(RectF(r.left + 20f, r.top + 28f, r.left + 30f, r.bottom - 28f), 5f, 5f, stripe)
            cv.drawText(row.l.start, r.left + 56f, r.top + 40f + 42f, timeP)
            if (row.l.end.isNotBlank()) cv.drawText(row.l.end, r.left + 56f, r.top + 40f + 90f, endP)
            var ty = r.top + 40f
            val tx = r.left + 56f + timeW
            cv.save(); cv.translate(tx, ty); row.room.draw(cv); cv.restore()
            ty += row.room.height + 10f
            cv.save(); cv.translate(tx, ty); row.subj.draw(cv); cv.restore()
            ty += row.subj.height
            row.meta?.let { m ->
                ty += 10f
                cv.save(); cv.translate(tx, ty); m.draw(cv); cv.restore()
            }
            y += row.h + 24f
        }

        // --- подпись
        val fp = TextPaint(footP).apply { textAlign = Paint.Align.CENTER }
        cv.drawText("MyGub — расписание РГУ нефти и газа", W / 2f, height - 44f, fp)
        return bmp
    }

    /** Сохранить картинку и открыть «Поделиться». */
    fun share(ctx: Context, bmp: Bitmap, date: LocalDate) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "mygub-%02d-%02d.png".format(date.dayOfMonth, date.monthValue))
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri("", uri)
            ctx.startActivity(Intent.createChooser(send, "Поделиться расписанием").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Не удалось поделиться: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
