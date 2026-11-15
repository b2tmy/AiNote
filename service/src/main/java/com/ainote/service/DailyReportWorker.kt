package com.ainote.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * 每日汇报的定时任务。
 *
 * 没有内容的一天不产生任何输出，也不推通知 —— 汇报的前提是「今天真的发生了什么」。
 */
class DailyReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )

        return entry.dailyReportGenerator().generate().fold(
            onSuccess = { result ->
                val stats = result.stats
                val summary = buildString {
                    append("截图 ${stats.screenshots} · ")
                    append("笔记 +${stats.notesCreated} · ")
                    append("待办 +${stats.todosCreated}")
                    if (stats.todosDone > 0) append(" / 完成 ${stats.todosDone}")
                    if (!result.usedAi) append("（未使用 AI，纯统计）")
                }
                DailyReportNotifications.show(applicationContext, stats.date, summary)
                Log.i(TAG, "日报已生成：${stats.date}，笔记 id=${result.noteId}")
                Result.success()
            },
            onFailure = { error ->
                // 内容为空属于正常情况（今天什么都没干），不重试
                Log.i(TAG, "本次日报未生成：${error.message}")
                Result.success()
            }
        )
    }

    private companion object {
        const val TAG = "DailyReport"
    }
}
