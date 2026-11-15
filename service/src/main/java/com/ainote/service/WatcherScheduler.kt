package com.ainote.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ainote.core.ai.DailyReportGenerator
import com.ainote.core.ai.ScreenshotAnalyzer
import com.ainote.core.data.repository.CalendarRepository
import com.ainote.core.data.repository.MemoRepository
import com.ainote.core.data.repository.ScreenshotRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * 截图监听管道的统一开关。
 *
 * 兜底 Worker 与前台服务是「互补」关系，不是二选一：
 * 前台服务负责实时，Worker 负责在服务被 ROM 干掉后补偿。
 */
object WatcherScheduler {

    private const val FALLBACK_WORK_NAME = "screenshot_fallback_scan"

    fun startWatcher(context: Context) {
        if (!hasReadPermission(context)) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenshotWatcherService::class.java)
            )
        }
        scheduleFallback(context)
    }

    fun stopWatcher(context: Context) {
        runCatching {
            context.stopService(Intent(context, ScreenshotWatcherService::class.java))
        }
        WorkManager.getInstance(context).cancelUniqueWork(FALLBACK_WORK_NAME)
    }

    /** 请求立即扫一次（磁贴 / 手动刷新用） */
    fun requestScanNow(context: Context) {
        if (!hasReadPermission(context)) return
        val intent = Intent(context, ScreenshotWatcherService::class.java).apply {
            action = ScreenshotWatcherService.ACTION_SCAN_NOW
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun scheduleFallback(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScreenshotFallbackWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FALLBACK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** 读取相册权限：API 33+ 用 READ_MEDIA_IMAGES，之前用 READ_EXTERNAL_STORAGE */
    fun readImagesPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, readImagesPermission()) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 是否已在电池优化白名单里。
     * 不在白名单时，国产 ROM 往往会周期性掐掉前台服务 —— 这是监听掉线最常见的原因。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(true)
    }

    /** 跳系统的「电池优化」授权页 */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}

/**
 * Worker / BroadcastReceiver 这类非 Hilt 管理组件拿依赖的入口。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun ingestor(): ScreenshotIngestor
    fun preferences(): WatcherPreferences
    fun screenshotAnalyzer(): ScreenshotAnalyzer
    fun screenshotRepository(): ScreenshotRepository
    fun calendarRepository(): CalendarRepository
    fun memoRepository(): MemoRepository
    fun dailyReportGenerator(): DailyReportGenerator
}
