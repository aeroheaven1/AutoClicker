package com.autoclicker.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoclicker.app.AutoClickerApp
import com.autoclicker.app.MainActivity
import com.autoclicker.app.R
import com.autoclicker.app.data.ActionType
import com.autoclicker.app.data.Script
import com.autoclicker.app.data.ScriptAction
import kotlinx.coroutines.*

/**
 * 自动化点击服务
 * 负责执行脚本中的点击、滑动等动作
 */
class ClickService : Service() {

    companion object {
        const val TAG = "ClickService"
        const val ACTION_START = "com.autoclicker.START"
        const val ACTION_STOP = "com.autoclicker.STOP"
        const val ACTION_EXECUTE = "com.autoclicker.EXECUTE"
        const val EXTRA_SCRIPT_JSON = "script_json"
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_X2 = "x2"
        const val EXTRA_Y2 = "y2"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_REPEAT = "repeat"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var currentScript: Script? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runner: ICommandRunner? = null
    private var executionJob: Job? = null
    private var ocrEngine: OcrEngine? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val scriptJson = intent.getStringExtra(EXTRA_SCRIPT_JSON)
                if (scriptJson != null) {
                    val script = Script.fromJson(scriptJson)
                    startScript(script)
                }
            }
            ACTION_STOP -> {
                stopScript()
            }
            ACTION_EXECUTE -> {
                val type = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: return START_NOT_STICKY
                val x = intent.getFloatExtra(EXTRA_X, 0f)
                val y = intent.getFloatExtra(EXTRA_Y, 0f)
                val x2 = intent.getFloatExtra(EXTRA_X2, 0f)
                val y2 = intent.getFloatExtra(EXTRA_Y2, 0f)
                val duration = intent.getLongExtra(EXTRA_DURATION, 100L)
                val repeat = intent.getIntExtra(EXTRA_REPEAT, 1)
                executeAction(type, x, y, x2, y2, duration, repeat)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ClickService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AutoClickerApp.CHANNEL_ID)
            .setContentTitle("连点器运行中")
            .setContentText("正在执行自动化脚本...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun startScript(script: Script) {
        if (isRunning) {
            stopScript()
        }
        isRunning = true
        currentScript = script
        startForegroundNotification()

        // 选择最佳权限方式
        val shizukuRunner = ShizukuRunner(this)
        val rootRunner = RootRunner()
        runner = when {
            shizukuRunner.isAvailable() -> {
                Log.d(TAG, "Using Shizuku")
                shizukuRunner
            }
            rootRunner.isAvailable() -> {
                Log.d(TAG, "Using Root")
                rootRunner
            }
            else -> {
                Log.e(TAG, "No permission available")
                isRunning = false
                stopSelf()
                return
            }
        }

        executionJob = serviceScope.launch {
            // 创建 OCR 引擎 (用于 FIND_TEXT 动作)
            ocrEngine = OcrEngine(applicationContext, runner!!)
            executeScript(script)
        }
    }

    fun stopScript() {
        executionJob?.cancel()
        executionJob = null
        isRunning = false
        currentScript = null
        runner = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun executeScript(script: Script) {
        val r = runner ?: return
        var count = 0
        val maxRepeats = script.repeatCount
        val speed = script.speedFactor.coerceIn(0.25f, 5f)
        val actionInterval = (script.intervalBetweenActions / speed).toLong().coerceAtLeast(0)
        val repeatInterval = (script.intervalBetweenRepeats / speed).toLong().coerceAtLeast(0)

        while (isRunning && (maxRepeats == 0 || count < maxRepeats)) {
            executeActions(r, script.actions, speed, actionInterval)
            count++
            if (repeatInterval > 0 && (maxRepeats == 0 || count < maxRepeats)) {
                delay(repeatInterval)
            }
        }

        // 执行完毕
        isRunning = false
        currentScript = null
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 执行动作列表, 支持 REPEAT_START/REPEAT_END 嵌套循环
     */
    private suspend fun executeActions(
        runner: ICommandRunner,
        actions: List<ScriptAction>,
        speed: Float,
        actionInterval: Long
    ) {
        // 循环栈: 保存循环开始位置和剩余次数
        val loopStack = ArrayDeque<LoopFrame>()
        var index = 0

        while (index < actions.size && isRunning) {
            val action = actions[index]
            when (action.type) {
                ActionType.REPEAT_START -> {
                    val count = action.delay.toInt().coerceAtLeast(1)
                    loopStack.addLast(LoopFrame(index, count))
                    Log.d(TAG, "循环开始 x$count (位置 $index)")
                }
                ActionType.REPEAT_END -> {
                    if (loopStack.isNotEmpty()) {
                        val frame = loopStack.last()
                        frame.remaining--
                        if (frame.remaining > 0) {
                            index = frame.startIndex // 回到循环开始
                            continue
                        } else {
                            loopStack.removeLast()
                            Log.d(TAG, "循环结束")
                        }
                    }
                }
                else -> {
                    executeSingleAction(runner, action, speed)
                    if (actionInterval > 0) {
                        delay(actionInterval)
                    }
                }
            }
            index++
        }
    }

    /** 循环栈帧 */
    private class LoopFrame(
        val startIndex: Int,
        var remaining: Int
    )

    private suspend fun executeSingleAction(runner: ICommandRunner, action: ScriptAction, speed: Float = 1.0f) {
        // DELAY 类型: 仅等待
        if (action.type == ActionType.DELAY) {
            if (action.delay > 0) {
                delay((action.delay / speed).toLong().coerceAtLeast(0))
            }
            return
        }

        // FIND_TEXT 类型: 截屏 OCR 找字点击
        if (action.type == ActionType.FIND_TEXT) {
            executeFindText(runner, action)
            return
        }

        val cmd = buildActionCommand(action)
        if (cmd.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runner.execAsync(cmd)
            }
            // 等待动作执行完成 (滑动/长按需要时间)
            if (action.duration > 0) {
                delay((action.duration / speed).toLong().coerceAtLeast(0) + 50)
            }
        }
    }

    /**
     * 文字识别找字点击
     */
    private suspend fun executeFindText(runner: ICommandRunner, action: ScriptAction) {
        val text = action.text.trim()
        if (text.isEmpty()) return

        val engine = ocrEngine ?: return
        // 未下载模型则跳过
        if (!engine.isModelReady()) {
            Log.e(TAG, "FIND_TEXT: OCR 模型未下载, 跳过 [$text]")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val bmp = engine.captureScreen() ?: return@withContext
                val words = engine.recognizeWords(bmp)
                val word = engine.findWord(words, text)
                if (word != null) {
                    Log.d(TAG, "FIND_TEXT 找到 [$text] at (${word.centerX},${word.centerY})")
                    runner.exec("input tap ${word.centerX} ${word.centerY}")
                } else {
                    Log.w(TAG, "FIND_TEXT 未找到 [$text]")
                }
                bmp.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "executeFindText error: ${e.message}")
            }
        }
    }

    private fun executeAction(
        type: String,
        x: Float,
        y: Float,
        x2: Float,
        y2: Float,
        duration: Long,
        repeat: Int
    ) {
        val shizukuRunner = ShizukuRunner(this)
        val rootRunner = RootRunner()
        val r = when {
            shizukuRunner.isAvailable() -> shizukuRunner
            rootRunner.isAvailable() -> rootRunner
            else -> return
        }

        serviceScope.launch {
            val actionType = try {
                ActionType.valueOf(type)
            } catch (e: Exception) {
                ActionType.TAP
            }

            val action = ScriptAction(
                type = actionType,
                x = x, y = y,
                x2 = x2, y2 = y2,
                duration = duration
            )

            repeat(repeat.coerceAtLeast(1)) {
                executeSingleAction(r, action)
                delay(100)
            }
        }
    }

    private fun buildActionCommand(action: ScriptAction): String {
        return when (action.type) {
            ActionType.TAP -> {
                "input tap ${action.x.toInt()} ${action.y.toInt()}"
            }
            ActionType.RANDOM_TAP -> {
                // 在区域内随机取点
                val minX = minOf(action.x, action.x2)
                val maxX = maxOf(action.x, action.x2)
                val minY = minOf(action.y, action.y2)
                val maxY = maxOf(action.y, action.y2)
                val rx = if (maxX > minX) (minX + Math.random() * (maxX - minX)).toInt() else minX.toInt()
                val ry = if (maxY > minY) (minY + Math.random() * (maxY - minY)).toInt() else minY.toInt()
                "input tap $rx $ry"
            }
            ActionType.SWIPE -> {
                "input swipe ${action.x.toInt()} ${action.y.toInt()} ${action.x2.toInt()} ${action.y2.toInt()} ${action.duration}"
            }
            ActionType.LONG_PRESS -> {
                "input swipe ${action.x.toInt()} ${action.y.toInt()} ${action.x.toInt()} ${action.y.toInt()} ${action.duration}"
            }
            ActionType.FIND_TEXT -> {
                // FIND_TEXT 在 executeFindText 中处理
                ""
            }
            ActionType.REPEAT_START, ActionType.REPEAT_END -> {
                // 循环控制在 executeActions 中处理
                ""
            }
            ActionType.DELAY -> {
                // DELAY 类型在 executeSingleAction 的 delay 中处理
                ""
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        isRunning = false
        currentScript = null
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
