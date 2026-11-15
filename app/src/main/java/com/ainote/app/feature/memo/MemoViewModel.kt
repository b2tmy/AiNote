package com.ainote.app.feature.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.data.repository.MemoRepository
import com.ainote.core.database.entity.MemoEntity
import com.ainote.core.database.entity.MemoPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoViewModel @Inject constructor(
    private val repository: MemoRepository
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _priority = MutableStateFlow(MemoPriority.NORMAL)
    val priority: StateFlow<MemoPriority> = _priority.asStateFlow()

    /** 可选的提醒时间；设置后会在 reminders 表里生成一条到点提醒 */
    private val _remindAt = MutableStateFlow<Long?>(null)
    val remindAt: StateFlow<Long?> = _remindAt.asStateFlow()

    val memos: StateFlow<List<MemoEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCount: StateFlow<Int> = memos
        .map { list -> list.count { !it.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun onPriorityChange(value: MemoPriority) {
        _priority.value = value
    }

    fun setRemindAt(value: Long?) {
        _remindAt.value = value
    }

    fun add() {
        val text = _input.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            repository.add(
                content = text,
                dueAt = _remindAt.value,
                priority = _priority.value
            )
            _input.value = ""
            _remindAt.value = null
        }
    }

    fun toggle(memo: MemoEntity) {
        viewModelScope.launch { repository.toggleDone(memo.id) }
    }

    fun delete(memo: MemoEntity) {
        viewModelScope.launch { repository.delete(memo) }
    }

    fun cyclePriority(memo: MemoEntity) {
        val next = when (memo.priority) {
            MemoPriority.LOW -> MemoPriority.NORMAL
            MemoPriority.NORMAL -> MemoPriority.HIGH
            MemoPriority.HIGH -> MemoPriority.LOW
        }
        viewModelScope.launch { repository.update(memo.copy(priority = next)) }
    }
}
