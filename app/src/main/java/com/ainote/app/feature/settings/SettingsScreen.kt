package com.ainote.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainote.core.database.entity.AiProviderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    reportViewModel: DailyReportViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val withKey by viewModel.providersWithKey.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> if (uri != null) viewModel.exportNotes(context, uri) }

    var editing by remember { mutableStateOf<AiProviderEntity?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPresets = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加模型服务")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DailyReportSection(
                    viewModel = reportViewModel,
                    onOpenNote = onOpenNote
                )
            }

            item {
                ExportCard(
                    message = exportMessage,
                    onExport = { exportLauncher.launch("ainote-export.md") }
                )
            }

            item {
                if (!viewModel.secretStoreAvailable) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("密钥库不可用", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "系统密钥库初始化失败，API Key 存不进来。换一台设备或重启系统再试。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Key 存在哪儿", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "API Key 用系统 Keystore（AES-GCM）加密后存在本机，" +
                                    "不进数据库、不进日志、不进备份。它只会发给你自己填的模型服务地址。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (providers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("还没有配置模型服务", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点右下角 + 从预设里挑一个，再填上你的 API Key",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    hasKey = withKey.contains(provider.id),
                    onClick = { editing = provider }
                )
            }
        }
    }

    if (showPresets) {
        ModalBottomSheet(
            onDismissRequest = { showPresets = false },
            sheetState = sheetState
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("选择厂商", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ProviderPresets.all.forEach { preset ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addPreset(preset)
                                showPresets = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(preset.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${preset.model.ifBlank { "（自填模型名）" }} · ${preset.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editing?.let { provider ->
        ProviderEditSheet(
            provider = provider,
            testState = testState,
            hasKey = withKey.contains(provider.id),
            onDismiss = {
                editing = null
                viewModel.resetTestState()
            },
            onSave = { updated, key ->
                viewModel.save(updated, key)
                editing = null
            },
            onTest = { draft, key -> viewModel.test(draft, key) },
            onDelete = {
                viewModel.delete(provider)
                editing = null
            }
        )
    }
}

@Composable
private fun ExportCard(message: String?, onExport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "导出数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "把所有笔记汇成一份 Markdown 存到你自己选的位置。" +
                    "数据库文件和 API Key 不会被导出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onExport) { Text("导出为 Markdown") }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AiProviderEntity,
    hasKey: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (provider.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = provider.model.ifBlank { "未填模型" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (hasKey) "已配置 Key" else "缺 API Key")
                        if (provider.supportsVision) append(" · 支持看图")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasKey) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditSheet(
    provider: AiProviderEntity,
    testState: TestState,
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiProviderEntity, String?) -> Unit,
    onTest: (AiProviderEntity, String?) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.id) { mutableStateOf(provider.model) }
    var apiKey by remember(provider.id) { mutableStateOf("") }
    var vision by remember(provider.id) { mutableStateOf(provider.supportsVision) }
    var isDefault by remember(provider.id) { mutableStateOf(provider.isDefault) }

    fun draft() = provider.copy(
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        supportsVision = vision,
        isDefault = isDefault
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("编辑模型服务", style = MaterialTheme.typography.titleMedium) }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址（Base URL）") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false
                    )
                )
            }

            item {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    placeholder = { Text("deepseek-chat") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
                )
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = {
                        Text(if (hasKey) "已保存，留空则不修改" else "粘贴你的 Key")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    )
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("支持图片理解", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关掉后用本地 OCR 兜底（截图分析会走纯文本通路）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vision, onCheckedChange = { vision = it })
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为默认", modifier = Modifier.weight(1f))
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }

            item {
                when (val state = testState) {
                    is TestState.Idle -> Unit
                    is TestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("正在测试…", style = MaterialTheme.typography.bodySmall)
                    }

                    is TestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                    }

                    is TestState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onTest(draft(), apiKey) },
                        modifier = Modifier.weight(1f)
                    ) { Text("测试连接") }

                    Button(
                        onClick = { onSave(draft(), apiKey.takeIf { it.isNotBlank() }) },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                }
            }

            item {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除这个服务", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
