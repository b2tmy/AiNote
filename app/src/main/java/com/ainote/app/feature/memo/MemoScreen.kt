package com.ainote.app.feature.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainote.core.common.TimeFormat
import com.ainote.core.database.entity.MemoEntity
import com.ainote.core.database.entity.MemoPriority
import java.time.LocalDate

@Composable
fun MemoScreen(viewModel: MemoViewModel = hiltViewModel()) {
    val memos by viewModel.memos.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val priority by viewModel.priority.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val remindAt by viewModel.remindAt.collectAsStateWithLifecycle()

    var showRemindPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = input,
            onValueChange = viewModel::onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("记一条待办…") },
            trailingIcon = {
                IconButton(onClick = viewModel::add) {
                    Icon(Icons.Filled.Add, contentDescription = "添加")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.add() }),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
                .clickable { showRemindPicker = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = remindAt?.let { "提醒：${TimeFormat.dateTime(it)}" } ?: "设置提醒时间",
                style = MaterialTheme.typography.labelMedium,
                color = if (remindAt != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (remindAt != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "清除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { viewModel.setRemindAt(null) }
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$activeCount 项待办",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            PrioritySelector(current = priority, onSelect = viewModel::onPriorityChange)
        }

        Spacer(Modifier.height(8.dp))

        if (memos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("暂无备忘", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "在上方输入框写下第一件事，回车即可添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memos, key = { it.id }) { memo ->
                    MemoRow(
                        memo = memo,
                        onToggle = { viewModel.toggle(memo) },
                        onPriority = { viewModel.cyclePriority(memo) },
                        onDelete = { viewModel.delete(memo) }
                    )
                }
            }
        }
    }

    if (showRemindPicker) {
        RemindPickerSheet(
            onDismiss = { showRemindPicker = false },
            onPick = { millis ->
                viewModel.setRemindAt(millis)
                showRemindPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindPickerSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    var dayOffset by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("设置提醒时间", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "今天", 1 to "明天", 7 to "下周").forEach { (offset, label) ->
                    FilterChip(
                        selected = dayOffset == offset,
                        onClick = { dayOffset = offset },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val date = LocalDate.now().plusDays(dayOffset.toLong())
                    onPick(TimeFormat.toEpochMillis(date, timeState.hour, timeState.minute))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("确定") }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrioritySelector(
    current: MemoPriority,
    onSelect: (MemoPriority) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MemoPriority.entries.forEach { p ->
            val label = when (p) {
                MemoPriority.LOW -> "低"
                MemoPriority.NORMAL -> "中"
                MemoPriority.HIGH -> "高"
            }
            val selected = p == current
            Box(
                modifier = Modifier
                    .background(
                        color = if (selected) {
                            priorityColor(p)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun priorityColor(priority: MemoPriority) = when (priority) {
    MemoPriority.LOW -> MaterialTheme.colorScheme.outline
    MemoPriority.NORMAL -> MaterialTheme.colorScheme.primary
    MemoPriority.HIGH -> MaterialTheme.colorScheme.error
}

@Composable
private fun MemoRow(
    memo: MemoEntity,
    onToggle: () -> Unit,
    onPriority: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = memo.done, onCheckedChange = { onToggle() })

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(priorityColor(memo.priority), CircleShape)
                    .clickable(onClick = onPriority)
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memo.content,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (memo.done) TextDecoration.LineThrough else null,
                    color = if (memo.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                val meta = buildString {
                    memo.dueAt?.let { append("截止 ${TimeFormat.relative(it)}") }
                    if (memo.done) {
                        if (isNotEmpty()) append(" · ")
                        append("已完成")
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
