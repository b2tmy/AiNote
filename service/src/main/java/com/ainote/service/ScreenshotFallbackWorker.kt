package com.ainote.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * 兜底扫描：部分 ROM 会拦截或延迟 MediaStore 的变更通知，
 * 也可能把前台服务直接干掉，所以每 15 分钟补扫一次。
 *
 * 永远返回 success —— 单次失败没有重试价值，反正 15 分钟后还会再来，
 * 而 retry 会让 WorkManager 进入退避队列、反而拖慢整体节奏。
 */
class ScreenshotFallbackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )

        val preferences = entry.preferences()
        preferences.initializeWatermarkIfNeeded()
        if (!preferences.watcherEnabled) return Result.success()

        runCatching { entry.ingestor().scanAndIngest() }
            .onSuccess { result ->
                if (result.inserted > 0) {
                    Log.i(TAG, "兜底扫描补入 ${result.inserted} 条截图记录")
                }
            }
            .onFailure { Log.w(TAG, "兜底扫描失败", it) }

        return Result.success()
    }

    private companion object {
        const val TAG = "ScreenshotFallback"
    }
}
