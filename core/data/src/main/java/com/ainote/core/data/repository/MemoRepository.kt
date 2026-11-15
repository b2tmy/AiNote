package com.ainote.core.data.repository

import com.ainote.core.database.dao.MemoDao
import com.ainote.core.database.entity.MemoEntity
import com.ainote.core.database.entity.MemoPriority
import com.ainote.core.database.entity.ReminderEntity
import com.ainote.core.database.entity.ReminderRefType
import com.ainote.core.database.entity.ReminderRepeat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoRepository @Inject constructor(
    private val memoDao: MemoDao,
    private val reminderDao: com.ainote.core.database.dao.ReminderDao
) {

    fun observeAll(): Flow<List<MemoEntity>> = memoDao.observeAll()

    fun observeActive(): Flow<List<MemoEntity>> = memoDao.observeActive()

    suspend fun getById(id: Long): MemoEntity? = memoDao.getById(id)

    suspend fun add(
        content: String,
        dueAt: Long? = null,
        priority: MemoPriority = MemoPriority.NORMAL
    ): Long {
        val now = System.currentTimeMillis()
        val id = memoDao.insert(
            MemoEntity(
                content = content.trim(),
                priority = priority,
                dueAt = dueAt,
                createdAt = now,
                updatedAt = now
            )
        )
        // 有截止时间就顺手挂一条提醒，具体调度在 M5 的 ReminderScheduler 里接管
        if (dueAt != null) {
            reminderDao.insert(
                ReminderEntity(
                    refType = ReminderRefType.MEMO,
                    refId = id,
                    triggerAt = dueAt,
                    repeatRule = ReminderRepeat.NONE,
                    createdAt = now
                )
            )
        }
        return id
    }

    suspend fun update(memo: MemoEntity) =
        memoDao.update(memo.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setContent(id: Long, content: String, priority: MemoPriority, dueAt: Long?) {
        val memo = memoDao.getById(id) ?: return
        memoDao.update(
            memo.copy(
                content = content.trim(),
                priority = priority,
                dueAt = dueAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        // 截止时间变化时重建提醒
        reminderDao.deleteByRef(ReminderRefType.MEMO, id)
        if (dueAt != null) {
            reminderDao.insert(
                ReminderEntity(
                    refType = ReminderRefType.MEMO,
                    refId = id,
                    triggerAt = dueAt,
                    repeatRule = ReminderRepeat.NONE,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleDone(id: Long) {
        val memo = memoDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val nextDone = !memo.done
        memoDao.setDone(id, nextDone, if (nextDone) now else null, now)
        if (nextDone) {
            // 完成即撤掉未触发的提醒，避免无意义打扰
            reminderDao.deleteByRef(ReminderRefType.MEMO, id)
        }
    }

    suspend fun delete(memo: MemoEntity) {
        reminderDao.deleteByRef(ReminderRefType.MEMO, memo.id)
        memoDao.delete(memo)
    }

    suspend fun countActive(): Int = memoDao.countActive()

    suspend fun countDoneInRange(from: Long, to: Long): Int = memoDao.countDoneInRange(from, to)

    suspend fun countCreatedInRange(from: Long, to: Long): Int = memoDao.countCreatedInRange(from, to)
}
