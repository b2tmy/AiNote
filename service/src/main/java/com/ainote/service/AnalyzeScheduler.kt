package com.ainote.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 截图分析任务的统一入队口。
 *
 * 触发时机：
 * - 新截图入库后（ScreenshotIngestor）
 * - 用户在列表/详情页点「重新分析」
 * - 打开 App 时补跑（上次没网、没配 Key 时残留的 PENDING）
 *
 * KEEP 策略足够：Worker 每次都会把当前所有 PENDING 捞一遍，
 * 重复入队没有意义，而队列里已有任务时新任务会被合并。
 */
object AnalyzeScheduler {

    private const val WORK_NAME = "analyze_screenshots"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AnalyzeScreenshotWorker>()
            // 刻意不挂 NetworkType 约束：部分设备与模拟器的网络能力判定会让任务一直停留在
            // 「约束不满足」而不执行（实测模拟器上 onBlockedStatusChanged 直接报了 false）。
            // 改由 Worker 自己检查网络，无网时 retry，行为更可控。
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // 必须用 REPLACE：KEEP 会复用队列里已有的同名任务（连同它当初的约束一起），
            // 实测在模拟器上旧任务的网络约束判定为不满足，导致新请求永远排在它后面不执行。
            // Worker 本身是幂等的（每次重扫全部 PENDING），替换掉旧任务没有副作用。
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
