package com.vadik.raspisanie.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Публичное API расписания lk.gubkin.ru — без логина.
 * Перед обращениями к API сайт требует «визит» на /schedule/ в той же сессии (cookie PHPSESSID),
 * иначе его защита (WAF) отвечает ошибкой. Иногда сайт просит капчу (HTTP 429).
 */
class GubkinApi(cookieFile: File) : ScheduleSource {

    private val cookieJar = FileCookieJar(cookieFile)

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder = Request.Builder()
        .url(BASE + path)
        .header("User-Agent", UA)
        .header("Accept", "application/json, text/plain, */*")
        .header("Referer", BASE + "schedule/")

    private fun visit(force: Boolean) {
        if (!force && cookieJar.hasSession()) return
        client.newCall(request("schedule/").header("Accept", "text/html,*/*").build())
            .execute().use { /* нужны только cookie */ }
    }

    /** GET к API с одной повторной попыткой после нового «визита», если сессия протухла. */
    @Volatile
    private var lastUrl: String? = null
    override val lastRequestUrl: String? get() = lastUrl

    private fun apiGet(path: String): String {
        lastUrl = BASE + path
        visit(force = false)
        var attempt = 0
        while (true) {
            client.newCall(request(path).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 429 -> throw CaptchaRequiredException()
                    resp.isSuccessful && body.trimStart().startsWith("{") -> return body
                    attempt == 0 -> Unit // попробуем ещё раз с новой сессией
                    !resp.isSuccessful -> throw SiteException("Сайт ответил ошибкой ${resp.code}")
                    else -> throw SiteException("Сайт вернул страницу вместо данных")
                }
            }
            attempt++
            visit(force = true)
        }
    }

    override fun weekJson(date: LocalDate, groupId: String): String =
        apiGet("schedule/api/api.php?act=schedule&date=${date.dayOfMonth}-${date.monthValue}-${date.year}&groupId=$groupId")

    override fun facultiesJson(): String =
        apiGet("schedule/api/api.php?act=list&method=getFaculties")

    override fun groupsJson(facultyId: String): String =
        apiGet("schedule/api/api.php?act=list&method=getFacultyGroups&facultyId=$facultyId")

    override fun teacherWeekJson(date: LocalDate, teacherId: String, divisionId: String?, variant: Int): String {
        val d = "${date.dayOfMonth}-${date.monthValue}-${date.year}"
        val id = java.net.URLEncoder.encode(teacherId, "UTF-8")
        val div = divisionId?.let { "&divisionId=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return apiGet(
            "schedule/api/api.php?" + TEACHER_WEEK[variant].replace("{d}", d).replace("{id}", id).replace("{div}", div),
        )
    }

    override fun teachersJson(variant: Int): String =
        apiGet("schedule/api/api.php?" + TEACHER_LIST[variant])

    override val teacherWeekVariants: Int get() = TEACHER_WEEK.size
    override val teacherListVariants: Int get() = TEACHER_LIST.size

    /** Картинка капчи (JPEG/PNG). */
    fun captchaImage(): ByteArray {
        visit(force = false)
        client.newCall(
            request("schedule/api/api.php?act=Captcha&method=generateCaptcha")
                .header("Accept", "image/*,*/*").build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw SiteException("Не удалось загрузить капчу (${resp.code})")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /** true — код принят. Бросает CaptchaRequiredException, если сайт просит подождать (429). */
    fun validateCaptcha(code: String): Boolean {
        val payload = "{\"key\":${quote(code.trim())}}"
            .toRequestBody("application/json".toMediaType())
        client.newCall(
            request("schedule/api/api.php?act=Captcha&method=validateCaptcha").post(payload).build()
        ).execute().use { resp ->
            if (resp.code == 429) throw SiteException("Сайт просит подождать минуту — попробуйте ещё раз чуть позже")
            val body = resp.body?.string().orEmpty()
            return try {
                ScheduleParser.parseRoot(body)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val BASE = "https://lk.gubkin.ru/"

        /** Возможные адреса расписания преподавателя — рабочий приложение запомнит. */
        private val TEACHER_WEEK = listOf(
            // так делает сам сайт: act=schedule&date=25-9-2026&divisionId=1507&teacherId=BgJqbgw=
            "act=schedule&date={d}{div}&teacherId={id}",
            "act=schedule&date={d}&teacherId={id}",
        )
        private val TEACHER_LIST = listOf(
            "act=list&method=getTeachers",
            "act=list&method=getLecturers",
        )
        private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}

/** Cookie, которые переживают перезапуск приложения (сессия и «разблокировка» после капчи). */
private class FileCookieJar(private val file: File) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    init {
        val base = GubkinApi.BASE.toHttpUrl()
        runCatching {
            file.readLines().mapNotNullTo(cookies) { line -> Cookie.parse(base, line) }
        }
    }

    @Synchronized
    fun hasSession(): Boolean {
        val now = System.currentTimeMillis()
        return cookies.any { it.name == "PHPSESSID" && it.expiresAt > now }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            this.cookies += c
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    private fun persist() {
        runCatching { file.writeText(cookies.joinToString("\n") { it.toString() }) }
    }
}
