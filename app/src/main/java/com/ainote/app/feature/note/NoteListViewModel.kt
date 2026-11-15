package com.ainote.app.feature.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.data.repository.NoteRepository
import com.ainote.core.data.repository.NotebookRepository
import com.ainote.core.database.entity.NoteEntity
import com.ainote.core.database.entity.NotebookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val notebookRepository: NotebookRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedNotebookId = MutableStateFlow<Long?>(null)

    /** 新建成功后通知界面跳转到编辑页 */
    private val _openNote = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val openNote = _openNote.asSharedFlow()

    val notebooks: StateFlow<List<NotebookEntity>> = notebookRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery: StateFlow<String> = query
    val currentNotebookId: StateFlow<Long?> = selectedNotebookId

    val notes: StateFlow<List<NoteEntity>> =
        combine(query, selectedNotebookId, notebooks) { q, notebookId, notebooks ->
            q to (notebookId ?: notebooks.firstOrNull()?.id)
        }.flatMapLatest { (q, notebookId) ->
            when {
                q.isNotBlank() -> flow { emit(noteRepository.search(q)) }
                notebookId != null -> noteRepository.observeByNotebook(notebookId)
                else -> noteRepository.observeRecent()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun selectNotebook(id: Long) {
        selectedNotebookId.value = id
    }

    fun createNote() {
        viewModelScope.launch {
            val notebookId = selectedNotebookId.value
                ?: notebooks.value.firstOrNull()?.id
                ?: notebookRepository.requireAiAnalysisNotebook().let {
                    // 理论上不会走到这里：种子数据已建好默认笔记本
                    notebookRepository.create("我的笔记")
                }
            val id = noteRepository.create(notebookId = notebookId)
            _openNote.tryEmit(id)
        }
    }

    fun createNotebook(name: String) {
        viewModelScope.launch { notebookRepository.create(name) }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch { noteRepository.setPinned(note.id, !note.pinned) }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { noteRepository.softDelete(note.id) }
    }
}
