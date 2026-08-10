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
import com.autoclicker.app.data.ScriptAction
import com.autoclicker.app.data.ScriptRepository
import com.autoclicker.app.service.ClickService
import com.autoclicker.app.service.OcrEngine
import com.autoclicker.app.service.RootRunner
import com.autoclicker.app.service.ShizukuRunner
import com.autoclicker.app.service.TouchEventRecorder
import com.autoclicker.app.ui.components.CoordinatePicker
import com.autoclicker.app.ui.components.FloatingControl
import com.autoclicker.app.ui.screens.CoordinatePickerActions
import com.autoclicker.app.ui.screens.FlowEditScreen
import com.autoclicker.app.ui.screens.HomeScreen
import com.autoclicker.app.ui.screens.OcrScreen
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

    // 悬浮窗
    private var floatingControl: FloatingControl? = null

    // 屏幕录制状态
    private var isRecording = false
    private var recorder: TouchEventRecorder? = null

    // 坐标拾取器
    private var coordinatePicker: CoordinatePicker? = null

    // OCR 引擎
    private var ocrEngine: OcrEngine? = null

    // 流程图编辑状态
    private var editingScript by mutableStateOf<Script?>(null)

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已获取", Toast.LENGTH_SHORT).show()
            showFloatingControl()
        }
    }

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
                onEditScript = { script ->
                    editingScript = script
                    currentScreen = "flow"
                },
                onDeleteScript = { script -> handleDeleteScript(script) },
                onNavigateToRecord = { currentScreen = "record" },
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToOcr = { currentScreen = "ocr" },
                onNavigateToFlow = {
                    editingScript = null
                    currentScreen = "flow"
                },
                onQuickTap = { x, y, duration, repeat ->
                    executeQuickAction("TAP", x, y, 0f, 0f, duration, repeat)
                },
                onQuickSwipe = { x1, y1, x2, y2, duration, repeat ->
                    executeQuickAction("SWIPE", x1, y1, x2, y2, duration, repeat)
                },
                pickerActions = CoordinatePickerActions(
                    pickTap = { cb -> startCoordinatePick(cb) },
                    pickSwipeStart = { cb -> startCoordinatePick(cb) },
                    pickSwipeEnd = { cb -> startCoordinatePick(cb) }
                )
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
            "ocr" -> OcrScreen(
                onBack = { currentScreen = "home" },
                modelReady = { getOcrEngine()?.isModelReady() == true },
                downloadModel = { getOcrEngine()?.ensureModel() ?: false },
                recognize = ocrRecognize@{
                    val engine = getOcrEngine() ?: return@ocrRecognize emptyList()
                    val (_, words) = engine.captureAndRecognize()
                    words
                },
                onWordClicked = { word -> handleWordClicked(word) }
            )
            "flow" -> FlowEditScreen(
                script = editingScript,
                onBack = { currentScreen = "home" },
                onSave = { saved ->
                    lifecycleScope.launch {
                        scriptRepository.saveScript(saved)
                        Toast.makeText(this@MainActivity, "✓ 脚本已保存: ${saved.name}", Toast.LENGTH_SHORT).show()
                        currentScreen = "home"
                    }
                }
            )
        }
    }

    /** 获取或创建 OCR 引擎 */
    private fun getOcrEngine(): OcrEngine? {
        if (ocrEngine == null) {
            val shizuku = ShizukuRunner(applicationContext)
            val root = RootRunner()
            val runner = when {
                shizuku.isAvailable() -> shizuku
                root.isAvailable() -> root
                else -> return null
            }
            ocrEngine = OcrEngine(applicationContext, runner)
        }
        return ocrEngine
    }

    /** 处理识别出的文字: 立即点击或创建找字脚本 */
    private fun handleWordClicked(word: com.autoclicker.app.service.OcrWord) {
        val options = arrayOf("立即点击该文字", "创建\"找字点击\"脚本")
        android.app.AlertDialog.Builder(this)
            .setTitle("文字: ${word.text}")
            .setMessage("位置: (${word.centerX}, ${word.centerY})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 立即点击
                        executeQuickAction("TAP", word.centerX.toFloat(), word.centerY.toFloat(), 0f, 0f, 100L, 1)
                    }
                    1 -> {
                        // 创建找字点击脚本
                        val script = Script(
                            name = "找字点击: ${word.text}",
                            actions = mutableListOf(
                                ScriptAction(type = com.autoclicker.app.data.ActionType.FIND_TEXT, text = word.text)
                            ),
                            repeatCount = 1,
                            intervalBetweenActions = 100,
                            intervalBetweenRepeats = 500
                        )
                        lifecycleScope.launch {
                            scriptRepository.saveScript(script)
                            Toast.makeText(this@MainActivity, "✓ 已创建脚本: ${script.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handlePlayScript(script: Script) {
        if (ClickService.isRunning) {
            stopClickService()
            runningScriptId = null
            floatingControl?.setRunning(false)
        } else {
            if (!hasAnyPermission()) {
                requestPermissions()
                return
            }
            startClickService(script)
            runningScriptId = script.id
            floatingControl?.setRunning(true)
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
        // 检查并申请悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            showFloatingControl()
        }
    }

    /** 显示悬浮球控制 */
    private fun showFloatingControl() {
        if (floatingControl == null) {
            floatingControl = FloatingControl(applicationContext, object : FloatingControl.Callbacks {
                override fun onToggleRun() {
                    runOnUiThread {
                        if (ClickService.isRunning) {
                            // 停止运行
                            stopClickService()
                            runningScriptId = null
                            floatingControl?.setRunning(false)
                        } else if (scripts.isNotEmpty()) {
                            // 执行第一个脚本
                            if (!hasAnyPermission()) {
                                requestPermissions()
                                Toast.makeText(this@MainActivity, "请先授权 Shizuku 或 Root", Toast.LENGTH_SHORT).show()
                                return@runOnUiThread
                            }
                            startClickService(scripts.first())
                            runningScriptId = scripts.first().id
                            floatingControl?.setRunning(true)
                        } else {
                            Toast.makeText(this@MainActivity, "暂无脚本, 请先录制", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onQuickTap() {
                    runOnUiThread {
                        // 打开主界面并弹出快速点击对话框
                        openApp()
                        Toast.makeText(this@MainActivity, "请在主界面配置快速点击", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onQuickSwipe() {
                    runOnUiThread {
                        openApp()
                        Toast.makeText(this@MainActivity, "请在主界面配置快速滑动", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onRecord() {
                    runOnUiThread {
                        openApp()
                        Toast.makeText(this@MainActivity, "请在主界面点击\"录制脚本\"", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onToggleRecord() {
                    runOnUiThread {
                        toggleScreenRecording()
                    }
                }

                override fun onPickCoordinate() {
                    runOnUiThread {
                        startCoordinatePick { x, y ->
                            // 取点后立即执行一次点击
                            Toast.makeText(this@MainActivity, "坐标: ($x, $y) 已执行点击", Toast.LENGTH_SHORT).show()
                            executeQuickAction("TAP", x, y, 0f, 0f, 100L, 1)
                        }
                    }
                }

                override fun onOpenApp() {
                    runOnUiThread { openApp() }
                }
            })
        }
        floatingControl?.show()
    }

    /** 打开主界面 */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    /**
     * 启动屏幕取点模式
     * @param onPicked 取到坐标后的回调
     */
    private fun startCoordinatePick(onPicked: (Float, Float) -> Unit) {
        if (coordinatePicker?.isActive() == true) return
        coordinatePicker?.stop()
        coordinatePicker = CoordinatePicker(applicationContext) { x, y ->
            runOnUiThread {
                onPicked(x, y)
            }
        }
        coordinatePicker?.start(CoordinatePicker.Mode.SINGLE)
        Toast.makeText(this, "🎯 请点击屏幕选取坐标", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换屏幕录制 (悬浮窗控制)
     */
    private fun toggleScreenRecording() {
        if (isRecording) {
            stopScreenRecording()
        } else {
            startScreenRecording()
        }
    }

    /** 开始屏幕录制 */
    private fun startScreenRecording() {
        if (!hasAnyPermission()) {
            requestPermissions()
            return
        }

        // 选择权限执行器
        val shizuku = ShizukuRunner(applicationContext)
        val root = RootRunner()
        val runner = when {
            shizuku.isAvailable() -> shizuku
            root.isAvailable() -> root
            else -> {
                requestPermissions()
                return
            }
        }

        val rec = TouchEventRecorder(runner)
        recorder = rec

        if (rec.startRecording(lifecycleScope)) {
            isRecording = true
            floatingControl?.setRecording(true)
            floatingControl?.setRecordingMode(true)
            Toast.makeText(this, "● 屏幕录制已开始, 请操作屏幕 (点击悬浮球停止)", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "录制启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    /** 停止屏幕录制并保存脚本 */
    private fun stopScreenRecording() {
        val rec = recorder
        if (rec == null) {
            isRecording = false
            return
        }

        val actions = rec.stopRecording()
        recorder = null
        isRecording = false
        floatingControl?.setRecording(false)
        floatingControl?.setRecordingMode(false)

        if (actions.isEmpty()) {
            Toast.makeText(this, "未录到任何操作", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存为脚本
        val now = System.currentTimeMillis()
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val script = Script(
            name = "屏幕录制 $timeStr",
            actions = actions.toMutableList(),
            repeatCount = 1,
            intervalBetweenActions = 0,
            intervalBetweenRepeats = 500
        )

        lifecycleScope.launch {
            scriptRepository.saveScript(script)
            Toast.makeText(this@MainActivity, "✓ 已保存脚本: ${script.name} (${actions.size}个动作)", Toast.LENGTH_LONG).show()
            floatingControl?.updateStatus("已录制 ${actions.size} 个动作")
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
        // 更新悬浮面板状态
        floatingControl?.setRunning(ClickService.isRunning)
        floatingControl?.setRecording(isRecording)
        floatingControl?.updateStatus("权限: $permissionType")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止录制
        if (isRecording) {
            recorder?.stopRecording()
            recorder = null
            isRecording = false
        }
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        floatingControl?.hide()
        floatingControl = null
    }
}
