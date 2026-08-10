package com.autoclicker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoclicker.app.data.ActionType
import com.autoclicker.app.data.Script
import com.autoclicker.app.data.ScriptAction

/**
 * 流程图式脚本编辑器
 *
 * 以流程图形式展示和编辑脚本:
 * - 开始 → 节点序列 → 结束
 * - 循环节点(REPEAT_START/END)包裹的子节点缩进显示
 * - 节点支持添加/编辑/删除/上下移
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowEditScreen(
    script: Script?,
    onBack: () -> Unit,
    onSave: (Script) -> Unit
) {
    var name by remember { mutableStateOf(script?.name ?: "新脚本") }
    var repeatCount by remember { mutableStateOf((script?.repeatCount ?: 1).toString()) }
    var actionInterval by remember { mutableStateOf((script?.intervalBetweenActions ?: 100L).toString()) }
    val actions = remember { mutableStateListOf<ScriptAction>().apply { addAll(script?.actions ?: emptyList()) } }

    var showAddMenu by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("流程图编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showSaveConfirm = true }) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddMenu = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加节点") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 脚本设置卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("脚本名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = repeatCount,
                            onValueChange = { repeatCount = it.filter { c -> c.isDigit() } },
                            label = { Text("外层循环(0=无限)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = actionInterval,
                            onValueChange = { actionInterval = it.filter { c -> c.isDigit() } },
                            label = { Text("动作间隔(ms)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 节点流程区
            if (actions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("空白流程图\n点击\"添加节点\"开始构建", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 开始节点
                    item {
                        FlowNodeStart()
                        FlowArrow()
                    }

                    itemsIndexed(actions) { index, action ->
                        // 计算循环层级 (缩进)
                        val depth = actions.take(index).count { it.type == ActionType.REPEAT_START } -
                                actions.take(index).count { it.type == ActionType.REPEAT_END }

                        FlowActionNode(
                            index = index,
                            action = action,
                            depth = depth.coerceAtLeast(0),
                            onEdit = { editingIndex = index },
                            onDelete = { actions.removeAt(index) },
                            onMoveUp = {
                                if (index > 0) {
                                    val item = actions.removeAt(index)
                                    actions.add(index - 1, item)
                                }
                            },
                            onMoveDown = {
                                if (index < actions.size - 1) {
                                    val item = actions.removeAt(index)
                                    actions.add(index + 1, item)
                                }
                            }
                        )
                        FlowArrow()
                    }

                    // 结束节点
                    item {
                        FlowNodeEnd()
                    }
                }
            }
        }
    }

    // 添加节点菜单
    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("添加节点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AddNodeItem(Icons.Default.TouchApp, "点击") {
                        actions.add(ScriptAction(type = ActionType.TAP, x = 500f, y = 1000f))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Swipe, "滑动") {
                        actions.add(ScriptAction(type = ActionType.SWIPE, x = 100f, y = 1000f, x2 = 900f, y2 = 1000f, duration = 300L))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.TouchApp, "长按") {
                        actions.add(ScriptAction(type = ActionType.LONG_PRESS, x = 500f, y = 1000f, duration = 500L))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Timer, "延迟") {
                        actions.add(ScriptAction(type = ActionType.DELAY, delay = 500L))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Shuffle, "随机点击") {
                        actions.add(ScriptAction(type = ActionType.RANDOM_TAP, x = 300f, y = 600f, x2 = 700f, y2 = 1400f))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Search, "找字点击") {
                        actions.add(ScriptAction(type = ActionType.FIND_TEXT, text = "确定"))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Repeat, "循环开始") {
                        actions.add(ScriptAction(type = ActionType.REPEAT_START, delay = 3L))
                        showAddMenu = false
                    }
                    AddNodeItem(Icons.Default.Repeat, "循环结束") {
                        actions.add(ScriptAction(type = ActionType.REPEAT_END))
                        showAddMenu = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMenu = false }) { Text("取消") }
            }
        )
    }

    // 编辑节点对话框
    editingIndex?.let { idx ->
        val action = actions[idx]
        if (action.type == ActionType.REPEAT_START) {
            // 循环节点: 编辑次数
            var countText by remember { mutableStateOf(action.delay.toString()) }
            AlertDialog(
                onDismissRequest = { editingIndex = null },
                title = { Text("编辑循环") },
                text = {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter { c -> c.isDigit() } },
                        label = { Text("循环次数") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        actions[idx] = action.copy(delay = (countText.toIntOrNull() ?: 1).toLong())
                        editingIndex = null
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { editingIndex = null }) { Text("取消") }
                }
            )
        } else {
            EditActionDialog(
                action = action,
                onDismiss = { editingIndex = null },
                onSave = {
                    actions[idx] = it
                    editingIndex = null
                }
            )
        }
    }

    // 保存确认
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("保存脚本") },
            text = {
                Column {
                    Text("名称: $name")
                    Text("节点数: ${actions.size}")
                    Text("外层循环: ${repeatCount.ifEmpty { "1" }} 次")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val saved = Script(
                        id = script?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        actions = actions.toMutableList(),
                        repeatCount = repeatCount.toIntOrNull() ?: 1,
                        intervalBetweenActions = actionInterval.toLongOrNull() ?: 100L,
                        intervalBetweenRepeats = script?.intervalBetweenRepeats ?: 500L,
                        speedFactor = script?.speedFactor ?: 1.0f
                    )
                    onSave(saved)
                    showSaveConfirm = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 开始节点 */
@Composable
private fun FlowNodeStart() {
    FlowNodeBase(
        icon = Icons.Default.PlayArrow,
        title = "开始",
        subtitle = "脚本入口",
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        indent = 0
    )
}

/** 结束节点 */
@Composable
private fun FlowNodeEnd() {
    FlowNodeBase(
        icon = Icons.Default.Stop,
        title = "结束",
        subtitle = "脚本出口",
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        indent = 0
    )
}

/** 箭头连接线 */
@Composable
private fun FlowArrow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 动作节点卡片 */
@Composable
private fun FlowActionNode(
    index: Int,
    action: ScriptAction,
    depth: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (icon, title, subtitle, isLoop) = describeAction(action)

    FlowNodeBase(
        icon = icon,
        title = title,
        subtitle = subtitle,
        container = if (isLoop)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurface,
        indent = depth
    ) {
        // 序号
        Text(
            text = "#${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp)
        )
        // 操作按钮
        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 动作描述 */
private fun describeAction(action: ScriptAction): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String> {
    return when (action.type) {
        ActionType.TAP -> Triple(Icons.Default.TouchApp, "点击", "(${action.x.toInt()}, ${action.y.toInt()})")
        ActionType.SWIPE -> Triple(Icons.Default.Swipe, "滑动", "(${action.x.toInt()},${action.y.toInt()})→(${action.x2.toInt()},${action.y2.toInt()}) ${action.duration}ms")
        ActionType.LONG_PRESS -> Triple(Icons.Default.TouchApp, "长按", "(${action.x.toInt()}, ${action.y.toInt()}) ${action.duration}ms")
        ActionType.DELAY -> Triple(Icons.Default.Timer, "延迟", "${action.delay}ms")
        ActionType.RANDOM_TAP -> Triple(Icons.Default.Shuffle, "随机点击", "区域(${action.x.toInt()},${action.y.toInt()})~(${action.x2.toInt()},${action.y2.toInt()})")
        ActionType.FIND_TEXT -> Triple(Icons.Default.Search, "找字点击", "\"${action.text}\"")
        ActionType.REPEAT_START -> Triple(Icons.Default.Repeat, "循环开始", "重复 ${action.delay.toInt()} 次")
        ActionType.REPEAT_END -> Triple(Icons.Default.Repeat, "循环结束", "返回循环起点")
    }
}

/** 节点基础布局 (带缩进) */
@Composable
private fun FlowNodeBase(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    indent: Int,
    extra: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 20).dp)
    ) {
        // 循环缩进竖线
        if (indent > 0) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                extra?.invoke()
            }
        }
    }
}

/** 添加节点菜单项 */
@Composable
private fun AddNodeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        ListItem(
            headlineContent = { Text(label) },
            leadingContent = { Icon(icon, contentDescription = null) }
        )
    }
}
