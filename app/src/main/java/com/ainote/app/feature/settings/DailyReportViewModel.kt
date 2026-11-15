package com.ainote.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.ai.DailyReportGenerator
import com.ainote.core.database.dao.DailySummaryDao
import com.ainote.core.database.entity.DailySummaryEntity
import com.ainote.service.DailyReportScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val summaryDao: DailySummaryDao,
    private val generator: DailyReportGenerator
) : ViewModel() {

    val latest: StateFlow<DailySummaryEntity?> = summaryDao.observeRecent(1)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 下次自动生成的时间，界面上给个明确预期 */
    val nextRunText: String = DailyReportScheduler.nextRunAt()
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    fun generateNow() {
        if (_generating.value) return
        viewModelScope.launch {
            _generating.value = true
            _message.value = null
            generator.generate().fold(
                onSuccess = { result ->
                    _message.value = if (result.usedAi) {
                        "已生成 ${result.stats.date} 的回顾"
                    } else {
                        "已生成 ${result.stats.date} 的统计（未使用 AI）"
                    }
                },
                onFailure = { error -> _message.value = error.message ?: "生成失败" }
            )
            _generating.value = false
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun formatDate(summary: DailySummaryEntity): String {
        return runCatching {
            LocalDateTime.parse(summary.summaryDate + "T00:00")
                .format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))
        }.getOrDefault(summary.summaryDate)
    }
}
