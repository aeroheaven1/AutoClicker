package com.autoclicker.app.service

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Shizuku 用户服务 (UserService)
 *
 * 注意: 这不是 Android Service!
 * 由 Shizuku 服务器在 shell 权限下通过反射实例化,
 * 所有方法执行在 Shizuku 服务器进程 (shell/adb 权限)。
 *
 * 要求:
 * 1. 必须有 public 无参构造器 (或带 Context 构造器)
 * 2. 不能被混淆 (见 proguard-rules.pro)
 * 3. 通过 Shizuku.bindUserService 绑定
 */
class ShellUserService : IShellService.Stub {

    companion object {
        private const val TAG = "ShellUserService"
        private const val MAX_BUFFER_SIZE = 512 * 1024 // 512KB 防溢出
    }

    constructor() : super()

    /**
     * v13+ 支持带 Context 的构造器
     * 需要 @Keep 注解防止混淆移除
     */
    @Keep
    constructor(context: Context) : this()

    // 流式命令状态
    @Volatile
    private var streamProcess: Process? = null

    @Volatile
    private var streamThread: Thread? = null

    private val streamBuffer = ConcurrentLinkedQueue<String>()

    /**
     * 执行命令并返回输出
     * 运行在 Shizuku shell 权限下
     */
    override fun exec(command: String): String {
        Log.d(TAG, "exec: $command (uid=${android.os.Process.myUid()})")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readText()
            val error = errorReader.readText()
            reader.close()
            errorReader.close()
            process.waitFor()
            process.destroy()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * 异步执行命令
     */
    override fun execAsync(command: String) {
        Thread {
            try {
                Log.d(TAG, "execAsync: $command")
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.waitFor()
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "execAsync error: ${e.message}")
            }
        }.start()
    }

    /**
     * 启动流式命令 (如 getevent), 输出持续写入 buffer
     */
    override fun startStream(command: String) {
        stopStream()
        streamBuffer.clear()
        Log.d(TAG, "startStream: $command")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            streamProcess = process

            streamThread = Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (process.isAlive && reader.readLine().also { line = it } != null) {
                        val text = line
                        if (text != null && text.isNotEmpty()) {
                            streamBuffer.add(text)
                            // 防溢出: 丢弃最旧的
                            while (streamBuffer.size > 20000) {
                                streamBuffer.poll()
                            }
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    Log.e(TAG, "stream read error: ${e.message}")
                }
            }
            streamThread?.isDaemon = true
            streamThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "startStream error: ${e.message}")
        }
    }

    /**
     * 读取并清空流式 buffer
     */
    override fun readStream(): String {
        if (streamBuffer.isEmpty()) return ""
        val sb = StringBuilder(streamBuffer.size * 32)
        var line = streamBuffer.poll()
        while (line != null) {
            sb.append(line).append('\n')
            if (sb.length > MAX_BUFFER_SIZE) break
            line = streamBuffer.poll()
        }
        return sb.toString()
    }

    /**
     * 停止流式命令
     */
    override fun stopStream() {
        try {
            streamProcess?.destroy()
        } catch (_: Exception) {}
        streamProcess = null
        try {
            streamThread?.join(500)
        } catch (_: Exception) {}
        streamThread = null
    }

    override fun isAlive(): Boolean = true

    /**
     * Shizuku 服务器调用, 销毁服务
     */
    override fun destroy() {
        stopStream()
        Log.d(TAG, "ShellUserService destroyed")
    }
}
