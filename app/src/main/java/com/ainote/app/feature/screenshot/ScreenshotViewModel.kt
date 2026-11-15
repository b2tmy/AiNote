package com.ainote.app.feature.screenshot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.data.repository.ScreenshotRepository
import com.ainote.core.database.entity.ScreenshotRecordEntity
import com.ainote.service.WatcherPreferences
import com.ainote.service.WatcherScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScreenshotRepository,
    private val preferences: WatcherPreferences
) : ViewModel() {

    val records: StateFlow<List<ScreenshotRecordEntity>> = repository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _hasPermission = MutableStateFlow(WatcherScheduler.hasReadPermission(context))
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _ignoringBatteryOptimization =
        MutableStateFlow(WatcherScheduler.isIgnoringBatteryOptimizations(context))
    val ignoringBatteryOptimization: StateFlow<Boolean> = _ignoringBatteryOptimization.asStateFlow()

    private val _watching = MutableStateFlow(preferences.watcherEnabled)
    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    /** 从系统设置页回来时刷新一遍外部状态 */
    fun refreshSystemStates() {
        _hasPermission.value = WatcherScheduler.hasReadPermission(context)
        _ignoringBatteryOptimization.value = WatcherScheduler.isIgnoringBatteryOptimizations(context)
    }

    fun onPermissionGranted() {
        refreshSystemStates()
        startWatching()
    }

    fun startWatching() {
        preferences.watcherEnabled = true
        WatcherScheduler.startWatcher(context)
        _watching.value = true
        scanNow()
    }

    fun stopWatching() {
        preferences.watcherEnabled = false
        WatcherScheduler.stopWatcher(context)
        _watching.value = false
    }

    fun scanNow() {
        WatcherScheduler.requestScanNow(context)
    }

    fun batteryOptimizationIntent() = WatcherScheduler.batteryOptimizationIntent(context)

    fun retry(record: ScreenshotRecordEntity) {
        viewModelScope.launch { repository.retry(record.id) }
    }

    fun delete(record: ScreenshotRecordEntity) {
        viewModelScope.launch { repository.delete(record.id) }
    }
}
