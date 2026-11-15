package com.ainote.app.feature.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    onBack: () -> Unit,
    viewModel: NoteEditViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    var showAiMenu by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    // 退出页面前强制落盘
    DisposableEffect(Unit) {
        onDispose { viewModel.flush() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = aiState) {
                        is AiActionState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${state.label}中…",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        else -> Text(
                            text = when (saveState) {
                                SaveState.IDLE -> ""
                                SaveState.SAVING -> "保存中…"
                                SaveState.SAVED -> "已保存"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.flush()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { showAiMenu = true },
                            enabled = aiState !is AiActionState.Running
                        ) { Text("AI") }

                        DropdownMenu(
                            expanded = showAiMenu,
                            onDismissRequest = { showAiMenu = false }
                        ) {
                            NoteAiAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        showAiMenu = false
                                        viewModel.runAction(action)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (pinned) "取消置顶" else "置顶",
                            tint = if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(onClick = viewModel::delete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                // 正文框贴底，不加这一句键盘会把它整块盖住
                .imePadding()
        ) {
            TextField(
                value = title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("标题") },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                maxLines = 3
            )

            Spacer(Modifier.height(4.dp))

            TextField(
                value = content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("开始写点什么…") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }

    when (val state = aiState) {
        is AiActionState.Done -> AlertDialog(
            onDismissRequest = viewModel::dismissAiState,
            title = { Text("${state.label}结果") },
            text = {
                Text(
                    text = state.result,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applyResult(ApplyMode.REPLACE) }) {
                    Text("替换正文")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.applyResult(ApplyMode.APPEND) }) {
                        Text("追加到末尾")
                    }
                    TextButton(onClick = viewModel::dismissAiState) { Text("取消") }
                }
            }
        )

        is AiActionState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissAiState,
            title = { Text("没能完成") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAiState) { Text("知道了") }
            }
        )

        else -> Unit
    }
}
