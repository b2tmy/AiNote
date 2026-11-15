package com.ainote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object WatcherNotifications {

    const val CHANNEL_WATCHER = "watcher_service"
    const val FOREGROUND_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_WATCHER,
                context.getString(R.string.watcher_channel_name),
                // 常驻通知不该打断用户，压到最低优先级
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.watcher_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForeground(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCHER)
            .setSmallIcon(R.drawable.ic_watcher)
            .setContentTitle(context.getString(R.string.watcher_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
}
