package com.ainote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新 / 系统时间变化后，把后台管道和提醒重新拉起来。
 *
 * 提醒是「内存里没有状态」的设计，所以这里只需要无脑重排一遍；
 * AlarmManager 里的闹钟会随重启丢失，不重排就会静默不响。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED ||
                    action == Intent.ACTION_MY_PACKAGE_REPLACED
                ) {
                    restoreWatcher(context.applicationContext)
                }
                // 开机、改时间、换时区都重排提醒
                ReminderScheduler.rescheduleAll(context.applicationContext)
            } catch (t: Throwable) {
                Log.w(TAG, "启动后恢复失败 action=$action", t)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun restoreWatcher(context: Context) {
        runCatching {
            val entry = EntryPointAccessors.fromApplication(
                context,
                ServiceEntryPoint::class.java
            )
            if (entry.preferences().watcherEnabled && WatcherScheduler.hasReadPermission(context)) {
                WatcherScheduler.startWatcher(context)
                Log.i(TAG, "开机后已恢复截图监听")
            } else {
                WatcherScheduler.scheduleFallback(context)
            }
        }.onFailure { Log.w(TAG, "恢复截图监听失败", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
