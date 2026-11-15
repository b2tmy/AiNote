package com.ainote.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 日报调度：默认每天 21:30 生成。
 *
 * 用 PeriodicWorkRequest + 初始延迟到下一个目标时刻，
 * 之后由 WorkManager 每 24 小时滚动 —— 允许系统做小幅弹性调整，
 * 对「每晚回顾」这种场景够用，也不至于像精确闹钟那样受权限限制。
 */
object DailyReportScheduler {

    private const val WORK_NAME = "daily_report"

    const val DEFAULT_HOUR = 21
    const val DEFAULT_MINUTE = 30

    fun schedule(context: Context, hour: Int = DEFAULT_HOUR, minute: Int = DEFAULT_MINUTE) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)

        val delayMinutes = Duration.between(now, target).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // UPDATE：用户改了时间就按新时间走，不用先取消
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** 立即生成一次（设置页/日报页的「现在生成」） */
    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily_report_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyReportWorker>().build()
        )
    }

    /** 下一次触发的时间点，用于界面提示 */
    fun nextRunAt(hour: Int = DEFAULT_HOUR, minute: Int = DEFAULT_MINUTE): LocalDateTime {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target
    }
}
