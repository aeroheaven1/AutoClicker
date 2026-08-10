package com.autoclicker.app.service

import android.os.IBinder
import android.util.Log
import rikka.shizuku.ShizukuUserService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 用户服务
 * 通过 Shizuku 以 shell 权限运行, 在服务内执行 shell 命令
 */
class ShellUserService : ShizukuUserService(), IShellService.Stub() {

    companion object {
        private const val TAG = "ShellUserService"
    }

    override fun onBind(intent: android.content.Intent?): IBinder? {
        Log.d(TAG, "ShellUserService bound, uid=${Process.myUid()}")
        return super.onBind(intent)
    }

    /**
     * 执行命令并返回输出
     * 此方法在 Shizuku shell 权限下运行
     */
    override fun exec(command: String): String {
        Log.d(TAG, "exec: $command")
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
     * 异步执行命令, 不等待结果
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

    override fun isAlive(): Boolean = true
}
