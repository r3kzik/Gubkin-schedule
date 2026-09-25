package com.vadik.raspisanie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadik.raspisanie.App
import com.vadik.raspisanie.data.CaptchaRequiredException
import com.vadik.raspisanie.data.Faculty
import com.vadik.raspisanie.data.Change
import com.vadik.raspisanie.data.Campus
import com.vadik.raspisanie.data.Group
import com.vadik.raspisanie.data.RoomLocation
import com.vadik.raspisanie.data.Homework
import com.vadik.raspisanie.data.SubjectInfo
import com.vadik.raspisanie.data.Lesson
import com.vadik.raspisanie.data.Prefs
import com.vadik.raspisanie.data.Repository
import com.vadik.raspisanie.data.Settings
import com.vadik.raspisanie.data.SiteException
import com.vadik.raspisanie.data.WeekSchedule
import com.vadik.raspisanie.data.UpdateInfo
import com.vadik.raspisanie.data.Teacher
import com.vadik.raspisanie.data.TeacherWeek
import com.vadik.raspisanie.security.Integrity
import com.vadik.raspisanie.work.Updater
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

/** Первый запуск: привет → факультет → группа → подгруппа. */
data class OnboardingState(
    val step: Int = 0,
    val loading: Boolean = false,
    val message: String? = null,
    val faculties: List<Faculty> = emptyList(),
    val faculty: Faculty? = null,
    val groups: List<Group> = emptyList(),
    val group: Group? = null,
    val subgroup: Int = 0,
)

/** Черновик домашнего задания в редакторе. */
data class HomeworkDraft(
    val id: String?,
    val subject: String,
    val text: String,
    val due: LocalDate?,
    /** Дата ближайшей пары по предмету — для кнопки «К следующей паре». */
    val nextLesson: LocalDate?,
)

/** Открытая карточка пары. */
data class LessonDetail(
    val date: LocalDate,
    val lesson: Lesson,
    val history: List<Change> = emptyList(),
)

/** Вкладка «Преподаватели». */
data class TeachersState(
    val query: String = "",
    /** Преподаватели своей группы (из сохранённого расписания). */
    val mine: List<Teacher> = emptyList(),
    /** Все преподаватели вуза с сайта; null — список ещё не загружен или сайт его не отдаёт. */
    val all: List<Teacher>? = null,
    val loadingList: Boolean = false,
    val listTried: Boolean = false,
    val selected: Teacher? = null,
    val monday: LocalDate = Repository.mondayOf(LocalDate.now()),
    val week: TeacherWeek? = null,
    val loadingWeek: Boolean = false,
    val error: String? = null,
)

/** Автообновление приложения. */
data class UpdateState(
    val info: UpdateInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    /** 0..1, или -1, если размер неизвестен. */
    val progress: Float = 0f,
    val message: String? = null,
    val showDialog: Boolean = false,
    /** Плашку «вышло обновление» скрыли до следующего запуска. */
    val bannerHidden: Boolean = false,
    /** Нужно разрешить MyGub устанавливать приложения. */
    val needPermission: Boolean = false,
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
    /** 0 — расписание, 1 — предметы, 2 — преподаватели, 3 — карта, 4 — настройки. */
    val tab: Int = 0,
    val showThemeEditor: Boolean = false,
    val homework: List<Homework> = emptyList(),
    val subjects: List<SubjectInfo> = emptyList(),
    val hwDraft: HomeworkDraft? = null,
    val onboarding: OnboardingState? = null,
    /** Что показать на карте (из расписания: «Где это?»). */
    val mapFocus: RoomLocation? = null,
    /** Счётчик, чтобы повторный показ того же места снова запускал анимацию. */
    val mapFocusSeq: Int = 0,
    val update: UpdateState = UpdateState(),
    val teachers: TeachersState = TeachersState(),
    /** Приложение подписано чужим ключом — это не оригинальный MyGub. */
    val tampered: Boolean = false,
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
            val original = withContext(Dispatchers.Default) { Integrity.isOriginal(app) }
            if (!original) _state.update { it.copy(tampered = true) }
        }
        viewModelScope.launch {
            // итог работы системного установщика (отмена, ошибка)
            Updater.installMessage.collect { msg ->
                if (msg != null) _state.update { it.copy(update = it.update.copy(downloading = false, message = msg)) }
            }
        }
        viewModelScope.launch {
            val (s, prefs) = withContext(Dispatchers.IO) { repo.settings() to repo.prefs() }
            if (prefs.autoUpdateCheck) launch { checkUpdates(manual = false) }
            val hw = withContext(Dispatchers.IO) { repo.homework() }
            _state.update { it.copy(prefs = prefs, homework = hw) }
            if (s == null) {
                _state.update { it.copy(starting = false) }
                startOnboarding()
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
        val u = _state.value.update
        if (u.needPermission && Updater.canInstall(app)) {
            // вернулись из настроек с разрешением — продолжаем обновление
            _state.update { it.copy(update = it.update.copy(needPermission = false)) }
            startUpdate()
        }
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

    // ------------------------------------------------------------ первый запуск

    private fun startOnboarding() {
        _state.update { it.copy(onboarding = OnboardingState()) }
        loadOnboardingFaculties() // список грузим заранее, пока человек читает приветствие
    }

    private fun loadOnboardingFaculties() {
        _state.update { it.copy(onboarding = it.onboarding?.copy(loading = true, message = null)) }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.faculties() }
                _state.update { it.copy(onboarding = it.onboarding?.copy(loading = false, faculties = list)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(onboarding = it.onboarding?.copy(loading = false, message = "Не удалось загрузить список факультетов"))
                }
                if (e is CaptchaRequiredException) handleError(e) { loadOnboardingFaculties() }
            }
        }
    }

    fun onboardingRetry() = loadOnboardingFaculties()

    fun onboardingNext() = _state.update { st ->
        st.copy(onboarding = st.onboarding?.let { it.copy(step = it.step + 1) })
    }

    fun onboardingBack() = _state.update { st ->
        st.copy(onboarding = st.onboarding?.let { it.copy(step = (it.step - 1).coerceAtLeast(0)) })
    }

    fun onboardingFaculty(f: Faculty) {
        _state.update { st ->
            st.copy(onboarding = st.onboarding?.copy(faculty = f, groups = emptyList(), group = null, step = 2, loading = true, message = null))
        }
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) { repo.groups(f.id) }
                _state.update { it.copy(onboarding = it.onboarding?.copy(loading = false, groups = groups)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(onboarding = it.onboarding?.copy(loading = false, message = "Не удалось загрузить группы"))
                }
                if (e is CaptchaRequiredException) handleError(e) { onboardingFaculty(f) }
            }
        }
    }

    fun onboardingGroup(g: Group) = _state.update { st ->
        st.copy(onboarding = st.onboarding?.copy(group = g, step = 3))
    }

    fun onboardingSubgroup(n: Int) = _state.update { st ->
        st.copy(onboarding = st.onboarding?.copy(subgroup = n))
    }

    fun finishOnboarding() {
        val ob = _state.value.onboarding ?: return
        val g = ob.group ?: return
        val settings = Settings(g.id, g.code, ob.faculty?.name.orEmpty())
        val prefs = _state.value.prefs.copy(subgroup = ob.subgroup)
        _state.update { it.copy(onboarding = null, prefs = prefs) }
        viewModelScope.launch(Dispatchers.IO) { repo.savePrefs(prefs) }
        applyGroup(settings)
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

    fun openSettings() = selectTab(TAB_SETTINGS)

    /** Показать аудиторию на карте кампуса. */
    fun showOnMap(room: String?) {
        val loc = Campus.locate(room) ?: return
        _state.update { it.copy(mapFocus = loc, mapFocusSeq = it.mapFocusSeq + 1, tab = TAB_MAP, detail = null) }
    }

    /** Показать здание на карте (из списка мест). */
    fun showBuildingOnMap(buildingId: String) {
        val loc = RoomLocation(Campus.building(buildingId), null, "")
        _state.update { it.copy(mapFocus = loc, mapFocusSeq = it.mapFocusSeq + 1, tab = TAB_MAP) }
    }
    fun closeSettings() = selectTab(0)

    fun selectTab(i: Int) {
        _state.update { it.copy(tab = i) }
        if (i == TAB_SUBJECTS) loadSubjects()
        if (i == TAB_TEACHERS) loadTeachers()
    }

    fun openThemeEditor() = _state.update { it.copy(showThemeEditor = true) }
    fun closeThemeEditor() = _state.update { it.copy(showThemeEditor = false) }

    // ------------------------------------------------------------ предметы и ДЗ

    fun loadSubjects() {
        val s = _state.value.settings ?: return
        val prefs = _state.value.prefs
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.subjects(s.groupId, prefs) }
            _state.update { it.copy(subjects = list) }
        }
    }

    /** Открыть редактор ДЗ: новое (existing == null) или правка существующего. */
    fun openHomeworkEditor(subject: String, existing: Homework? = null) {
        val s = _state.value.settings
        viewModelScope.launch {
            val next = if (s == null) null else withContext(Dispatchers.IO) {
                repo.subjects(s.groupId, _state.value.prefs).firstOrNull { it.name == subject }?.nextDate
            }
            _state.update {
                it.copy(
                    hwDraft = HomeworkDraft(
                        id = existing?.id,
                        subject = subject,
                        text = existing?.text.orEmpty(),
                        due = existing?.due ?: next,
                        nextLesson = next,
                    ),
                )
            }
        }
    }

    fun closeHomeworkEditor() = _state.update { it.copy(hwDraft = null) }

    fun saveHomework(draft: HomeworkDraft) {
        if (draft.text.isBlank()) return
        val old = _state.value.homework.firstOrNull { it.id == draft.id }
        val h = Homework(
            id = draft.id ?: java.util.UUID.randomUUID().toString(),
            subject = draft.subject,
            text = draft.text.trim(),
            due = draft.due,
            done = old?.done ?: false,
            createdAt = old?.createdAt ?: System.currentTimeMillis(),
        )
        _state.update { it.copy(hwDraft = null) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.upsertHomework(h) }
            _state.update { it.copy(homework = list) }
        }
    }

    fun toggleHomework(h: Homework) {
        val upd = h.copy(done = !h.done)
        _state.update { st -> st.copy(homework = st.homework.map { if (it.id == h.id) upd else it }) }
        viewModelScope.launch(Dispatchers.IO) { repo.upsertHomework(upd) }
    }

    fun deleteHomework(id: String) {
        _state.update { st -> st.copy(homework = st.homework.filter { it.id != id }, hwDraft = null) }
        viewModelScope.launch(Dispatchers.IO) { repo.deleteHomework(id) }
    }

    fun updatePrefs(change: (Prefs) -> Prefs) {
        val p = change(_state.value.prefs)
        _state.update { it.copy(prefs = p) }
        viewModelScope.launch(Dispatchers.IO) {
            repo.savePrefs(p)
            AppSync.afterDataChange(app)
        }
    }

    fun rawFile() = repo.rawFile()

    // ------------------------------------------------------------ преподаватели

    private var teacherJob: Job? = null

    private fun updTeachers(f: (TeachersState) -> TeachersState) = _state.update { it.copy(teachers = f(it.teachers)) }

    fun loadTeachers() {
        val s = _state.value.settings ?: return
        viewModelScope.launch {
            val mine = withContext(Dispatchers.IO) { repo.myTeachers(s.groupId) }
            updTeachers { it.copy(mine = mine) }
        }
        val t = _state.value.teachers
        if (t.all != null || t.loadingList || t.listTried) return
        updTeachers { it.copy(loadingList = true) }
        viewModelScope.launch {
            val all = try {
                withContext(Dispatchers.IO) { repo.allTeachers() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // без полного списка ищем среди преподавателей своей группы
            }
            updTeachers { it.copy(all = all, loadingList = false, listTried = true) }
        }
    }

    fun setTeacherQuery(q: String) = updTeachers { it.copy(query = q) }

    fun openTeacher(t: Teacher) {
        updTeachers { it.copy(selected = t, week = null, error = null, monday = Repository.mondayOf(LocalDate.now())) }
        loadTeacherWeek()
    }

    /** Из карточки пары: сразу расписание этого преподавателя. */
    fun openTeacherFromLesson(t: Teacher) {
        _state.update { it.copy(detail = null, tab = TAB_TEACHERS) }
        loadTeachers()
        openTeacher(t)
    }

    fun closeTeacher() {
        teacherJob?.cancel()
        updTeachers { it.copy(selected = null, week = null, loadingWeek = false, error = null) }
    }

    fun teacherShiftWeek(weeks: Long) {
        updTeachers { it.copy(monday = it.monday.plusWeeks(weeks), week = null) }
        loadTeacherWeek()
    }

    fun teacherThisWeek() {
        updTeachers { it.copy(monday = Repository.mondayOf(LocalDate.now()), week = null) }
        loadTeacherWeek()
    }

    fun loadTeacherWeek() {
        val s = _state.value.settings ?: return
        val t = _state.value.teachers.selected ?: return
        val monday = _state.value.teachers.monday
        teacherJob?.cancel()
        teacherJob = viewModelScope.launch {
            updTeachers { it.copy(loadingWeek = true, error = null) }
            try {
                val w = withContext(Dispatchers.IO) { repo.teacherWeek(t, monday, s) }
                updTeachers {
                    if (it.selected?.id != t.id || it.monday != monday) it.copy(loadingWeek = false)
                    else it.copy(week = w, loadingWeek = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CaptchaRequiredException) {
                updTeachers { it.copy(loadingWeek = false) }
                handleError(e) { loadTeacherWeek() }
            } catch (e: Exception) {
                updTeachers { it.copy(loadingWeek = false, error = "Не удалось загрузить расписание: ${e.message ?: "нет связи"}") }
            }
        }
    }

    // ------------------------------------------------------------ обновления приложения

    private suspend fun checkUpdates(manual: Boolean) {
        if (_state.value.update.checking) return
        _state.update { it.copy(update = it.update.copy(checking = true, message = null)) }
        try {
            val info = Updater.check(app)
            _state.update {
                it.copy(
                    update = it.update.copy(
                        info = info,
                        checking = false,
                        message = if (manual && info == null) "Установлена последняя версия" else null,
                        showDialog = manual && info != null,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    update = it.update.copy(
                        checking = false,
                        message = if (manual) "Не удалось проверить: нет связи с GitHub" else null,
                    ),
                )
            }
        }
    }

    fun checkUpdatesNow() {
        viewModelScope.launch { checkUpdates(manual = true) }
    }

    fun openUpdateDialog() = _state.update { it.copy(update = it.update.copy(showDialog = true, message = null)) }

    fun closeUpdateDialog() = _state.update {
        it.copy(update = it.update.copy(showDialog = it.update.downloading, needPermission = false))
    }

    fun hideUpdateBanner() = _state.update { it.copy(update = it.update.copy(bannerHidden = true)) }

    fun allowInstalls(): android.content.Intent = Updater.installPermissionIntent(app)

    fun startUpdate() {
        val info = _state.value.update.info ?: return
        if (_state.value.update.downloading) return
        if (!Updater.canInstall(app)) {
            _state.update { it.copy(update = it.update.copy(needPermission = true, showDialog = true)) }
            return
        }
        _state.update {
            it.copy(update = it.update.copy(downloading = true, progress = 0f, message = null, showDialog = true))
        }
        viewModelScope.launch {
            try {
                val apk = Updater.download(app, info) { p ->
                    _state.update { it.copy(update = it.update.copy(progress = p)) }
                }
                withContext(Dispatchers.IO) { Updater.install(app, apk) }
                Notifier.clearUpdate(app)
                _state.update {
                    it.copy(update = it.update.copy(downloading = false, message = "Подтвердите установку в окне Android"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _state.update { it.copy(update = it.update.copy(downloading = false, message = e.message)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            downloading = false,
                            message = "Не удалось скачать обновление: ${e.message ?: "нет связи"}",
                        ),
                    )
                }
            }
        }
    }

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
        const val TAB_SUBJECTS = 1
        const val TAB_TEACHERS = 2
        const val TAB_MAP = 3
        const val TAB_SETTINGS = 4
        private const val STALE_MS = 30 * 60 * 1000L // 30 минут
    }
}
