package com.ainote.app.feature.screenshot

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.data.repository.MemoRepository
import com.ainote.core.data.repository.ScreenshotRepository
import com.ainote.core.database.entity.MemoPriority
import com.ainote.core.database.entity.ScreenshotRecordEntity
import com.ainote.service.AnalyzeScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenshotDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScreenshotRepository,
    private val memoRepository: MemoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId: Long = checkNotNull(savedStateHandle.get<Long>("recordId")) {
        "recordId 缺失"
    }

    val record: StateFlow<ScreenshotRecordEntity?> = repository.observeById(recordId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _openNote = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val openNote = _openNote.asSharedFlow()

    /** 重新分析：状态退回 PENDING 后立刻入队 */
    fun retry() {
        viewModelScope.launch {
            repository.retry(recordId)
            AnalyzeScheduler.enqueue(context)
        }
    }

    fun openLinkedNote() {
        val noteId = record.value?.noteId ?: return
        viewModelScope.launch { _openNote.tryEmit(noteId) }
    }

    /** 把这张图里提取出的待办一次性落进备忘 */
    fun addTodosToMemos() {
        val todos = record.value?.todosJson
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (todos.isEmpty()) return

        viewModelScope.launch {
            todos.forEach { memoRepository.add(it, priority = MemoPriority.NORMAL) }
            Toast.makeText(
                context,
                "已加入 ${todos.size} 条备忘",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recordId)
            onDone()
        }
    }
}
