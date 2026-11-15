package com.ainote.core.database.di

import android.content.Context
import com.ainote.core.database.AiNoteDatabase
import com.ainote.core.database.dao.AiChatDao
import com.ainote.core.database.dao.AiProviderDao
import com.ainote.core.database.dao.AttachmentDao
import com.ainote.core.database.dao.CalendarEventDao
import com.ainote.core.database.dao.DailySummaryDao
import com.ainote.core.database.dao.MemoDao
import com.ainote.core.database.dao.NoteDao
import com.ainote.core.database.dao.NotebookDao
import com.ainote.core.database.dao.PromptTemplateDao
import com.ainote.core.database.dao.ReminderDao
import com.ainote.core.database.dao.ScreenshotRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AiNoteDatabase =
        AiNoteDatabase.build(context)

    @Provides
    fun provideNotebookDao(db: AiNoteDatabase): NotebookDao = db.notebookDao()

    @Provides
    fun provideNoteDao(db: AiNoteDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideAttachmentDao(db: AiNoteDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideScreenshotRecordDao(db: AiNoteDatabase): ScreenshotRecordDao = db.screenshotRecordDao()

    @Provides
    fun provideMemoDao(db: AiNoteDatabase): MemoDao = db.memoDao()

    @Provides
    fun provideCalendarEventDao(db: AiNoteDatabase): CalendarEventDao = db.calendarEventDao()

    @Provides
    fun provideReminderDao(db: AiNoteDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideAiProviderDao(db: AiNoteDatabase): AiProviderDao = db.aiProviderDao()

    @Provides
    fun provideAiChatDao(db: AiNoteDatabase): AiChatDao = db.aiChatDao()

    @Provides
    fun providePromptTemplateDao(db: AiNoteDatabase): PromptTemplateDao = db.promptTemplateDao()

    @Provides
    fun provideDailySummaryDao(db: AiNoteDatabase): DailySummaryDao = db.dailySummaryDao()
}
