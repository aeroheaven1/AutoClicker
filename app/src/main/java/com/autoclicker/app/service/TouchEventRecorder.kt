package com.autoclicker.app.service

import android.util.Log
import com.autoclicker.app.data.ActionType
import com.autoclicker.app.data.ScriptAction
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 触摸事件录制器
 * 通过 getevent 命令捕获真实的触摸事件
 */
class TouchEventRecorder(
    private val commandRunner: ICommandRunner
) {
    companion object {
        private const val TAG = "TouchRecorder"
        private const val DEFAULT_DEVICE = "/dev/input/event1" // 通常是触摸屏
    }

    private var recordingJob: Job? = null
    private var isRecording = false

    // 记录的动作列表
    val recordedActions = mutableListOf<ScriptAction>()

    /**
     * 查找触摸输入设备路径
     */
    fun findTouchDevice(): String {
        return try {
            val result = commandRunner.exec("getevent -p 2>/dev/null | grep -A5 'touch\\|Touch\\|touchscreen' | head -20")
            Log.d(TAG, "Touch devices: $result")

            // 默认返回常见的触摸设备路径
            val devices = commandRunner.exec("ls /dev/input/event* 2>/dev/null")
                .split("\n")
                .filter { it.startsWith("/dev/input/event") }
                .map { it.trim() }

            // 尝试查找触摸设备
            for (dev in devices) {
                val info = commandRunner.exec("getevent -p $dev 2>/dev/null | head -20")
                if (info.contains("touch", ignoreCase = true) ||
                    info.contains("ABS_MT_POSITION") ||
                    info.contains("BTN_TOUCH")) {
                    return dev
                }
            }

            // 返回最常见的触摸设备
            if ("/dev/input/event1" in devices) "/dev/input/event1"
            else if ("/dev/input/event0" in devices) "/dev/input/event0"
            else devices.firstOrNull() ?: DEFAULT_DEVICE
        } catch (e: Exception) {
            Log.e(TAG, "Error finding touch device: ${e.message}")
            DEFAULT_DEVICE
        }
    }

    /**
     * 开始录制触摸事件
     */
    fun startRecording(scope: CoroutineScope): Boolean {
        if (isRecording) return false

        recordedActions.clear()
        isRecording = true

        val device = findTouchDevice()
        Log.d(TAG, "Recording from device: $device")

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                // 使用 getevent -lt 获取带时间戳的事件
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "getevent -lt $device 2>/dev/null")
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var lastDownTime = 0L
                var lastX = 0f
                var lastY = 0f
                var isDown = false
                var startTime = System.currentTimeMillis()

                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (!isRecording) return@forEach

                        val parsed = parseGetEventLine(line) ?: return@forEach

                        when {
                            // 手指按下 (BTN_TOUCH DOWN 或 ABS_MT_TRACKING_ID 出现)
                            parsed.key.contains("BTN_TOUCH") && parsed.value == 1 -> {
                                isDown = true
                                lastDownTime = parsed.timestamp
                                startTime = System.currentTimeMillis()
                            }
                            // X坐标
                            parsed.key.contains("ABS_MT_POSITION_X") || parsed.key.contains("ABS_X") -> {
                                lastX = parsed.value.toFloat()
                            }
                            // Y坐标
                            parsed.key.contains("ABS_MT_POSITION_Y") || parsed.key.contains("ABS_Y") -> {
                                lastY = parsed.value.toFloat()
                            }
                            // 手指抬起
                            parsed.key.contains("BTN_TOUCH") && parsed.value == 0 -> {
                                if (isDown && lastX > 0 && lastY > 0) {
                                    val duration = parsed.timestamp - lastDownTime
                                    val delayFromStart = System.currentTimeMillis() - startTime

                                    // 记录上一次动作到现在的延迟
                                    if (recordedActions.isNotEmpty()) {
                                        // 不添加额外延迟，动作间间隔由脚本的 intervalBetweenActions 控制
                                    }

                                    if (duration > 300) {
                                        // 长按
                                        recordedActions.add(
                                            ScriptAction(
                                                type = ActionType.LONG_PRESS,
                                                x = lastX,
                                                y = lastY,
                                                duration = duration
                                            )
                                        )
                                    } else {
                                        // 普通点击
                                        recordedActions.add(
                                            ScriptAction(
                                                type = ActionType.TAP,
                                                x = lastX,
                                                y = lastY
                                            )
                                        )
                                    }
                                    Log.d(TAG, "Recorded: ${recordedActions.last()}")
                                }
                                isDown = false
                                lastX = 0f
                                lastY = 0f
                            }
                        }
                    }
                }

                reader.close()
                process.destroy()

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
            }
        }

        return true
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
    }

    /**
     * 解析 getevent -lt 输出的一行
     * 格式: [   timestamp] device: type code value
     * 例如: [  123456.789012] /dev/input/event1: 0003 0035 00000123
     */
    private fun parseGetEventLine(line: String): EventLine? {
        return try {
            // 匹配时间戳
            val timeMatch = Regex("\\[\\s*(\\d+\\.\\d+)\\]").find(line)
            val timestamp = timeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

            // 提取事件类型、代码、值
            val eventMatch = Regex(":\\s*([0-9a-fA-F]+)\\s+([0-9a-fA-F]+)\\s+([0-9a-fA-F]+)").find(line)
            if (eventMatch == null) return null

            val evType = eventMatch.groupValues[1].toInt(16)
            val evCode = eventMatch.groupValues[2].toInt(16)
            val evValue = eventMatch.groupValues[3].toInt(16)

            EventLine(
                timestamp = (timestamp * 1_000_000).toLong(), // 转换到微秒
                type = evType,
                code = evCode,
                value = evValue,
                key = "${evType.toString(16).padStart(4, '0')}_${evCode.toString(16).padStart(4, '0')}"
            )
        } catch (e: Exception) {
            null
        }
    }

    data class EventLine(
        val timestamp: Long,
        val type: Int,
        val code: Int,
        val value: Int,
        val key: String
    )
}
