package com.ainote.service

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 常驻监听服务：截图一落进 MediaStore 就把它带进管道。
 *
 * 两点说明：
 * 1. 用 ContentObserver 而不是轮询 —— 系统截图时会连续触发多次 onChange，
 *    这里做 400ms 去抖，避免同一张图被反复处理。
 * 2. 部分 ROM（MIUI / HyperOS / ColorOS）对 MediaStore 变更通知有延迟甚至拦截，
 *    所以还有 15 分钟一次的兜底 Worker 补偿遗漏。
 */
@AndroidEntryPoint
class ScreenshotWatcherService : Service() {

    @Inject lateinit var ingestor: ScreenshotIngestor
    @Inject lateinit var preferences: WatcherPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        WatcherNotifications.ensureChannel(this)
        preferences.initializeWatermarkIfNeeded()

        startForeground(
            WatcherNotifications.FOREGROUND_ID,
            WatcherNotifications.buildForeground(this, getString(R.string.watcher_notification_text))
        )

        registerMediaObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SCAN_NOW) {
            Log.i(TAG, "收到手动扫描请求")
        }
        scanNow()
        // 被系统杀掉后尽量自动回来
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        observer = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerMediaObserver() {
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    scanNow()
                }
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
        }.onFailure {
            Log.w(TAG, "注册 MediaStore 观察者失败，将只依赖兜底 Worker", it)
        }
        observer = contentObserver
    }

    private fun scanNow() {
        scope.launch {
            runCatching { ingestor.scanAndIngest() }
                .onSuccess { result ->
                    if (result.inserted > 0) {
                        Log.i(TAG, "新增截图记录 ${result.inserted} 条（扫描 ${result.scanned}）")
                    }
                }
                .onFailure { Log.w(TAG, "扫描截图失败", it) }
        }
    }

    companion object {
        const val ACTION_SCAN_NOW = "com.ainote.service.action.SCAN_NOW"
        private const val TAG = "ScreenshotWatcher"
        private const val DEBOUNCE_MS = 400L
    }
}
