package com.ainote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ainote.core.database.entity.ReminderRefType
import com.ainote.core.database.entity.ReminderRepeat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 提醒触发与通知动作的落地点。
 *
 * 三种 action：
 * - FIRE（AlarmManager 触发）：弹通知；重复提醒顺延到下一次
 * - SNOOZE：10 分钟后再响
 * - DONE：标记已处理，撤掉通知
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        val action = intent.action ?: ACTION_FIRE
        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                handle(context.applicationContext, reminderId, action)
            } catch (t: Throwable) {
                Log.w(TAG, "处理提醒失败 id=$reminderId action=$action", t)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun handle(context: Context, reminderId: Long, action: String) {
        val entry = EntryPointAccessors.fromApplication(context, ServiceEntryPoint::class.java)
        val calendar = entry.calendarRepository()
        val reminder = calendar.reminderById(reminderId) ?: return

        when (action) {
            ACTION_SNOOZE -> {
                val next = System.currentTimeMillis() + SNOOZE_MS
                calendar.snoozeReminder(reminderId, next)
                ReminderNotifications.cancel(context, reminderId)
                ReminderScheduler.schedule(context, reminder.copy(triggerAt = next))
            }

            ACTION_DONE -> {
                calendar.markFired(reminderId)
                ReminderNotifications.cancel(context, reminderId)
            }

            else -> {
                val title = resolveTitle(entry, reminder.refType, reminder.refId)
                val body = when (reminder.refType) {
                    ReminderRefType.EVENT -> "日程提醒"
                    ReminderRefType.MEMO -> "待办提醒"
                    ReminderRefType.NOTE -> "笔记提醒"
                }

                if (reminder.repeatRule == ReminderRepeat.NONE) {
                    calendar.markFired(reminderId)
                } else {
                    val next = nextOccurrence(reminder.triggerAt, reminder.repeatRule)
                    calendar.reschedule(reminderId, next)
                    ReminderScheduler.schedule(context, reminder.copy(triggerAt = next))
                }

                ReminderNotifications.show(context, reminderId, title, body)
            }
        }
    }

    private suspend fun resolveTitle(
        entry: ServiceEntryPoint,
        type: ReminderRefType,
        refId: Long
    ): String = when (type) {
        ReminderRefType.EVENT -> entry.calendarRepository().getById(refId)?.title ?: "日程"
        ReminderRefType.MEMO -> entry.memoRepository().getById(refId)?.content?.take(40) ?: "待办"
        ReminderRefType.NOTE -> "笔记提醒"
    }

    private fun nextOccurrence(current: Long, repeat: ReminderRepeat): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = current }
        when (repeat) {
            ReminderRepeat.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            ReminderRepeat.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            ReminderRepeat.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            ReminderRepeat.YEARLY -> calendar.add(Calendar.YEAR, 1)
            ReminderRepeat.NONE -> return current
        }
        return calendar.timeInMillis
    }

    companion object {
        const val ACTION_FIRE = "com.ainote.service.action.REMINDER_FIRE"
        const val ACTION_SNOOZE = "com.ainote.service.action.REMINDER_SNOOZE"
        const val ACTION_DONE = "com.ainote.service.action.REMINDER_DONE"
        const val EXTRA_REMINDER_ID = "reminder_id"

        private const val TAG = "ReminderReceiver"
        private const val SNOOZE_MS = 10 * 60 * 1000L
    }
}
