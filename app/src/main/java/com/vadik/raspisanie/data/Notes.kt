package com.vadik.raspisanie.data

import java.time.LocalDate

/** Заметка и фото к конкретной паре (дата + время + предмет). */
data class LessonNote(
    val key: String,
    val text: String = "",
    /** Имена файлов фото в папке notes/. */
    val photos: List<String> = emptyList(),
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = text.isBlank() && photos.isEmpty()

    companion object {
        fun keyOf(date: LocalDate, l: Lesson) = "$date|${l.start}|${l.subject}"
    }
}
