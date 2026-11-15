package com.ainote.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.ai.AiRepository
import com.ainote.core.common.TimeFormat
import com.ainote.core.database.dao.NoteDao
import com.ainote.core.database.dao.NotebookDao
import com.ainote.core.database.entity.AiProviderEntity
import com.ainote.core.database.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Success(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AiRepository,
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao
) : ViewModel() {

    val providers: StateFlow<List<AiProviderEntity>> = repository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 哪些 Provider 已经存过 Key —— 只暴露「有没有」，不暴露值 */
    val providersWithKey: StateFlow<Set<Long>> = repository.observeProviders()
        .map { list -> list.filter { repository.hasApiKey(it) }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    val secretStoreAvailable: Boolean get() = repository.secretStoreAvailable

    fun addPreset(preset: ProviderPreset) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val isFirst = providers.value.isEmpty()
            repository.saveProvider(
                preset.toEntity(now).copy(isDefault = isFirst),
                apiKey = null
            )
            resetTestState()
        }
    }

    fun save(provider: AiProviderEntity, apiKey: String?) {
        viewModelScope.launch {
            repository.saveProvider(provider, apiKey)
            resetTestState()
        }
    }

    fun delete(provider: AiProviderEntity) {
        viewModelScope.launch {
            repository.deleteProvider(provider)
            resetTestState()
        }
    }

    fun setDefault(provider: AiProviderEntity) {
        viewModelScope.launch { repository.setDefaultProvider(provider.id) }
    }

    /**
     * 连通性测试。
     * [draft] 是编辑框里的当前值（可能还没保存），[apiKeyInput] 为空时用已存的那把 key。
     */
    fun test(draft: AiProviderEntity, apiKeyInput: String?) {
        viewModelScope.launch {
            _testState.value = TestState.Testing

            val effective = if (!apiKeyInput.isNullOrBlank()) {
                draft
            } else {
                // 没输入新 Key，就用库里已经存好的
                repository.getProvider(draft.id) ?: draft
            }

            if (apiKeyInput.isNullOrBlank() && !repository.hasApiKey(effective)) {
                _testState.value = TestState.Failed("还没填 API Key")
                return@launch
            }

            // 先落盘再测，确保测试用的就是保存后的配置
            val savedId = repository.saveProvider(effective, apiKeyInput?.takeIf { it.isNotBlank() })
            val provider = repository.getProvider(savedId) ?: effective.copy(id = savedId)

            repository.testConnection(provider).fold(
                onSuccess = { reply ->
                    _testState.value = TestState.Success(
                        "连接成功，模型回了一句：${reply.take(60)}"
                    )
                },
                onFailure = { error ->
                    _testState.value = TestState.Failed(error.message ?: "未知错误")
                }
            )
        }
    }

    fun resetTestState() {
        _testState.value = TestState.Idle
    }

    // ---------------- 数据导出 ----------------

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    /** 把所有笔记导出成一份 Markdown，写到用户选定的位置 */
    fun exportNotes(context: Context, uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = null
            runCatching {
                val notes = noteDao.getAllActive()
                val notebookNames = notebookDao.getAllOnce().associate { it.id to it.name }
                val text = buildExportText(notes, notebookNames)

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选位置")

                notes.size
            }.onSuccess { count ->
                _exportMessage.value = "已导出 $count 条笔记"
            }.onFailure { error ->
                _exportMessage.value = "导出失败：${error.message}"
            }
        }
    }

    fun dismissExportMessage() {
        _exportMessage.value = null
    }

    private fun buildExportText(
        notes: List<NoteEntity>,
        notebookNames: Map<Long, String>
    ): String = buildString {
        appendLine("# 智记导出")
        appendLine()
        appendLine("共 ${notes.size} 条笔记 · ${TimeFormat.dateTime(System.currentTimeMillis())}")
        appendLine()

        notes.groupBy { it.notebookId }.forEach { (notebookId, group) ->
            appendLine("## ${notebookNames[notebookId] ?: "未分类"}")
            appendLine()
            group.forEach { note ->
                appendLine("### ${note.title.ifBlank { "无标题" }}")
                appendLine()
                appendLine("> 更新于 ${TimeFormat.dateTime(note.updatedAt)}")
                appendLine()
                appendLine(note.contentMd)
                appendLine()
            }
        }
    }
}
