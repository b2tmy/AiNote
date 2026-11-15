package com.ainote.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainote.core.common.TimeFormat
import com.ainote.core.database.entity.CalendarEventEntity
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    val dayEvents by viewModel.dayEvents.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }

    val eventDates = remember(monthEvents) {
        monthEvents.map { TimeFormat.dayKey(it.startAt) }.toSet()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建日程")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MonthHeader(
                month = month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onToday = viewModel::goToToday
            )

            WeekdayHeader()

            MonthGrid(
                month = month,
                selected = selected,
                eventDates = eventDates,
                onSelect = viewModel::selectDate
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${selected.monthValue} 月 ${selected.dayOfMonth} 日 · ${dayEvents.size} 项日程",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(6.dp))

            if (dayEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "这一天还没有安排",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayEvents, key = { it.id }) { event ->
                        EventCard(event = event, onDelete = { viewModel.deleteEvent(event) })
                    }
                }
            }
        }
    }

    if (showEditor) {
        EventEditorSheet(
            date = selected,
            onDismiss = { showEditor = false },
            onSave = { title, hour, minute, duration, allDay, reminderOffset ->
                viewModel.createEvent(
                    title = title,
                    date = selected,
                    hour = hour,
                    minute = minute,
                    durationMinutes = duration,
                    allDay = allDay,
                    reminderOffsetMs = reminderOffset
                )
                showEditor = false
            }
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = onToday) { Text("今天") }
        IconButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    eventDates: Set<String>,
    onSelect: (LocalDate) -> Unit
) {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // 周一为第一列
    val daysInMonth = month.lengthOfMonth()
    val rows = ((leadingBlanks + daysInMonth) + 6) / 7
    val today = LocalDate.now()

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayNumber = cellIndex - leadingBlanks + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isSelected = date == selected,
                                isToday = date == today,
                                hasEvent = eventDates.contains(TimeFormat.dayKey(date)),
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    color = if (hasEvent) {
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun EventCard(event: CalendarEventEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (event.allDay) {
                        "全天"
                    } else {
                        "${TimeFormat.time(event.startAt)} - ${TimeFormat.time(event.endAt)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Long, Boolean, Long?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)

    var title by remember { mutableStateOf("") }
    var durationIndex by remember { mutableIntStateOf(1) }
    var reminderIndex by remember { mutableIntStateOf(2) }

    val durations = CalendarViewModel.DURATION_OPTIONS
    val reminders = CalendarViewModel.REMINDER_OPTIONS

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("新建日程", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("做什么") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))
            Text("时长", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEachIndexed { index, (label, _) ->
                    FilterChip(
                        selected = durationIndex == index,
                        onClick = { durationIndex = index },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("提醒", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.forEachIndexed { index, (label, _) ->
                    FilterChip(
                        selected = reminderIndex == index,
                        onClick = { reminderIndex = index },
                        label = { Text(label) }
                    )
                }
            }

            if (durations[durationIndex].second > 0) {
                Spacer(Modifier.height(8.dp))
                Text("开始时间", style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val duration = durations[durationIndex].second
                    onSave(
                        title,
                        timeState.hour,
                        timeState.minute,
                        if (duration == 0L) 0L else duration,
                        duration == 0L,
                        reminders[reminderIndex].second
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }

            Spacer(Modifier.height(24.dp))
        }
    }
}
