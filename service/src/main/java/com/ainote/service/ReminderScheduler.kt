package com.ainote.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ainote.core.database.entity.ReminderEntity
import dagger.hilt.android.EntryPointAccessors

/**
 * 提醒的 AlarmManager 调度。
 *
 * 精确闹钟在 Android 12+ 需要用户在系统设置里单独授权（SCHEDULE_EXACT_ALARM）；
 * 拿不到权限时退化为 setWindow —— 允许系统延迟，但不至于彻底不响。
 */
object ReminderScheduler {

    fun schedule(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, reminder.id)
        val triggerAt = reminder.triggerAt

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    FALLBACK_WINDOW_MS,
                    pendingIntent
                )
            }
        }.onFailure {
            Log.w(TAG, "调度提醒失败 id=${reminder.id}", it)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(buildPendingIntent(context, reminderId)) }
    }

    /**
     * 开机、时区变化、时间被改之后重排所有未触发的提醒。
     *
     * 对已经过期的提醒：超过 [STALE_THRESHOLD_MS] 的直接作废 ——
     * 否则关一晚上机，早上会一口气炸出十几个陈旧通知。
     */
    suspend fun rescheduleAll(context: Context) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ServiceEntryPoint::class.java
        )
        val repository = entry.calendarRepository()
        val now = System.currentTimeMillis()

        repository.allPendingReminders().forEach { reminder ->
            val overdueBy = now - reminder.triggerAt
            when {
                overdueBy > STALE_THRESHOLD_MS -> repository.markFired(reminder.id)

                overdueBy > 0 -> schedule(context, reminder.copy(triggerAt = now + 2_000))

                else -> schedule(context, reminder)
            }
        }
    }

    fun buildPendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val TAG = "ReminderScheduler"
    private const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    private const val STALE_THRESHOLD_MS = 60 * 60 * 1000L
}
