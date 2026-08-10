package com.autoclicker.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.autoclicker.app.data.Script
import com.autoclicker.app.data.ScriptRepository
import com.autoclicker.app.service.ClickService
import com.autoclicker.app.service.RootRunner
import com.autoclicker.app.service.ShizukuRunner
import com.autoclicker.app.ui.screens.HomeScreen
import com.autoclicker.app.ui.screens.RecordScreen
import com.autoclicker.app.ui.screens.SettingsScreen
import com.autoclicker.app.ui.theme.AutoClickerTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private lateinit var scriptRepository: ScriptRepository
    private val scripts = mutableStateListOf<Script>()
    private var permissionType by mutableStateOf("检测中...")
    private var runningScriptId by mutableStateOf<String?>(null)

    // Shizuku 权限请求
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        runOnUiThread {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已获取", Toast.LENGTH_SHORT).show()
                updatePermissionStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scriptRepository = ScriptRepository(applicationContext)

        // 添加 Shizuku 权限监听
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        // 检查权限
        updatePermissionStatus()
        checkOverlayPermission()

        // 加载脚本
        lifecycleScope.launch {
            scriptRepository.scriptsFlow.collect { list ->
                scripts.clear()
                scripts.addAll(list)
            }
        }

        // 检查是否有正在运行的服务
        runningScriptId = ClickService.currentScript?.id

        setContent {
            AutoClickerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }

    @Composable
    fun MainApp() {
        var currentScreen by remember { mutableStateOf("home") }

        when (currentScreen) {
            "home" -> HomeScreen(
                scripts = scripts,
                runningScriptId = runningScriptId,
                permissionType = permissionType,
                onPlayScript = { script -> handlePlayScript(script) },
                onEditScript = { script -> handleEditScript(script) },
                onDeleteScript = { script -> handleDeleteScript(script) },
                onNavigateToRecord = { currentScreen = "record" },
                onNavigateToSettings = { currentScreen = "settings" },
                onQuickTap = { x, y, duration, repeat ->
                    executeQuickAction("TAP", x, y, 0f, 0f, duration, repeat)
                },
                onQuickSwipe = { x1, y1, x2, y2, duration, repeat ->
                    executeQuickAction("SWIPE", x1, y1, x2, y2, duration, repeat)
                }
            )
            "record" -> RecordScreen(
                onBack = { currentScreen = "home" },
                onSaveScript = { script ->
                    lifecycleScope.launch {
                        scriptRepository.saveScript(script)
                        Toast.makeText(this@MainActivity, "脚本已保存", Toast.LENGTH_SHORT).show()
                        currentScreen = "home"
                    }
                }
            )
            "settings" -> SettingsScreen(
                onBack = { currentScreen = "home" },
                permissionType = permissionType,
                onRefreshPermission = {
                    updatePermissionStatus()
                    Toast.makeText(this@MainActivity, "权限状态: $permissionType", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun handlePlayScript(script: Script) {
        if (ClickService.isRunning) {
            stopClickService()
            runningScriptId = null
        } else {
            if (!hasAnyPermission()) {
                requestPermissions()
                return
            }
            startClickService(script)
            runningScriptId = script.id
        }
    }

    private fun handleEditScript(script: Script) {
        val newRepeat = if (script.repeatCount == 0) 1 else 0
        val updated = script.copy(
            repeatCount = newRepeat,
            updatedAt = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            scriptRepository.saveScript(updated)
        }
        Toast.makeText(
            this,
            if (newRepeat == 0) "已设为无限循环" else "已设为单次执行",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleDeleteScript(script: Script) {
        if (script.id == runningScriptId) {
            stopClickService()
            runningScriptId = null
        }
        lifecycleScope.launch {
            scriptRepository.deleteScript(script.id)
            Toast.makeText(this@MainActivity, "脚本已删除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeQuickAction(
        type: String,
        x: Float, y: Float,
        x2: Float, y2: Float,
        duration: Long,
        repeat: Int
    ) {
        if (!hasAnyPermission()) {
            requestPermissions()
            return
        }

        val intent = Intent(this, ClickService::class.java).apply {
            action = ClickService.ACTION_EXECUTE
            putExtra(ClickService.EXTRA_ACTION_TYPE, type)
            putExtra(ClickService.EXTRA_X, x)
            putExtra(ClickService.EXTRA_Y, y)
            putExtra(ClickService.EXTRA_X2, x2)
            putExtra(ClickService.EXTRA_Y2, y2)
            putExtra(ClickService.EXTRA_DURATION, duration)
            putExtra(ClickService.EXTRA_REPEAT, repeat)
        }
        startService(intent)
        Toast.makeText(this, "正在执行...", Toast.LENGTH_SHORT).show()
    }

    private fun startClickService(script: Script) {
        val intent = Intent(this, ClickService::class.java).apply {
            action = ClickService.ACTION_START
            putExtra(ClickService.EXTRA_SCRIPT_JSON, Script.toJson(script))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopClickService() {
        val intent = Intent(this, ClickService::class.java).apply {
            action = ClickService.ACTION_STOP
        }
        startService(intent)
    }

    private fun hasAnyPermission(): Boolean {
        return ShizukuRunner(applicationContext).isAvailable() || RootRunner().isAvailable()
    }

    private fun updatePermissionStatus() {
        val shizuku = ShizukuRunner(applicationContext)
        val root = RootRunner()
        permissionType = when {
            shizuku.isAvailable() -> "Shizuku ✓"
            root.isAvailable() -> "Root ✓"
            else -> "未授权 ✗"
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 悬浮窗权限稍后按需请求
        }
    }

    private fun requestPermissions() {
        // 先尝试 Shizuku
        if (!ShizukuRunner(applicationContext).isAvailable()) {
            try {
                Shizuku.requestPermission(0)
            } catch (e: Exception) {
                // Shizuku 不可用
            }
        }

        // 检查 Root
        if (!hasAnyPermission()) {
            Toast.makeText(
                this,
                "请安装并运行 Shizuku，或获取 Root 权限后使用\n\n" +
                        "Shizuku 下载: https://shizuku.rikka.app",
                Toast.LENGTH_LONG
            ).show()
        }

        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        runningScriptId = ClickService.currentScript?.id
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }
}
