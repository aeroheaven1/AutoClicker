package com.autoclicker.app.service

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 权限命令执行器
 * 通过 Shizuku 用户服务执行 shell 命令
 */
class ShizukuRunner : ICommandRunner {

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == 0 ||
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    override fun exec(command: String): String {
        return try {
            tryExecViaShizuku(command)
        } catch (e: Exception) {
            fallbackExec(command)
        }
    }

    private fun tryExecViaShizuku(command: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
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
            throw e
        }
    }

    private fun fallbackExec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
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
            "Shizuku Error: ${e.message}"
        }
    }

    override fun execAsync(command: String) {
        Thread {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                process.waitFor()
                process.destroy()
            } catch (e: Exception) {
                try {
                    val process = Runtime.getRuntime().exec(command)
                    process.waitFor()
                    process.destroy()
                } catch (_: Exception) {}
            }
        }.start()
    }

    override fun getPermissionType(): String = "Shizuku"
}
