package com.ainote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object DailyReportNotifications {

    const val CHANNEL_DAILY = "daily_report"
    private const val NOTIFICATION_ID = 3001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.daily_report_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.daily_report_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, date: String, statsSummary: String) {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent().setClassName(context.packageName, "${context.packageName}.MainActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_watcher)
            .setContentTitle(context.getString(R.string.daily_report_title))
            .setContentText(statsSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$date\n$statsSummary"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
