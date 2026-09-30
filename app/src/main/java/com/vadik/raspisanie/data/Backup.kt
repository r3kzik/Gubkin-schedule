package com.vadik.raspisanie.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Резервная копия: настройки, группа, ДЗ, свои дела, заметки и фото — одним .zip-файлом.
 * Расписание с сайта не копируем — оно скачается заново.
 */
object Backup {
    /** Какие файлы из папки данных входят в копию. */
    val FILES = listOf("settings.json", "prefs.json", "homework.json", "personal.json", "notes.json")
    const val PHOTOS_DIR = "notes"
    private const val MARKER = "mygub-backup.txt"

    fun export(dir: File, out: OutputStream, appVersion: String) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MARKER))
            zip.write("MyGub backup\nversion=$appVersion\ncreated=${System.currentTimeMillis()}\n".toByteArray())
            zip.closeEntry()
            for (name in FILES) {
                val f = File(dir, name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            File(dir, PHOTOS_DIR).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("$PHOTOS_DIR/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Что восстановлено. */
    data class Result(val files: Int, val photos: Int)

    /**
     * Восстановить из копии. Берутся только известные файлы (никаких путей из архива — защита от подмены),
     * остальное игнорируется. Бросает IllegalArgumentException, если это не копия MyGub.
     */
    fun import(dir: File, input: InputStream): Result {
        val tmp = File(dir, "restore_tmp").apply { deleteRecursively(); mkdirs() }
        var marker = false
        var files = 0
        var photos = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    when {
                        e.isDirectory -> Unit
                        name == MARKER -> marker = true
                        name in FILES -> {
                            File(tmp, name).outputStream().use { zip.copyTo(it) }
                            files++
                        }
                        name.startsWith("$PHOTOS_DIR/") -> {
                            val base = name.substringAfter("/")
                            // только простое имя файла: без «..» и вложенных папок
                            if (base.isNotEmpty() && base.all { it.isLetterOrDigit() || it in "._-" } && !base.startsWith(".")) {
                                val pd = File(tmp, PHOTOS_DIR).apply { mkdirs() }
                                File(pd, base).outputStream().use { zip.copyTo(it) }
                                photos++
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (!marker) throw IllegalArgumentException("Это не резервная копия MyGub")
            // всё прочитали без ошибок — переносим на место
            for (name in FILES) {
                val f = File(tmp, name)
                if (f.exists()) f.copyTo(File(dir, name), overwrite = true)
            }
            File(tmp, PHOTOS_DIR).listFiles()?.forEach { f ->
                f.copyTo(File(File(dir, PHOTOS_DIR).apply { mkdirs() }, f.name), overwrite = true)
            }
            return Result(files, photos)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
