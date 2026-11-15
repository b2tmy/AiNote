package com.ainote.app.feature.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.ai.AiRepository
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatRole
import com.ainote.core.data.repository.NoteRepository
import com.ainote.core.database.dao.PromptTemplateDao
import com.ainote.core.database.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 编辑器保存状态，用于底部提示文案 */
enum class SaveState { IDLE, SAVING, SAVED }

/** 四个场景动作，各自对应库里的一条提示词模板 */
enum class NoteAiAction(val templateKey: String, val label: String) {
    SUMMARIZE("note_summarize", "摘要"),
    CONTINUE("note_continue", "续写"),
    POLISH("note_polish", "润色"),
    EXTRACT_TODOS("note_extract_todos", "提取待办")
}

enum class ApplyMode { REPLACE, APPEND }

sealed interface AiActionState {
    data object Idle : AiActionState
    data class Running(val label: String) : AiActionState
    data class Done(val label: String, val result: String) : AiActionState
    data class Failed(val message: String) : AiActionState
}

@HiltViewModel
class NoteEditViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val aiRepository: AiRepository,
    private val promptTemplateDao: PromptTemplateDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: Long = checkNotNull(savedStateHandle.get<Long>("noteId")) {
        "noteId 缺失"
    }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _aiState = MutableStateFlow<AiActionState>(AiActionState.Idle)
    val aiState: StateFlow<AiActionState> = _aiState.asStateFlow()

    private var note: NoteEntity? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = noteRepository.getById(noteId) ?: return@launch
            note = loaded
            _title.value = loaded.title
            _content.value = loaded.contentMd
            _pinned.value = loaded.pinned
        }
    }

    fun onTitleChange(value: String) {
        _title.value = value
        scheduleSave()
    }

    fun onContentChange(value: String) {
        _content.value = value
        scheduleSave()
    }

    fun togglePin() {
        val next = !_pinned.value
        _pinned.value = next
        viewModelScope.launch { noteRepository.setPinned(noteId, next) }
    }

    fun delete() {
        viewModelScope.launch {
            saveJob?.cancel()
            noteRepository.softDelete(noteId)
            _deleted.value = true
        }
    }

    // ---------------- AI 动作 ----------------

    /**
     * 跑一次场景动作。
     * 结果不直接写进正文 —— 先给用户看，由他决定替换还是追加，
     * 避免 AI 一句话把人家写的东西覆盖掉。
     */
    fun runAction(action: NoteAiAction) {
        val current = _content.value
        if (current.isBlank()) {
            _aiState.value = AiActionState.Failed("正文还是空的，先写点东西")
            return
        }

        viewModelScope.launch {
            _aiState.value = AiActionState.Running(action.label)

            val provider = aiRepository.getDefaultProvider()
            if (provider == null) {
                _aiState.value = AiActionState.Failed("还没有配置模型服务")
                return@launch
            }

            val template = promptTemplateDao.getByKey(action.templateKey)
            val systemPrompt = template?.systemPrompt ?: "你是中文写作助手。"
            val userPrompt = (template?.userTemplate ?: "{content}")
                .replace("{content}", current)

            aiRepository.complete(
                provider,
                listOf(
                    ChatMessage(ChatRole.SYSTEM, systemPrompt),
                    ChatMessage(ChatRole.USER, userPrompt)
                )
            ).fold(
                onSuccess = { response ->
                    val text = response.content.trim()
                    _aiState.value = if (text.isBlank()) {
                        AiActionState.Failed("模型没有返回内容")
                    } else {
                        AiActionState.Done(action.label, text)
                    }
                },
                onFailure = { error ->
                    _aiState.value = AiActionState.Failed(error.message ?: "调用失败")
                }
            )
        }
    }

    fun applyResult(mode: ApplyMode) {
        val state = _aiState.value as? AiActionState.Done ?: return
        _content.value = when (mode) {
            ApplyMode.REPLACE -> state.result
            ApplyMode.APPEND -> _content.value.trimEnd() + "\n\n" + state.result
        }
        scheduleSave()
        _aiState.value = AiActionState.Idle
    }

    fun dismissAiState() {
        _aiState.value = AiActionState.Idle
    }

    // ---------------- 保存 ----------------

    /** 防抖保存：停止输入 700ms 后写库 */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _saveState.value = SaveState.SAVING
            delay(700)
            noteRepository.save(noteId, _title.value, _content.value)
            _saveState.value = SaveState.SAVED
        }
    }

    /** 离开页面时立即落盘；从未填过内容的空笔记直接丢弃 */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch {
            if (_title.value.isBlank() && _content.value.isBlank()) {
                noteRepository.hardDelete(noteId)
            } else {
                noteRepository.save(noteId, _title.value, _content.value)
            }
        }
    }
}
