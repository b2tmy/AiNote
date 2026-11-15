package com.ainote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ReminderNotifications {

    const val CHANNEL_REMINDER = "reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 提醒通知：带「稍后 10 分钟」「完成」两个动作，不用打开 App 就能处理 */
    fun show(context: Context, reminderId: Long, title: String, body: String) {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent().setClassName(context.packageName, "${context.packageName}.MainActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_watcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.reminder_action_snooze),
                actionIntent(context, reminderId, ReminderReceiver.ACTION_SNOOZE)
            )
            .addAction(
                0,
                context.getString(R.string.reminder_action_done),
                actionIntent(context, reminderId, ReminderReceiver.ACTION_DONE)
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(reminderId), notification)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
        }
    }

    private fun actionIntent(context: Context, reminderId: Long, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        // 动作用不同的 requestCode，避免和触发闹钟的 PendingIntent 撞车
        val requestCode = when (action) {
            ReminderReceiver.ACTION_SNOOZE -> reminderId.toInt() + SNOOZE_OFFSET
            else -> reminderId.toInt() + DONE_OFFSET
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(reminderId: Long) = (reminderId % 100_000).toInt() + NOTIFICATION_BASE

    private const val NOTIFICATION_BASE = 2000
    private const val SNOOZE_OFFSET = 400_000
    private const val DONE_OFFSET = 800_000
}
