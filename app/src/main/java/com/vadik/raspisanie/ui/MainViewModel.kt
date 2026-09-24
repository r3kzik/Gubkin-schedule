package com.vadik.raspisanie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.CaptchaRequiredException
import com.vadik.raspisanie.data.Faculty
import com.vadik.raspisanie.data.Change
import com.vadik.raspisanie.data.Group
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Prefs
import com.vadik.raspisanie.data.Repository
import com.vadik.raspisanie.data.Settings
import com.vadik.raspisanie.data.SiteException
import com.vadik.raspisanie.data.WeekSchedule
import com.vadik.raspisanie.work.AppSync
import com.vadik.raspisanie.work.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** Экран выбора группы. */
data class PickerState(
    val loading: Boolean = false,
    val faculties: List<Faculty> = emptyList(),
    val faculty: Faculty? = null,
    val groups: List<Group> = emptyList(),
    val message: String? = null,
)

data class CaptchaState(
    val image: ByteArray? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Открытая карточка пары. */
data class LessonDetail(
    val date: LocalDate,
    val lesson: Lesson,
    val history: List<Change> = emptyList(),
)

data class UiState(
    val starting: Boolean = true,
    val settings: Settings? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val week: WeekSchedule? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val changes: List<String> = emptyList(),
    val picker: PickerState? = null,
    val captcha: CaptchaState? = null,
    val prefs: Prefs = Prefs(),
    val showSettings: Boolean = false,
    val detail: LessonDetail? = null,
) {
    val monday: LocalDate get() = Repository.mondayOf(selectedDate)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val repo = app.repo

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Что повторить после успешного ввода капчи. */
    private var afterCaptcha: (() -> Unit)? = null
    private var refreshJob: Job? = null
    private var refreshMonday: LocalDate? = null
    private var lastResumeDay: LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            val (s, prefs) = withContext(Dispatchers.IO) { repo.settings() to repo.prefs() }
            _state.update { it.copy(prefs = prefs) }
            if (s == null) {
                _state.update { it.copy(starting = false) }
                autoSetup()
            } else {
                _state.update { it.copy(starting = false, settings = s) }
                showWeek(LocalDate.now())
            }
        }
    }

    // ------------------------------------------------------------ расписание

    fun selectDate(date: LocalDate) {
        val weekChanged = Repository.mondayOf(date) != _state.value.monday
        _state.update { if (weekChanged) it.copy(selectedDate = date, week = null) else it.copy(selectedDate = date) }
        if (weekChanged) showWeek(date)
    }

    /** Выбор дня текущей недели по индексу (0 = понедельник) — для свайпов. */
    fun selectDayOfWeek(index: Int) {
        val d = _state.value.monday.plusDays(index.toLong())
        if (d != _state.value.selectedDate) selectDate(d)
    }

    fun shiftWeek(weeks: Long) = selectDate(_state.value.selectedDate.plusWeeks(weeks))

    fun goToday() = selectDate(LocalDate.now())

    /** Показать сохранённую неделю сразу, а потом обновить из сети, если она устарела. */
    private fun showWeek(date: LocalDate) {
        val s = _state.value.settings ?: return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { repo.cachedWeek(s.groupId, date) }
            val monday = Repository.mondayOf(date)
            if (_state.value.monday != monday) return@launch // пока читали, пользователь ушёл на другую неделю
            _state.update { it.copy(week = cached, error = null) }
            if (cached == null || isStale(cached)) refresh()
        }
    }

    private fun isStale(w: WeekSchedule) =
        System.currentTimeMillis() - w.fetchedAt > STALE_MS

    /** Вызывается, когда приложение снова открыли. */
    fun onResume() {
        val today = LocalDate.now()
        if (today != lastResumeDay) {
            // приложение открыли на следующий день — сразу показываем сегодняшний
            lastResumeDay = today
            goToday()
        }
        val st = _state.value
        val w = st.week
        if (st.settings != null && (w == null || isStale(w))) refresh()
    }

    fun refresh() {
        val s = _state.value.settings ?: return
        val date = _state.value.selectedDate
        val monday = Repository.mondayOf(date)
        if (refreshJob?.isActive == true) {
            if (refreshMonday == monday) return
            refreshJob?.cancel() // пользователь перелистнул неделю — старый запрос уже не нужен
        }
        refreshMonday = monday
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val r = withContext(Dispatchers.IO) {
                    repo.refreshWeek(s, date).also { AppSync.afterDataChange(app) }
                }
                _state.update { st ->
                    if (st.monday != r.week.monday) st.copy(refreshing = false)
                    else st.copy(
                        refreshing = false,
                        week = r.week,
                        changes = (r.changes + st.changes).distinct(),
                    )
                }
                Notifier.clearCaptcha(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false) }
                handleError(e) { refresh() }
            }
        }
    }

    fun dismissChanges() = _state.update { it.copy(changes = emptyList()) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private fun handleError(e: Exception, retry: () -> Unit) {
        when (e) {
            is CaptchaRequiredException -> {
                afterCaptcha = retry
                openCaptcha()
            }
            is SiteException -> _state.update { it.copy(error = e.message) }
            is IOException -> _state.update {
                it.copy(
                    error = if (it.week != null) "Нет связи с сайтом вуза. Показано сохранённое расписание."
                    else "Нет связи с сайтом вуза. Проверьте интернет и нажмите ⟳.",
                )
            }
            else -> _state.update { it.copy(error = "Ошибка: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    // ------------------------------------------------------------ выбор группы

    private fun autoSetup() {
        _state.update { it.copy(picker = PickerState(loading = true, message = "Ищу группу ${App.DEFAULT_GROUP}…")) }
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    repo.findGroup(App.DEFAULT_FACULTY_HINT, App.DEFAULT_GROUP)
                }
                if (found != null) applyGroup(found)
                else openPicker("Группа ${App.DEFAULT_GROUP} не нашлась автоматически — выберите факультет и группу")
            } catch (e: Exception) {
                _state.update { it.copy(picker = PickerState(loading = false)) }
                handleError(e) { autoSetup() }
                if (e !is CaptchaRequiredException) {
                    _state.update {
                        it.copy(picker = PickerState(message = "Не удалось связаться с сайтом: ${it.error ?: e.message}"))
                    }
                }
            }
        }
    }

    fun openPicker(message: String? = null) {
        _state.update { it.copy(picker = PickerState(loading = true)) }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.faculties() }
                _state.update { it.copy(picker = PickerState(faculties = list, message = message)) }
            } catch (e: Exception) {
                _state.update { it.copy(picker = PickerState(message = "Не удалось загрузить список факультетов")) }
                handleError(e) { openPicker() }
            }
        }
    }

    fun retryPicker() = if (_state.value.settings == null) autoSetup() else openPicker()

    fun pickFaculty(f: Faculty) {
        val p = _state.value.picker ?: PickerState()
        _state.update { it.copy(picker = p.copy(loading = true, faculty = f, groups = emptyList())) }
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) { repo.groups(f.id) }
                _state.update { it.copy(picker = it.picker?.copy(loading = false, groups = groups)) }
            } catch (e: Exception) {
                _state.update { it.copy(picker = it.picker?.copy(loading = false, faculty = null)) }
                handleError(e) { pickFaculty(f) }
            }
        }
    }

    fun backToFaculties() = _state.update { it.copy(picker = it.picker?.copy(faculty = null, groups = emptyList())) }

    fun pickGroup(g: Group) {
        val f = _state.value.picker?.faculty
        applyGroup(Settings(g.id, g.code, f?.name.orEmpty()))
    }

    fun closePicker() {
        if (_state.value.settings != null) _state.update { it.copy(picker = null) }
    }

    private fun applyGroup(s: Settings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.saveSettings(s)
                AppSync.afterDataChange(app)
            }
            _state.update {
                it.copy(settings = s, picker = null, week = null, changes = emptyList(), error = null)
            }
            showWeek(_state.value.selectedDate)
        }
    }

    // ------------------------------------------------------------ настройки

    fun openSettings() = _state.update { it.copy(showSettings = true) }
    fun closeSettings() = _state.update { it.copy(showSettings = false) }

    fun updatePrefs(change: (Prefs) -> Prefs) {
        val p = change(_state.value.prefs)
        _state.update { it.copy(prefs = p) }
        viewModelScope.launch(Dispatchers.IO) {
            repo.savePrefs(p)
            AppSync.afterDataChange(app)
        }
    }

    fun rawFile() = repo.rawFile()

    // ------------------------------------------------------------ карточка пары

    fun openLesson(date: LocalDate, lesson: Lesson) {
        _state.update { it.copy(detail = LessonDetail(date, lesson)) }
        viewModelScope.launch {
            val h = withContext(Dispatchers.IO) { repo.historyFor(date, lesson.subject) }
            _state.update { st ->
                if (st.detail?.lesson == lesson) st.copy(detail = st.detail.copy(history = h)) else st
            }
        }
    }

    fun closeLesson() = _state.update { it.copy(detail = null) }

    // ------------------------------------------------------------ капча

    fun openCaptcha() {
        _state.update { it.copy(captcha = CaptchaState(loading = true)) }
        viewModelScope.launch {
            try {
                val img = withContext(Dispatchers.IO) { app.api.captchaImage() }
                _state.update { it.copy(captcha = CaptchaState(image = img)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(captcha = CaptchaState(error = "Не удалось загрузить картинку: ${e.message}"))
                }
            }
        }
    }

    fun submitCaptcha(code: String) {
        val current = _state.value.captcha ?: return
        _state.update { it.copy(captcha = current.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { app.api.validateCaptcha(code) }
                if (ok) {
                    _state.update { it.copy(captcha = null) }
                    Notifier.clearCaptcha(app)
                    afterCaptcha?.invoke()
                    afterCaptcha = null
                } else {
                    val img = withContext(Dispatchers.IO) { app.api.captchaImage() }
                    _state.update { it.copy(captcha = CaptchaState(image = img, error = "Код не подошёл, вот новая картинка")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(captcha = current.copy(loading = false, error = e.message)) }
            }
        }
    }

    fun cancelCaptcha() {
        afterCaptcha = null
        _state.update {
            it.copy(
                captcha = null,
                error = if (it.week != null) "Сайт просит капчу — показано сохранённое расписание" else
                    "Сайт просит капчу. Нажмите «Обновить», чтобы ввести код.",
            )
        }
    }

    companion object {
        private const val STALE_MS = 30 * 60 * 1000L // 30 минут
    }
}
