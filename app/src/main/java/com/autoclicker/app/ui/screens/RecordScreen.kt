package com.autoclicker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoclicker.app.data.ActionType
import com.autoclicker.app.data.Script
import com.autoclicker.app.data.ScriptAction
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onSaveScript: (Script) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordedActions = remember { mutableStateListOf<ScriptAction>() }
    var scriptName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var repeatCount by remember { mutableIntStateOf(1) }
    var actionInterval by remember { mutableLongStateOf(100L) }
    var repeatInterval by remember { mutableLongStateOf(500L) }
    var speedFactor by remember { mutableStateOf("1.0") }
    var showSettings by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableLongStateOf(0L) }

    // 录制计时器
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedTime = 0L
            while (isRecording) {
                delay(100)
                elapsedTime += 100
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录制脚本") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (recordedActions.isNotEmpty()) {
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 录制状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 录制指示灯
                    if (isRecording) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "录制中",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "● 录制中 - ${formatTime(elapsedTime)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "未录制",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "准备就绪",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "已录制 ${recordedActions.size} 个动作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    // 控制按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 录制/停止按钮
                        Button(
                            onClick = { isRecording = !isRecording },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRecording) "停止录制" else "开始录制")
                        }

                        if (recordedActions.isNotEmpty() && !isRecording) {
                            OutlinedButton(onClick = { showSaveDialog = true }) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("保存脚本")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 手动添加动作
                    if (!isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    recordedActions.add(
                                        ScriptAction(type = ActionType.TAP, x = 500f, y = 1000f)
                                    )
                                },
                                label = { Text("+ 点击") },
                                leadingIcon = {
                                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            AssistChip(
                                onClick = {
                                    recordedActions.add(
                                        ScriptAction(type = ActionType.SWIPE, x = 100f, y = 1000f, x2 = 900f, y2 = 1000f, duration = 300L)
                                    )
                                },
                                label = { Text("+ 滑动") },
                                leadingIcon = {
                                    Icon(Icons.Default.Swipe, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    recordedActions.add(
                                        ScriptAction(type = ActionType.RANDOM_TAP, x = 300f, y = 600f, x2 = 700f, y2 = 1400f)
                                    )
                                },
                                label = { Text("+ 随机点击") },
                                leadingIcon = {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            AssistChip(
                                onClick = {
                                    recordedActions.add(
                                        ScriptAction(type = ActionType.DELAY, delay = 500L)
                                    )
                                },
                                label = { Text("+ 延迟") },
                                leadingIcon = {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            AssistChip(
                                onClick = {
                                    recordedActions.add(
                                        ScriptAction(type = ActionType.FIND_TEXT, text = "按钮文字")
                                    )
                                },
                                label = { Text("+ 找字") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            // 脚本设置
            AnimatedVisibility(visible = showSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("脚本设置", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = scriptName.ifEmpty { "录制脚本 ${System.currentTimeMillis() % 10000}" },
                            onValueChange = { scriptName = it },
                            label = { Text("脚本名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = repeatCount.toString(),
                                onValueChange = { repeatCount = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                                label = { Text("重复次数(0=无限)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = actionInterval.toString(),
                                onValueChange = { actionInterval = it.filter { c -> c.isDigit() }.toLongOrNull() ?: 100L },
                                label = { Text("动作间隔(ms)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = repeatInterval.toString(),
                            onValueChange = { repeatInterval = it.filter { c -> c.isDigit() }.toLongOrNull() ?: 500L },
                            label = { Text("循环间隔(ms)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = speedFactor,
                            onValueChange = { speedFactor = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("速度倍率 (0.25~5.0)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 动作列表
            if (recordedActions.isNotEmpty()) {
                Text(
                    text = "动作列表",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(recordedActions) { index, action ->
                        ActionItem(
                            index = index,
                            action = action,
                            onEdit = { newAction ->
                                recordedActions[index] = newAction
                            },
                            onDelete = {
                                recordedActions.removeAt(index)
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    val item = recordedActions.removeAt(index)
                                    recordedActions.add(index - 1, item)
                                }
                            },
                            onMoveDown = {
                                if (index < recordedActions.size - 1) {
                                    val item = recordedActions.removeAt(index)
                                    recordedActions.add(index + 1, item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 保存对话框
    if (showSaveDialog) {
        val name = scriptName.ifEmpty { "录制脚本 ${System.currentTimeMillis() % 10000}" }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存脚本") },
            text = {
                Column {
                    Text("脚本名称: $name")
                    Spacer(Modifier.height(4.dp))
                    Text("动作数: ${recordedActions.size}")
                    Text("重复次数: ${if (repeatCount == 0) "无限" else repeatCount.toString()}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val script = Script(
                        name = name,
                        actions = recordedActions.toMutableList(),
                        repeatCount = repeatCount,
                        intervalBetweenActions = actionInterval,
                        intervalBetweenRepeats = repeatInterval,
                        speedFactor = speedFactor.toFloatOrNull()?.coerceIn(0.25f, 5f) ?: 1.0f
                    )
                    onSaveScript(script)
                    showSaveDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun ActionItem(
    index: Int,
    action: ScriptAction,
    onEdit: (ScriptAction) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp)
            )

            // 类型图标
            Icon(
                imageVector = when (action.type) {
                    ActionType.TAP -> Icons.Default.TouchApp
                    ActionType.RANDOM_TAP -> Icons.Default.Shuffle
                    ActionType.SWIPE -> Icons.Default.Swipe
                    ActionType.LONG_PRESS -> Icons.Default.TouchApp
                    ActionType.DELAY -> Icons.Default.Timer
                    ActionType.FIND_TEXT -> Icons.Default.Search
                    ActionType.REPEAT_START -> Icons.Default.Repeat
                    ActionType.REPEAT_END -> Icons.Default.Repeat
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            // 动作描述
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (action.type) {
                        ActionType.TAP -> "点击 (${action.x.toInt()}, ${action.y.toInt()})"
                        ActionType.RANDOM_TAP -> "随机点击 (${action.x.toInt()},${action.y.toInt()})~(${action.x2.toInt()},${action.y2.toInt()})"
                        ActionType.SWIPE -> "滑动 (${action.x.toInt()},${action.y.toInt()}) → (${action.x2.toInt()},${action.y2.toInt()})"
                        ActionType.LONG_PRESS -> "长按 (${action.x.toInt()}, ${action.y.toInt()}) ${action.duration}ms"
                        ActionType.DELAY -> "等待 ${action.delay}ms"
                        ActionType.FIND_TEXT -> "找字点击: \"${action.text}\""
                        ActionType.REPEAT_START -> "循环开始 ×${action.delay.toInt()}"
                        ActionType.REPEAT_END -> "循环结束"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 操作按钮
            Row {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showEdit = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // 编辑对话框
    if (showEdit) {
        EditActionDialog(
            action = action,
            onDismiss = { showEdit = false },
            onSave = {
                onEdit(it)
                showEdit = false
            }
        )
    }
}

@Composable
fun EditActionDialog(
    action: ScriptAction,
    onDismiss: () -> Unit,
    onSave: (ScriptAction) -> Unit
) {
    var x by remember { mutableStateOf(action.x.toString()) }
    var y by remember { mutableStateOf(action.y.toString()) }
    var x2 by remember { mutableStateOf(action.x2.toString()) }
    var y2 by remember { mutableStateOf(action.y2.toString()) }
    var duration by remember { mutableStateOf(action.duration.toString()) }
    var delay by remember { mutableStateOf(action.delay.toString()) }
    var text by remember { mutableStateOf(action.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑动作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("类型: ${action.type.name}", style = MaterialTheme.typography.labelLarge)

                if (action.type == ActionType.FIND_TEXT) {
                    // 找字点击: 输入要识别的文字
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("要识别的文字 (如: 确定, 开始)") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (action.type == ActionType.RANDOM_TAP) {
                    // 随机区域: 左上角 + 右下角
                    Text("区域左上角", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = x,
                            onValueChange = { x = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("左上X") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = y,
                            onValueChange = { y = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("左上Y") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("区域右下角", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = x2,
                            onValueChange = { x2 = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("右下X") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = y2,
                            onValueChange = { y2 = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("右下Y") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (action.type != ActionType.DELAY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = x,
                            onValueChange = { x = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("X") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = y,
                            onValueChange = { y = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Y") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (action.type == ActionType.SWIPE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = x2,
                            onValueChange = { x2 = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("终点X") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = y2,
                            onValueChange = { y2 = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("终点Y") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (action.type == ActionType.DELAY) {
                    OutlinedTextField(
                        value = delay,
                        onValueChange = { delay = it.filter { c -> c.isDigit() } },
                        label = { Text("延迟(ms)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (action.type != ActionType.TAP && action.type != ActionType.RANDOM_TAP) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { c -> c.isDigit() } },
                        label = { Text("时长(ms)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    action.copy(
                        x = x.toFloatOrNull() ?: action.x,
                        y = y.toFloatOrNull() ?: action.y,
                        x2 = x2.toFloatOrNull() ?: action.x2,
                        y2 = y2.toFloatOrNull() ?: action.y2,
                        duration = duration.toLongOrNull() ?: action.duration,
                        delay = delay.toLongOrNull() ?: action.delay,
                        text = text
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    return String.format("%02d:%02d", minutes, seconds % 60)
}
