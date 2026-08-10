package com.autoclicker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.autoclicker.app.ui.components.ScriptListView

/**
 * 屏幕取点动作 (由 MainActivity 提供实现)
 */
class CoordinatePickerActions(
    val pickTap: ((Float, Float) -> Unit) -> Unit,
    val pickSwipeStart: ((Float, Float) -> Unit) -> Unit,
    val pickSwipeEnd: ((Float, Float) -> Unit) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scripts: List<Script>,
    runningScriptId: String?,
    permissionType: String,
    onPlayScript: (Script) -> Unit,
    onEditScript: (Script) -> Unit,
    onDeleteScript: (Script) -> Unit,
    onNavigateToRecord: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onQuickTap: (Float, Float, Long, Int) -> Unit,
    onQuickSwipe: (Float, Float, Float, Float, Long, Int) -> Unit,
    pickerActions: CoordinatePickerActions
) {
    var showQuickTapDialog by remember { mutableStateOf(false) }
    var showQuickSwipeDialog by remember { mutableStateOf(false) }
    var showPermissionInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("连点器", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "权限: $permissionType",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { showPermissionInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "权限信息")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToRecord,
                icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
                text = { Text("录制脚本") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 快捷操作栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "快捷操作",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 快速点击
                        FilledTonalButton(
                            onClick = { showQuickTapDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("快速点击", style = MaterialTheme.typography.labelLarge)
                        }
                        // 快速滑动
                        FilledTonalButton(
                            onClick = { showQuickSwipeDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Swipe, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("快速滑动", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 文字识别
                        FilledTonalButton(
                            onClick = onNavigateToOcr,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("文字识别", style = MaterialTheme.typography.labelLarge)
                        }
                        // 屏幕取点
                        FilledTonalButton(
                            onClick = { onPickTap { x, y -> onQuickTap(x, y, 100L, 1) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("屏幕取点", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // 脚本列表
            ScriptListView(
                scripts = scripts,
                runningScriptId = runningScriptId,
                onPlay = onPlayScript,
                onEdit = onEditScript,
                onDelete = onDeleteScript,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // 快速点击对话框
    if (showQuickTapDialog) {
        QuickTapDialog(
            onDismiss = { showQuickTapDialog = false },
            onPick = pickerActions.pickTap,
            onExecute = { x, y, duration, repeat ->
                onQuickTap(x, y, duration, repeat)
                showQuickTapDialog = false
            }
        )
    }

    // 快速滑动对话框
    if (showQuickSwipeDialog) {
        QuickSwipeDialog(
            onDismiss = { showQuickSwipeDialog = false },
            onPickStart = pickerActions.pickSwipeStart,
            onPickEnd = pickerActions.pickSwipeEnd,
            onExecute = { x1, y1, x2, y2, duration, repeat ->
                onQuickSwipe(x1, y1, x2, y2, duration, repeat)
                showQuickSwipeDialog = false
            }
        )
    }

    // 权限信息对话框
    if (showPermissionInfo) {
        AlertDialog(
            onDismissRequest = { showPermissionInfo = false },
            title = { Text("权限状态") },
            text = {
                Column {
                    Text("当前使用: $permissionType")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Shizuku: 无需Root即可使用系统级权限。\n" +
                        "请确保Shizuku已安装并运行。\n\n" +
                        "Root: 需要设备已获取Root权限。\n" +
                        "通过Magisk/KernelSU等方式获取。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionInfo = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
fun QuickTapDialog(
    onDismiss: () -> Unit,
    onPick: ((Float, Float) -> Unit) -> Unit,
    onExecute: (Float, Float, Long, Int) -> Unit
) {
    var xText by remember { mutableStateOf("500") }
    var yText by remember { mutableStateOf("1000") }
    var durationText by remember { mutableStateOf("100") }
    var repeatText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
        title = { Text("快速点击") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 屏幕选取按钮
                OutlinedButton(
                    onClick = {
                        onPick { x, y ->
                            xText = x.toInt().toString()
                            yText = y.toInt().toString()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🎯 从屏幕选取坐标")
                }
                OutlinedTextField(
                    value = xText,
                    onValueChange = { xText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("X 坐标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yText,
                    onValueChange = { yText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Y 坐标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                        label = { Text("间隔(ms)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repeatText,
                        onValueChange = { repeatText = it.filter { c -> c.isDigit() } },
                        label = { Text("次数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "提示: 也可点击\"从屏幕选取坐标\"直接点选, 无需开发者模式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val x = xText.toFloatOrNull() ?: 500f
                val y = yText.toFloatOrNull() ?: 1000f
                val duration = durationText.toLongOrNull() ?: 100L
                val repeat = repeatText.toIntOrNull() ?: 1
                onExecute(x, y, duration, repeat)
            }) {
                Text("执行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun QuickSwipeDialog(
    onDismiss: () -> Unit,
    onPickStart: ((Float, Float) -> Unit) -> Unit,
    onPickEnd: ((Float, Float) -> Unit) -> Unit,
    onExecute: (Float, Float, Float, Float, Long, Int) -> Unit
) {
    var x1Text by remember { mutableStateOf("100") }
    var y1Text by remember { mutableStateOf("1000") }
    var x2Text by remember { mutableStateOf("900") }
    var y2Text by remember { mutableStateOf("1000") }
    var durationText by remember { mutableStateOf("300") }
    var repeatText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Swipe, contentDescription = null) },
        title = { Text("快速滑动") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("起点坐标", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = x1Text,
                        onValueChange = { x1Text = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("起始X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = y1Text,
                        onValueChange = { y1Text = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("起始Y") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onPickStart { x, y ->
                                x1Text = x.toInt().toString()
                                y1Text = y.toInt().toString()
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Text("终点坐标", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = x2Text,
                        onValueChange = { x2Text = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("终点X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = y2Text,
                        onValueChange = { y2Text = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("终点Y") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onPickEnd { x, y ->
                                x2Text = x.toInt().toString()
                                y2Text = y.toInt().toString()
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                        label = { Text("滑动时长(ms)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repeatText,
                        onValueChange = { repeatText = it.filter { c -> c.isDigit() } },
                        label = { Text("次数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "提示: 点击🎯按钮可直接在屏幕上选取起点/终点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val x1 = x1Text.toFloatOrNull() ?: 100f
                val y1 = y1Text.toFloatOrNull() ?: 1000f
                val x2 = x2Text.toFloatOrNull() ?: 900f
                val y2 = y2Text.toFloatOrNull() ?: 1000f
                val duration = durationText.toLongOrNull() ?: 300L
                val repeat = repeatText.toIntOrNull() ?: 1
                onExecute(x1, y1, x2, y2, duration, repeat)
            }) {
                Text("执行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
