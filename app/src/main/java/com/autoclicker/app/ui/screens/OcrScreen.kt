package com.autoclicker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoclicker.app.service.OcrWord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onBack: () -> Unit,
    modelReady: () -> Boolean,
    downloadModel: suspend () -> Boolean,
    recognize: suspend () -> List<OcrWord>,
    onWordClicked: (OcrWord) -> Unit
) {
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<OcrWord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var modelState by remember { mutableStateOf(modelReady()) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文字识别 (OCR)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMsg = null
                        words = try {
                            recognize()
                        } catch (e: Exception) {
                            errorMsg = "识别失败: ${e.message}"
                            emptyList()
                        }
                        isLoading = false
                    }
                },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("截屏并识别") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 模型状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (modelState)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (modelState) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (modelState)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (modelState) "OCR 模型已就绪" else "需要下载 OCR 模型 (~10MB)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!modelState) {
                            Text(
                                text = "首次使用需下载中文识别模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!modelState) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isDownloading = true
                                    errorMsg = null
                                    val ok = try {
                                        downloadModel()
                                    } catch (e: Exception) {
                                        errorMsg = "下载失败: ${e.message}"
                                        false
                                    }
                                    modelState = ok
                                    isDownloading = false
                                    if (!ok && errorMsg == null) errorMsg = "模型下载失败"
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            Text(if (isDownloading) "下载中..." else "下载模型")
                        }
                    }
                }
            }

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("🔍 搜索识别到的文字") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在截屏并识别文字...")
                    }
                }
            } else {
                errorMsg?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                if (words.isEmpty() && errorMsg == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "点击右下角\"截屏并识别\"\n识别屏幕上的文字, 点击文字可执行操作",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // 识别结果列表
                    val filtered = if (searchQuery.isBlank()) {
                        words
                    } else {
                        words.filter { it.text.contains(searchQuery, ignoreCase = true) }
                    }

                    Text(
                        text = "识别到 ${words.size} 个文字 (点击文字进行操作)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.text + it.x + it.y }) { word ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onWordClicked(word) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = word.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "(${word.centerX}, ${word.centerY})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
