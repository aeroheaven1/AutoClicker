package com.autoclicker.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 脚本动作类型
 */
enum class ActionType {
    TAP,        // 点击
    SWIPE,      // 滑动
    LONG_PRESS, // 长按
    DELAY,      // 延迟等待
    RANDOM_TAP, // 随机区域点击 (x,y)-(x2,y2) 为区域范围
    FIND_TEXT,  // 文字识别找字点击 (text 为要查找的文字)
    REPEAT_START, // 循环开始 (delay 字段存循环次数)
    REPEAT_END    // 循环结束
}

/**
 * 单个动作
 */
data class ScriptAction(
    val type: ActionType,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,       // 滑动终点X / 随机区域右下角X
    val y2: Float = 0f,       // 滑动终点Y / 随机区域右下角Y
    val duration: Long = 100L, // 持续时间(ms) - 长按/滑动
    val delay: Long = 0L,     // 延迟(ms) - DELAY类型使用
    val text: String = ""     // 文字 - FIND_TEXT类型使用
)

/**
 * 脚本定义
 */
data class Script(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "未命名脚本",
    var actions: MutableList<ScriptAction> = mutableListOf(),
    var repeatCount: Int = 1,           // 重复次数, 0=无限
    var intervalBetweenRepeats: Long = 500L, // 重复间隔(ms)
    var intervalBetweenActions: Long = 100L, // 动作间隔(ms)
    var speedFactor: Float = 1.0f,      // 执行速度倍率 (0.25x~5x)
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val totalActions: Int get() = actions.size

    companion object {
        private val gson = Gson()

        fun toJson(script: Script): String = gson.toJson(script)

        fun fromJson(json: String): Script = gson.fromJson(json, Script::class.java)

        fun listToJson(scripts: List<Script>): String = gson.toJson(scripts)

        fun listFromJson(json: String): List<Script> {
            val type = object : TypeToken<List<Script>>() {}.type
            return gson.fromJson(json, type)
        }
    }
}
