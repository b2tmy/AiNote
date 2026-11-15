package com.ainote.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * 待分析队列的消费者：把 PENDING 的截图逐条送进分析链路。
 *
 * 单次只吃 [MAX_PER_RUN] 条，避免一次跑太久被系统掐掉；
 * 剩下的留到下一次触发（截图入库、手动重试、打开 App 都会入队）。
 *
 * 网络检查放在这里而不是 WorkManager 约束里 —— 见 AnalyzeScheduler 的注释。
 */
class AnalyzeScreenshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!hasInternet()) {
            Log.i(TAG, "当前没有网络，稍后重试")
            return Result.retry()
        }

        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )

        val repository = entry.screenshotRepository()
        val analyzer = entry.screenshotAnalyzer()

        val pending = repository.pending()
        if (pending.isEmpty()) return Result.success()

        var succeeded = 0
        var failed = 0

        for (record in pending.take(MAX_PER_RUN)) {
            analyzer.analyze(record.id)
                .onSuccess { succeeded++ }
                .onFailure { failed++ }
        }

        Log.i(TAG, "本轮分析完成：成功 $succeeded，失败 $failed，剩余 ${pending.size - succeeded - failed}")
        return Result.success()
    }

    private fun hasInternet(): Boolean {
        val manager = applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true

        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "AnalyzeScreenshot"
        const val MAX_PER_RUN = 5
    }
}
