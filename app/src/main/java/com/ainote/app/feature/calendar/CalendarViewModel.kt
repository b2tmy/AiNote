package com.ainote.app.feature.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.common.TimeFormat
import com.ainote.core.data.repository.CalendarRepository
import com.ainote.core.database.entity.CalendarEventEntity
import com.ainote.core.database.entity.ReminderRefType
import com.ainote.service.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CalendarRepository
) : ViewModel() {

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** 当前月份的所有事件，用于在月网格上打点 */
    val monthEvents: StateFlow<List<CalendarEventEntity>> = _visibleMonth
        .flatMapLatest { month ->
            repository.observeInRange(
                TimeFormat.startOfDay(month.atDay(1)),
                TimeFormat.endOfDay(month.atEndOfMonth())
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选中那一天的事件列表 */
    val dayEvents: StateFlow<List<CalendarEventEntity>> = _selectedDate
        .flatMapLatest { date ->
            repository.observeInRange(
                TimeFormat.startOfDay(date),
                TimeFormat.endOfDay(date)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousMonth() {
        _visibleMonth.value = _visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _visibleMonth.value = _visibleMonth.value.plusMonths(1)
    }

    fun goToToday() {
        val today = LocalDate.now()
        _visibleMonth.value = YearMonth.from(today)
        _selectedDate.value = today
    }

    fun createEvent(
        title: String,
        date: LocalDate,
        hour: Int,
        minute: Int,
        durationMinutes: Long,
        allDay: Boolean,
        reminderOffsetMs: Long?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val startAt = if (allDay) {
                TimeFormat.startOfDay(date)
            } else {
                TimeFormat.toEpochMillis(date, hour, minute)
            }
            val endAt = if (allDay) {
                TimeFormat.endOfDay(date)
            } else {
                startAt + durationMinutes * 60_000L
            }

            val eventId = repository.createEvent(
                title = title,
                startAt = startAt,
                endAt = endAt,
                allDay = allDay,
                reminderOffsetsMs = reminderOffsetMs?.let { listOf(it) } ?: emptyList()
            )

            // 落库之后立刻把闹钟排上，否则要等到下次开机/改时间才会被调度
            if (reminderOffsetMs != null) {
                repository.remindersOf(ReminderRefType.EVENT, eventId)
                    .forEach { ReminderScheduler.schedule(context, it) }
            }
        }
    }

    fun deleteEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.remindersOf(ReminderRefType.EVENT, event.id)
                .forEach { ReminderScheduler.cancel(context, it.id) }
            repository.deleteEvent(event)
        }
    }

    companion object {
        /** 常用提前量，UI 上做成一组 chip */
        val REMINDER_OPTIONS: List<Pair<String, Long?>> = listOf(
            "不提醒" to null,
            "准时" to 0L,
            "5 分钟前" to 5 * 60_000L,
            "15 分钟前" to 15 * 60_000L,
            "1 小时前" to 60 * 60_000L
        )

        val DURATION_OPTIONS: List<Pair<String, Long>> = listOf(
            "30 分钟" to 30L,
            "1 小时" to 60L,
            "2 小时" to 120L,
            "全天" to 0L
        )
    }
}
