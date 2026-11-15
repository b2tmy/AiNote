package com.ainote.app.feature.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ainote.core.common.TimeFormat
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.ScreenshotRecordEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotDetailScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: ScreenshotDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.openNote.collect { noteId -> onOpenNote(noteId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("截图详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = record
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AsyncImage(
                    model = File(current.localPath),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.6f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            item { MetaRow(current) }

            current.summary?.let { summary ->
                item {
                    SectionCard(title = "AI 摘要") {
                        Text(summary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            current.tagsJson?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }?.let { tags ->
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tags) { tag ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

            current.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
                item {
                    SectionCard(title = "图中文字") {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val todos = current.todosJson
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            if (todos.isNotEmpty()) {
                item {
                    SectionCard(title = "提取的待办") {
                        todos.forEach { todo ->
                            Text(
                                text = "• $todo",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::addTodosToMemos) {
                            Text("全部加入备忘")
                        }
                    }
                }
            }

            current.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("分析失败", fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::retry) { Text("重新分析") }
                        }
                    }
                }
            }

            if (current.status == AnalyzeStatus.PENDING || current.status == AnalyzeStatus.RUNNING) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = if (current.status == AnalyzeStatus.RUNNING) {
                                    "正在分析…"
                                } else {
                                    "排队等待分析"
                                },
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "没有配置模型服务或没有网络时，它会一直留在这个状态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            current.noteId?.let {
                item {
                    Button(
                        onClick = viewModel::openLinkedNote,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("查看生成的笔记") }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(record: ScreenshotRecordEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(record.status)
        Spacer(Modifier.width(10.dp))
        Text(
            text = TimeFormat.dateTime(record.capturedAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        record.tokensIn?.let { input ->
            Text(
                text = "token ${input}/${record.tokensOut ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(status: AnalyzeStatus) {
    val (label, color) = when (status) {
        AnalyzeStatus.PENDING -> "待分析" to MaterialTheme.colorScheme.onSurfaceVariant
        AnalyzeStatus.RUNNING -> "分析中" to MaterialTheme.colorScheme.primary
        AnalyzeStatus.DONE -> "已分析" to MaterialTheme.colorScheme.primary
        AnalyzeStatus.FAILED -> "分析失败" to MaterialTheme.colorScheme.error
        AnalyzeStatus.SKIPPED -> "已跳过" to MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
