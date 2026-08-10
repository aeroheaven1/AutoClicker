package com.autoclicker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    permissionType: String,
    onRefreshPermission: () -> Unit
) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 权限状态
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("权限状态", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (permissionType.contains("✓"))
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (permissionType.contains("✓"))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = permissionType,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onRefreshPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("刷新权限状态")
                        }
                    }
                }
            }

            // 使用说明
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("使用说明", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))

                        InfoItem(
                            icon = Icons.Default.TouchApp,
                            title = "点击操作",
                            description = "通过 input tap 命令模拟屏幕点击"
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoItem(
                            icon = Icons.Default.Swipe,
                            title = "滑动操作",
                            description = "通过 input swipe 命令模拟滑动，可设置滑动时长"
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoItem(
                            icon = Icons.Default.FiberManualRecord,
                            title = "录制功能",
                            description = "手动添加动作序列，或通过真实触摸录制完整操作流程"
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoItem(
                            icon = Icons.Default.Repeat,
                            title = "循环执行",
                            description = "支持设置重复次数或无限循环执行脚本"
                        )
                    }
                }
            }

            // 坐标查看提示
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "如何获取屏幕坐标",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "1. 进入系统设置 → 开发者选项\n" +
                                    "2. 开启「指针位置」\n" +
                                    "3. 屏幕顶部会显示实时坐标信息\n" +
                                    "4. 触摸屏幕即可看到具体坐标值",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 关于
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAboutDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("关于连点器", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "版本 1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 关于对话框
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("关于连点器") },
            text = {
                Column {
                    Text("版本: 1.0.0")
                    Spacer(Modifier.height(4.dp))
                    Text("基于 Material Design 3 设计")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支持权限方式:\n" +
                                "• Shizuku - 通过 Shizuku API 执行系统级命令\n" +
                                "• Root - 通过 su 获取 root 权限执行命令\n\n" +
                                "功能:\n" +
                                "• 点击模拟 (input tap)\n" +
                                "• 滑动模拟 (input swipe)\n" +
                                "• 长按模拟\n" +
                                "• 操作录制与回放\n" +
                                "• 无限循环执行",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
