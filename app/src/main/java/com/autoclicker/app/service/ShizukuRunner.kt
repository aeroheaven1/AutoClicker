package com.autoclicker.app.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku 权限命令执行器
 * 通过 Shizuku 用户服务 (ShellUserService) 以 shell 权限执行命令
 */
class ShizukuRunner(private val context: Context) : ICommandRunner {

    companion object {
        private const val TAG = "ShizukuRunner"
        private const val BIND_TIMEOUT_MS = 8000L
    }

    @Volatile
    private var shellService: IShellService? = null

    private val binderLock = Any()

    private val userServiceArgs: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ShellUserService::class.java)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(binderLock) {
                shellService = IShellService.Stub.asInterface(binder)
                Log.d(TAG, "Shizuku user service connected")
                (binderLock as java.lang.Object).notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(binderLock) {
                shellService = null
                Log.d(TAG, "Shizuku user service disconnected")
            }
        }
    }

    private fun ensureBound() {
        if (shellService != null) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
            Log.d(TAG, "bindUserService requested")
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService error: ${e.message}")
        }
    }

    private fun waitForService(timeoutMs: Long): IShellService? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(binderLock) {
            while (shellService == null && System.currentTimeMillis() < deadline) {
                try {
                    (binderLock as java.lang.Object).wait(deadline - System.currentTimeMillis())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return shellService
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    override fun exec(command: String): String {
        ensureBound()
        val service = waitForService(BIND_TIMEOUT_MS)
        return service?.exec(command) ?: "Error: Shizuku 服务未绑定或超时"
    }

    override fun execAsync(command: String) {
        ensureBound()
        val service = waitForService(BIND_TIMEOUT_MS)
        service?.execAsync(command)
    }

    override fun startStream(command: String) {
        ensureBound()
        val service = waitForService(BIND_TIMEOUT_MS)
        service?.startStream(command)
    }

    override fun readStream(): String {
        val service = shellService ?: return ""
        return try {
            service.readStream()
        } catch (e: Exception) {
            ""
        }
    }

    override fun stopStream() {
        val service = shellService ?: return
        try {
            service.stopStream()
        } catch (e: Exception) {
            Log.e(TAG, "stopStream error: ${e.message}")
        }
    }

    override fun getPermissionType(): String = "Shizuku"
}
