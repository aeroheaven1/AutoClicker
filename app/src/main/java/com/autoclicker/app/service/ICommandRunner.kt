package com.autoclicker.app.service

/**
 * 命令执行接口 - 统一 Shizuku 和 Root 的调用方式
 */
interface ICommandRunner {
    /** 是否可用 */
    fun isAvailable(): Boolean

    /** 执行shell命令，返回输出 */
    fun exec(command: String): String

    /** 执行shell命令（异步，不等待结果） */
    fun execAsync(command: String)

    /** 获取权限类型名称 */
    fun getPermissionType(): String

    /** 启动流式命令 (如 getevent 持续输出) */
    fun startStream(command: String) {}

    /** 读取流式命令的新增输出 (读取后清空) */
    fun readStream(): String = ""

    /** 停止流式命令 */
    fun stopStream() {}
}
