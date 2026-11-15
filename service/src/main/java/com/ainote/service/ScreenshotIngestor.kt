package com.ainote.service

import android.content.Context
import android.util.Log
import com.ainote.core.common.DispatcherProvider
import com.ainote.core.common.image.ImageProcessor
import com.ainote.core.database.dao.ScreenshotRecordDao
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.ScreenshotRecordEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 截图入库管道：扫 MediaStore → 去重 → 压缩 → 落库。
 *
 * 只负责把记录变成 PENDING，真正的 AI 分析在 M4 的 Worker 里做，
 * 这样即使没有网络、没配 API Key，截图也不会丢。
 */
@Singleton
class ScreenshotIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordDao: ScreenshotRecordDao,
    private val preferences: WatcherPreferences,
    private val dispatchers: DispatcherProvider
) {

    data class IngestResult(val scanned: Int, val inserted: Int, val duplicated: Int, val failed: Int)

    private val mediaDir: File
        get() = File(context.filesDir, "media/screenshots").apply { mkdirs() }

    suspend fun scanAndIngest(): IngestResult = withContext(dispatchers.io) {
        preferences.initializeWatermarkIfNeeded()

        val since = (preferences.lastSeenSeconds - 5).coerceAtLeast(0)
        val candidates = ScreenshotQuery.querySince(context, since)
        if (candidates.isEmpty()) return@withContext IngestResult(0, 0, 0, 0)

        var inserted = 0
        var duplicated = 0
        var failed = 0
        var watermark = preferences.lastSeenSeconds

        for (candidate in candidates) {
            try {
                val hash = context.contentResolver.openInputStream(candidate.uri)?.use {
                    ImageProcessor.sha256(it)
                }

                if (hash == null) {
                    failed++
                    continue
                }

                if (recordDao.findBySha256(hash) != null) {
                    duplicated++
                    watermark = maxOf(watermark, candidate.dateAddedSeconds)
                    continue
                }

                val quality = preferences.quality
                val target = File(mediaDir, "$hash.webp")
                val result = ImageProcessor.compressToWebp(
                    openInput = { context.contentResolver.openInputStream(candidate.uri)!! },
                    target = target,
                    maxEdge = quality.maxEdge,
                    quality = quality.quality
                )

                if (result == null) {
                    failed++
                    continue
                }

                val capturedAt = candidate.dateAddedSeconds * 1000
                val rowId = recordDao.insert(
                    ScreenshotRecordEntity(
                        mediaUri = candidate.uri.toString(),
                        localPath = result.file.absolutePath,
                        capturedAt = capturedAt,
                        sha256 = hash,
                        status = AnalyzeStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                )

                if (rowId == -1L) {
                    // 并发插入撞了唯一索引，当作重复处理
                    duplicated++
                } else {
                    inserted++
                }
                watermark = maxOf(watermark, candidate.dateAddedSeconds)
            } catch (t: Throwable) {
                Log.w(TAG, "处理截图失败: ${candidate.displayName}", t)
                failed++
            }
        }

        preferences.lastSeenSeconds = watermark

        // 有新截图就顺手把分析任务排上；没网 / 没配 Key 时任务会在队列里等着
        if (inserted > 0) {
            AnalyzeScheduler.enqueue(context)
        }

        IngestResult(scanned = candidates.size, inserted = inserted, duplicated = duplicated, failed = failed)
    }

    /** 单张图片入库（分享入口用） */
    suspend fun ingestSingle(uri: android.net.Uri): Long? = withContext(dispatchers.io) {
        runCatching {
            val hash = context.contentResolver.openInputStream(uri)?.use {
                ImageProcessor.sha256(it)
            } ?: return@runCatching null

            val existing = recordDao.findBySha256(hash)
            if (existing != null) return@runCatching null

            val quality = preferences.quality
            val target = File(mediaDir, "$hash.webp")
            val result = ImageProcessor.compressToWebp(
                openInput = { context.contentResolver.openInputStream(uri)!! },
                target = target,
                maxEdge = quality.maxEdge,
                quality = quality.quality
            ) ?: return@runCatching null

            recordDao.insert(
                ScreenshotRecordEntity(
                    mediaUri = uri.toString(),
                    localPath = result.file.absolutePath,
                    capturedAt = System.currentTimeMillis(),
                    sha256 = hash,
                    status = AnalyzeStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )
            )
        }.getOrNull()?.takeIf { it > 0 }
    }

    private companion object {
        const val TAG = "ScreenshotIngestor"
    }
}
