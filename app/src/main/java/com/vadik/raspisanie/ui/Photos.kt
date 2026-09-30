package com.vadik.raspisanie.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/** Фото к парам: уменьшаем до разумного размера (≈1600 px), чтобы не занимать память и копию. */
object Photos {
    private const val MAX = 1600

    private fun decodeScaled(open: () -> java.io.InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX) sample *= 2
        return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun rotation(open: () -> java.io.InputStream?): Int = runCatching {
        open()?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun save(bmp: Bitmap, deg: Int, out: File): Boolean {
        var b = bmp
        val longSide = max(b.width, b.height)
        if (longSide > MAX) {
            val k = MAX.toFloat() / longSide
            b = Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
        }
        if (deg != 0) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return out.length() > 0
    }

    fun shrinkInPlace(f: File): Boolean = runCatching {
        val bmp = decodeScaled { f.inputStream() } ?: return false
        save(bmp, rotation { f.inputStream() }, f)
    }.getOrDefault(false)

    fun copyShrunk(ctx: Context, uri: Uri, out: File): Boolean = runCatching {
        val open = { ctx.contentResolver.openInputStream(uri) }
        val bmp = decodeScaled(open) ?: return false
        save(bmp, rotation(open), out)
    }.getOrDefault(false)
}
