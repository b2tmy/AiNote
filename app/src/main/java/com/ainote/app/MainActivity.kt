package com.ainote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ainote.app.navigation.AiNoteApp
import com.ainote.core.designsystem.theme.AiNoteTheme
import com.ainote.service.AnalyzeScheduler
import com.ainote.service.DailyReportScheduler
import com.ainote.service.ReminderScheduler
import com.ainote.service.WatcherPreferences
import com.ainote.service.WatcherScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var watcherPreferences: WatcherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ensureWatcherRunning()

        // 补跑上次没网 / 没配 Key 时残留下来的待分析队列
        AnalyzeScheduler.enqueue(this)

        // AlarmManager 里的闹钟会随 force-stop、重启、改时间丢失。
        // 库里的未触发提醒才是唯一真相，每次打开 App 重排一遍最稳。
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }

        // 每日汇报：默认 21:30。重复排定是幂等的（UPDATE 策略）
        DailyReportScheduler.schedule(this)

        setContent {
            AiNoteTheme {
                AiNoteApp()
            }
        }
    }

    /**
     * 在前台启动监听服务。
     *
     * 放在 Activity 里而不是 Application 里，是因为 Android 12+ 禁止 App 从后台拉起前台服务；
     * 借用户打开 App 这个前台时机启动才是合规且可靠的。
     */
    private fun ensureWatcherRunning() {
        if (watcherPreferences.watcherEnabled && WatcherScheduler.hasReadPermission(this)) {
            WatcherScheduler.startWatcher(this)
        } else {
            // 即使没有权限，也把兜底任务排上，免得用户以为监听在跑
            WatcherScheduler.scheduleFallback(this)
        }
    }
}
