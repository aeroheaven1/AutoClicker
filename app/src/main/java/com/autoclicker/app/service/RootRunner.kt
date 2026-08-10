package com.autoclicker.app.service

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Root 权限命令执行器
 * 通过 su 执行 shell 命令
 */
class RootRunner : ICommandRunner {

    private var hasRoot: Boolean? = null

    private var streamProcess: Process? = null
    private var streamThread: Thread? = null
    private val streamBuffer = ConcurrentLinkedQueue<String>()

    override fun isAvailable(): Boolean {
        if (hasRoot == null) {
            hasRoot = checkRoot()
        }
        return hasRoot == true
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            outputStream.write("id\n".toByteArray())
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            reader.close()
            process.destroy()
            result != null && result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    override fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
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

    override fun execAsync(command: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                process.waitFor()
                process.destroy()
            } catch (_: Exception) {}
        }.start()
    }

    override fun startStream(command: String) {
        stopStream()
        streamBuffer.clear()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            streamProcess = process
            streamThread = Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (process.isAlive && reader.readLine().also { line = it } != null) {
                        val text = line
                        if (text != null && text.isNotEmpty()) {
                            streamBuffer.add(text)
                            while (streamBuffer.size > 20000) streamBuffer.poll()
                        }
                    }
                    reader.close()
                } catch (_: Exception) {}
            }
            streamThread?.isDaemon = true
            streamThread?.start()
        } catch (_: Exception) {}
    }

    override fun readStream(): String {
        if (streamBuffer.isEmpty()) return ""
        val sb = StringBuilder(streamBuffer.size * 32)
        var line = streamBuffer.poll()
        while (line != null) {
            sb.append(line).append('\n')
            line = streamBuffer.poll()
        }
        return sb.toString()
    }

    override fun stopStream() {
        try { streamProcess?.destroy() } catch (_: Exception) {}
        streamProcess = null
        try { streamThread?.join(500) } catch (_: Exception) {}
        streamThread = null
    }

    override fun getPermissionType(): String = "Root"
}
