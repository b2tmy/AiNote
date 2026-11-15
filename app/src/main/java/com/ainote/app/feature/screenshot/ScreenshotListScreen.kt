package com.ainote.app.feature.screenshot

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ainote.core.common.TimeFormat
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.ScreenshotRecordEntity
import com.ainote.service.WatcherScheduler
import java.io.File

@Composable
fun ScreenshotListScreen(
    onOpenDetail: (Long) -> Unit,
    viewModel: ScreenshotViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val watching by viewModel.watching.collectAsStateWithLifecycle()
    val ignoringBatteryOptimization by viewModel.ignoringBatteryOptimization.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[WatcherScheduler.readImagesPermission()] == true) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.refreshSystemStates()
        }
    }

    // 从系统设置页返回时重新检查权限与电池优化状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        WatcherStatusBar(
            watching = watching,
            hasPermission = hasPermission,
            onToggle = { enabled ->
                if (enabled && !hasPermission) {
                    permissionLauncher.launch(requiredPermissions())
                } else if (enabled) {
                    viewModel.startWatching()
                } else {
                    viewModel.stopWatching()
                }
            },
            onScanNow = viewModel::scanNow
        )

        if (!hasPermission) {
            PermissionCard(
                onRequest = { permissionLauncher.launch(requiredPermissions()) }
            )
        } else if (!ignoringBatteryOptimization) {
            BatteryHint(
                onOpenSettings = {
                    runCatching { context.startActivity(viewModel.batteryOptimizationIntent()) }
                }
            )
        }

        if (records.isEmpty()) {
            EmptyScreenshots(hasPermission = hasPermission)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    ScreenshotRow(
                        record = record,
                        onClick = { onOpenDetail(record.id) },
                        onRetry = { viewModel.retry(record) },
                        onDelete = { viewModel.delete(record) }
                    )
                }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(WatcherScheduler.readImagesPermission())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
private fun WatcherStatusBar(
    watching: Boolean,
    hasPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onScanNow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (watching) "截图监听已开启" else "截图监听已暂停",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (watching) {
                    if (hasPermission) "截图后自动进入待分析队列" else "还缺相册权限"
                } else {
                    "开启后截图才会被自动收录"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onScanNow, enabled = hasPermission) {
            Icon(Icons.Filled.Refresh, contentDescription = "立即扫描")
        }
        Switch(checked = watching && hasPermission, onCheckedChange = onToggle)
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("需要相册读取权限", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "智记需要读取系统相册，才能发现你新截的图。" +
                    "它只读取截图文件，不上传任何内容（除非你自己配了 AI 接口并开启分析）。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) { Text("去授权") }
        }
    }
}

@Composable
private fun BatteryHint(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpenSettings),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "建议关闭电池优化",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "未加入白名单时，系统可能在后台掐掉监听服务（国产 ROM 尤其明显）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "去设置",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyScreenshots(hasPermission: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("还没有截图记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasPermission) {
                "去截一张图试试，返回这里应该会看到它"
            } else {
                "先在上面授予相册权限"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScreenshotRow(
    record: ScreenshotRecordEntity,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = File(record.localPath),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                StatusBadge(record.status)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.summary
                        ?: record.ocrText?.takeIf { it.isNotBlank() }?.let { "OCR：$it" }
                        ?: "等待分析",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = TimeFormat.relative(record.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                record.error?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = err.take(120),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (record.status == AnalyzeStatus.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "重试",
                            modifier = Modifier.size(18.dp)
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
}

@Composable
private fun StatusBadge(status: AnalyzeStatus) {
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
