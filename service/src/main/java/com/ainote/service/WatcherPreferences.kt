package com.ainote.service

import android.content.Context
import com.ainote.core.common.image.ImageProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听管道的本地状态。
 * M3 引入 DataStore 后会迁移过去，这里的键位保持不变以便平滑过渡。
 */
@Singleton
class WatcherPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("watcher_state", Context.MODE_PRIVATE)

    /** 已处理到的时间水位线（MediaStore 的 DATE_ADDED，单位秒） */
    var lastSeenSeconds: Long
        get() = prefs.getLong(KEY_LAST_SEEN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SEEN, value).apply()

    /** 用户是否开启了截图监听总开关 */
    var watcherEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 首次启动时把水位线设成「此刻」，避免把历史截图一次性全部倒进来 */
    fun initializeWatermarkIfNeeded(nowSeconds: Long = System.currentTimeMillis() / 1000) {
        if (lastSeenSeconds == 0L) {
            lastSeenSeconds = nowSeconds
        }
    }

    var quality: ImageProcessor.Quality
        get() = when (prefs.getString(KEY_QUALITY, "MEDIUM")) {
            "LOW" -> ImageProcessor.Quality.LOW
            "HIGH" -> ImageProcessor.Quality.HIGH
            else -> ImageProcessor.Quality.MEDIUM
        }
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    private companion object {
        const val KEY_LAST_SEEN = "last_seen_seconds"
        const val KEY_ENABLED = "watcher_enabled"
        const val KEY_QUALITY = "image_quality"
    }
}
