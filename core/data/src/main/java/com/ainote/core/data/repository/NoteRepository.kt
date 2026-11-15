package com.ainote.core.data.repository

import com.ainote.core.database.dao.AttachmentDao
import com.ainote.core.database.dao.NoteDao
import com.ainote.core.database.entity.AttachmentEntity
import com.ainote.core.database.entity.NoteEntity
import com.ainote.core.database.entity.NoteSourceType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao
) {

    fun observeByNotebook(notebookId: Long): Flow<List<NoteEntity>> =
        noteDao.observeByNotebook(notebookId)

    fun observeRecent(): Flow<List<NoteEntity>> = noteDao.observeRecent()

    fun observeById(id: Long): Flow<NoteEntity?> = noteDao.observeById(id)

    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)

    suspend fun search(query: String, limit: Int = 100): List<NoteEntity> =
        if (query.isBlank()) emptyList() else noteDao.search(query.trim(), limit)

    suspend fun create(
        notebookId: Long,
        title: String = "",
        contentMd: String = "",
        sourceType: NoteSourceType = NoteSourceType.MANUAL
    ): Long {
        val now = System.currentTimeMillis()
        return noteDao.insert(
            NoteEntity(
                notebookId = notebookId,
                title = title,
                contentMd = contentMd,
                contentText = contentMd,
                sourceType = sourceType,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 编辑器保存：写入正文并刷新时间戳 */
    suspend fun save(id: Long, title: String, contentMd: String) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(
            existing.copy(
                title = title,
                contentMd = contentMd,
                contentText = contentMd,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun update(note: NoteEntity) = noteDao.update(note)

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val note = noteDao.getById(id) ?: return
        noteDao.update(note.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val note = noteDao.getById(id) ?: return
        noteDao.update(note.copy(archived = archived, updatedAt = System.currentTimeMillis()))
    }

    suspend fun softDelete(id: Long) = noteDao.softDelete(id, System.currentTimeMillis())

    /** 物理删除：用于丢弃从未填过任何内容的空笔记 */
    suspend fun hardDelete(id: Long) {
        noteDao.getById(id)?.let { noteDao.delete(it) }
    }

    suspend fun count(): Int = noteDao.count()

    // ---------- 附件 ----------

    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeByNote(noteId)

    suspend fun addAttachment(
        noteId: Long,
        uri: String,
        mimeType: String? = null,
        width: Int = 0,
        height: Int = 0,
        sizeBytes: Long = 0
    ): Long = attachmentDao.insert(
        AttachmentEntity(
            noteId = noteId,
            uri = uri,
            mimeType = mimeType,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun removeAttachment(id: Long) = attachmentDao.deleteById(id)
}
