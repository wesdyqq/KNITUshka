package com.qq.knitushka

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qq.knitushka.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

data class ScheduleUiState(
    val starting: Boolean = true,
    val group: StudyGroup? = null,
    val choosing: Boolean = false,
    val institutes: List<Institute> = emptyList(),
    val institute: Institute? = null,
    val groups: List<StudyGroup> = emptyList(),
    val date: LocalDate = today(),
    val cached: CachedWeek? = null,
    val previewWeeks: Map<LocalDate, CachedWeek> = emptyMap(),
    val loading: Boolean = false,
    val pickerLoading: Boolean = false,
    val error: String? = null,
    val pickerError: String? = null,
    val theme: String = "system",
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as KnituApplication).repository
    private val mutable = MutableStateFlow(ScheduleUiState())
    val state = mutable.asStateFlow()
    private var weekJob: Job? = null
    private var pickerJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val group = repo.selection()
                val theme = repo.theme()
                mutable.update { it.copy(starting = false, group = group, theme = theme, choosing = group == null) }
                if (group == null) loadInstitutes() else loadWeek()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(starting = false, choosing = true, pickerError = message(e)) }
            }
        }
    }

    fun choose() {
        mutable.update { it.copy(choosing = true, pickerError = null) }
        if (state.value.institutes.isEmpty()) loadInstitutes()
    }

    fun cancelChoice() {
        if (state.value.group == null) return
        pickerJob?.cancel()
        mutable.update { it.copy(choosing = false, pickerLoading = false, pickerError = null) }
    }

    fun loadInstitutes() {
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            mutable.update { it.copy(pickerLoading = true, pickerError = null) }
            try {
                val institutes = repo.institutes()
                mutable.update { it.copy(institutes = institutes, pickerLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(pickerLoading = false, pickerError = message(e)) }
            }
        }
    }

    fun chooseInstitute(institute: Institute?) {
        pickerJob?.cancel()
        mutable.update { it.copy(institute = institute, groups = emptyList(), pickerError = null, pickerLoading = institute != null) }
        if (institute == null) return
        pickerJob = viewModelScope.launch {
            try {
                val groups = repo.groups(institute)
                mutable.update { it.copy(groups = groups, pickerLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(pickerLoading = false, pickerError = message(e)) }
            }
        }
    }

    fun chooseGroup(group: StudyGroup) {
        if (state.value.pickerLoading) return
        pickerJob = viewModelScope.launch {
            mutable.update { it.copy(pickerLoading = true, pickerError = null) }
            try {
                val date = today()
                val cached = repo.cached(group, date) ?: repo.refresh(group, date)
                // Persist only after a usable timetable exists. The old selection remains on failure.
                repo.select(group)
                weekJob?.cancel()
                mutable.update { it.copy(group = group, choosing = false, date = date, cached = cached,
                    previewWeeks = mapOf(date.monday() to cached),
                    pickerLoading = false, loading = false, error = null) }
                loadWeek()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(pickerLoading = false, pickerError = message(e)) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date == state.value.date) return
        val sameWeek = date.monday() == state.value.date.monday()
        if (!sameWeek) weekJob?.cancel()
        mutable.update { it.copy(date = date, cached = if (sameWeek) it.cached else null,
            loading = if (sameWeek) it.loading else false, error = if (sameWeek) it.error else null) }
        if (!sameWeek) loadWeek()
    }

    fun loadWeek(force: Boolean = false) {
        val group = state.value.group ?: return
        if (state.value.loading && !force) return
        prefetchJob?.cancel()
        weekJob?.cancel()
        val date = state.value.date
        weekJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val saved = repo.cached(group, date)
                val previews = listOf(date.minusWeeks(1), date, date.plusWeeks(1)).mapNotNull { day ->
                    repo.cached(group, day)?.let { day.monday() to it }
                }.toMap()
                mutable.update { it.copy(cached = saved, previewWeeks = it.previewWeeks + previews) }
                val updated = repo.refresh(group, date, force)
                mutable.update { it.copy(cached = updated, loading = false,
                    previewWeeks = it.previewWeeks + (date.monday() to updated)) }
                prefetchJob?.cancel()
                prefetchJob = viewModelScope.launch {
                    try {
                        repo.refreshWindow(group)
                        val prefetched = (-1L..2L).mapNotNull { offset ->
                            val day = today().plusWeeks(offset)
                            repo.cached(group, day)?.let { day.monday() to it }
                        }.toMap()
                        mutable.update { if (it.group == group) it.copy(previewWeeks = it.previewWeeks + prefetched) else it }
                    }
                    catch (e: Exception) { if (e is CancellationException) throw e }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(loading = false, error = message(e)) }
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            repo.setTheme(theme)
            mutable.update { it.copy(theme = theme) }
        }
    }

    private fun message(error: Exception) = when (error) {
        is SourceFormatException -> "Не удалось прочитать расписание на сайте КНИТУ. Сохранённые данные не изменены. Попробуйте обновить позже или откройте источник."
        is IOException -> "Не удалось связаться с сайтом КНИТУ. Проверьте интернет и повторите загрузку. Сохранённое расписание доступно без сети."
        else -> "Не удалось загрузить данные. Повторите попытку."
    }
}
