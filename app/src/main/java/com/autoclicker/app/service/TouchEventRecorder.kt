package com.autoclicker.app.service

import android.util.Log
import com.autoclicker.app.data.ActionType
import com.autoclicker.app.data.ScriptAction
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 触摸事件录制器
 *
 * 通过 getevent 命令捕获真实屏幕触摸, 支持:
 * - 点击 (TAP)
 * - 滑动 (SWIPE)
 * - 长按 (LONG_PRESS)
 * - 动作间延迟 (DELAY)
 *
 * 使用方式:
 * 1. [startRecording] 开始 (自动探测触摸设备 + 启动 getevent 流)
 * 2. 用户在屏幕上操作
 * 3. [stopRecording] 停止, [recordedActions] 为完整脚本
 */
class TouchEventRecorder(
    private val commandRunner: ICommandRunner
) {
    companion object {
        private const val TAG = "TouchRecorder"
        private const val DEFAULT_DEVICE = "/dev/input/event1"

        // 手势判定阈值
        private const val SWIPE_DISTANCE_PX = 24f       // 滑动最小距离
        private const val LONG_PRESS_MS = 500L          // 长按最小时长
    }

    /** 录制状态回调 */
    interface RecorderCallback {
        fun onError(message: String)
        fun onRecorded(action: ScriptAction)
    }

    var callback: RecorderCallback? = null

    private var recordingJob: Job? = null

    @Volatile
    private var isRecording = false

    // 结果动作列表 (CopyOnWriteArrayList 供 UI 线程读取)
    val recordedActions = CopyOnWriteArrayList<ScriptAction>()

    /** 是否正在录制 */
    fun isRecording(): Boolean = isRecording

    /**
     * 查找触摸输入设备路径
     */
    fun findTouchDevice(): String {
        return try {
            val devices = commandRunner.exec("ls /dev/input/event* 2>/dev/null")
                .split("\n")
                .filter { it.startsWith("/dev/input/event") }
                .map { it.trim() }
            Log.d(TAG, "Input devices: $devices")

            // 优先检测含触摸的设备
            for (dev in devices) {
                val info = commandRunner.exec("getevent -p $dev 2>/dev/null | head -30")
                if (info.contains("ABS_MT_POSITION_X", ignoreCase = true) ||
                    info.contains("BTN_TOUCH", ignoreCase = true)) {
                    Log.d(TAG, "Touch device found: $dev")
                    return dev
                }
            }

            // 回退: 常见设备
            if ("/dev/input/event1" in devices) "/dev/input/event1"
            else if ("/dev/input/event0" in devices) "/dev/input/event0"
            else devices.firstOrNull() ?: DEFAULT_DEVICE
        } catch (e: Exception) {
            Log.e(TAG, "findTouchDevice error: ${e.message}")
            DEFAULT_DEVICE
        }
    }

    /**
     * 开始录制
     * @param scope 协程作用域
     * @return 是否成功开始
     */
    fun startRecording(scope: CoroutineScope): Boolean {
        if (isRecording) return false

        recordedActions.clear()
        isRecording = true

        val device = findTouchDevice()
        Log.d(TAG, "Recording from device: $device")

        // 启动 getevent 流式命令 (带标签 + 时间戳)
        commandRunner.startStream("getevent -lt $device 2>/dev/null")

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val gestureBuilder = GestureBuilder()

                while (isRecording) {
                    // 读取新增事件
                    val chunk = commandRunner.readStream()
                    if (chunk.isNotEmpty()) {
                        chunk.lineSequence().forEach { line ->
                            if (isRecording) {
                                gestureBuilder.feed(line)
                            }
                        }
                    } else {
                        // 无新数据, 稍等
                        delay(20)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
                callback?.onError("录制出错: ${e.message}")
            } finally {
                commandRunner.stopStream()
            }
        }

        return true
    }

    /**
     * 停止录制, 结束当前未完成的手势
     */
    fun stopRecording(): List<ScriptAction> {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        commandRunner.stopStream()
        return recordedActions.toList()
    }

    /**
     * 手势构建器: 解析 getevent 行, 累积手势状态, 完成时输出动作
     */
    private inner class GestureBuilder {

        // 当前手势状态
        private var touching = false
        private var downTimeUs = 0L
        private var prevActionEndUs = 0L

        // 当前轨迹
        private var startX = -1f
        private var startY = -1f
        private var lastX = -1f
        private var lastY = -1f
        private var maxDistance = 0f

        /**
         * 解析并处理一行 getevent 输出
         * 格式(带标签): [ 123.456789] /dev/input/event1: EV_ABS       ABS_MT_POSITION_X 00000123
         */
        fun feed(line: String) {
            val ev = parseLine(line) ?: return
            val nowUs = ev.timestampUs

            when {
                // 手指按下 (BTN_TOUCH=1 或 TRACKING_ID>=0)
                ev.isBtnTouchDown || ev.isTrackingDown -> {
                    if (!touching) {
                        touching = true
                        downTimeUs = nowUs
                        // 坐标可能先到, 用已缓存的 lastX/lastY 作为起点
                        startX = if (lastX >= 0) lastX else -1f
                        startY = if (lastY >= 0) lastY else -1f
                        maxDistance = 0f
                    }
                }

                // 手指抬起 (BTN_TOUCH=0 或 TRACKING_ID=-1)
                ev.isBtnTouchUp || ev.isTrackingUp -> {
                    if (touching) {
                        touching = false
                        finishGesture(nowUs)
                    }
                }

                // 坐标事件: 始终缓存最新坐标
                ev.x != null -> {
                    lastX = ev.x!!
                    if (touching) {
                        if (startX < 0) startX = ev.x!!
                        if (lastY >= 0) {
                            maxDistance = maxOf(maxDistance, distance(startX, startY, ev.x!!, lastY))
                        }
                    }
                }

                ev.y != null -> {
                    lastY = ev.y!!
                    if (touching) {
                        if (startY < 0) startY = ev.y!!
                        if (lastX >= 0) {
                            maxDistance = maxOf(maxDistance, distance(startX, startY, lastX, ev.y!!))
                        }
                    }
                }

                // 其他事件忽略
            }
        }

        /** 手势结束: 判定类型并添加动作 */
        private fun finishGesture(endUs: Long) {
            val durationMs = (endUs - downTimeUs) / 1000
            val startUs = downTimeUs

            // 动作间延迟: 从上一个动作结束到现在
            if (prevActionEndUs > 0) {
                val gapMs = (startUs - prevActionEndUs) / 1000
                if (gapMs > 80) {
                    recordedActions.add(
                        ScriptAction(type = ActionType.DELAY, delay = gapMs.coerceAtLeast(0))
                    )
                }
            }
            prevActionEndUs = endUs

            // 根据距离和时长判定手势
            val action: ScriptAction = when {
                maxDistance >= SWIPE_DISTANCE_PX -> {
                    // 滑动
                    ScriptAction(
                        type = ActionType.SWIPE,
                        x = startX, y = startY,
                        x2 = lastX, y2 = lastY,
                        duration = durationMs.coerceAtLeast(50)
                    )
                }
                durationMs >= LONG_PRESS_MS -> {
                    // 长按
                    ScriptAction(
                        type = ActionType.LONG_PRESS,
                        x = startX, y = startY,
                        duration = durationMs
                    )
                }
                else -> {
                    // 点击
                    ScriptAction(
                        type = ActionType.TAP,
                        x = startX, y = startY
                    )
                }
            }

            if (action.x > 0 && action.y > 0) {
                recordedActions.add(action)
                Log.d(TAG, "Recorded: $action")
                callback?.onRecorded(action)
            }

            // 重置轨迹
            startX = -1f; startY = -1f
            lastX = -1f; lastY = -1f
            maxDistance = 0f
        }

        private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x1 - x2
            val dy = y1 - y2
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }

    /** 解析一行 getevent -lt 输出 */
    private fun parseLine(line: String): ParsedEvent? {
        return try {
            val timeMatch = Regex("\\[\\s*([\\d.]+)\\]").find(line) ?: return null
            val timestampUs = (timeMatch.groupValues[1].toDouble() * 1_000_000).toLong()

            // 带标签格式: EV_ABS ABS_MT_POSITION_X 00000123
            val labelMatch = Regex("EV_\\w+\\s+(\\w+)\\s+([0-9a-fA-F]+)").find(line)
            if (labelMatch != null) {
                val code = labelMatch.groupValues[1]
                val value = java.lang.Long.parseLong(labelMatch.groupValues[2], 16)
                return ParsedEvent(timestampUs, code, value)
            }

            // 数字格式: 0003 0035 00000123
            val hexMatch = Regex(":\\s*([0-9a-fA-F]+)\\s+([0-9a-fA-F]+)\\s+([0-9a-fA-F]+)").find(line)
            if (hexMatch != null) {
                val type = hexMatch.groupValues[1].toInt(16)
                val code = hexMatch.groupValues[2].toInt(16)
                val value = java.lang.Long.parseLong(hexMatch.groupValues[3], 16)
                val label = when {
                    type == 0x03 && code == 0x35 -> "ABS_MT_POSITION_X"
                    type == 0x03 && code == 0x36 -> "ABS_MT_POSITION_Y"
                    type == 0x01 && code == 0x14a -> "BTN_TOUCH"
                    type == 0x03 && code == 0x39 -> "ABS_MT_TRACKING_ID"
                    else -> "OTHER"
                }
                return ParsedEvent(timestampUs, label, value)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 解析后的事件 */
    private class ParsedEvent(
        val timestampUs: Long,
        val code: String,
        val value: Long
    ) {
        val isBtnTouchDown: Boolean
            get() = code.contains("BTN_TOUCH") && value == 1L
        val isBtnTouchUp: Boolean
            get() = code.contains("BTN_TOUCH") && value == 0L
        val isTrackingDown: Boolean
            get() = code.contains("ABS_MT_TRACKING_ID") && value >= 0 && value < 0x80000000L
        val isTrackingUp: Boolean
            get() = code.contains("ABS_MT_TRACKING_ID") && (value < 0 || value >= 0x80000000L)
        val x: Float?
            get() = if (code.contains("ABS_MT_POSITION_X") || code.contains("ABS_X")) value.toFloat() else null
        val y: Float?
            get() = if (code.contains("ABS_MT_POSITION_Y") || code.contains("ABS_Y")) value.toFloat() else null
    }
}
