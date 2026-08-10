package com.autoclicker.app.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 屏幕坐标拾取器
 *
 * 显示一个全屏半透明取点层, 用户点击屏幕任意位置自动获取坐标,
 * 无需开发者模式的"指针位置"功能。
 *
 * 支持三种模式:
 * - SINGLE: 取一个点 (用于点击)
 * - START_POINT: 取起点 (用于滑动)
 * - END_POINT: 取终点 (用于滑动)
 */
class CoordinatePicker(
    private val context: Context,
    private val callback: (Float, Float) -> Unit
) {

    enum class Mode(val label: String) {
        SINGLE("点击屏幕选取坐标 (轻点任意位置)"),
        START_POINT("点击屏幕选取滑动起点"),
        END_POINT("点击屏幕选取滑动终点")
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isActive = false

    /** 是否正在取点 */
    fun isActive(): Boolean = isActive

    /**
     * 开始取点
     */
    fun start(mode: Mode) {
        if (isActive) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 全屏透明覆盖层
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x66000000.toInt()) // 半透明暗色
        }

        // 顶部提示条
        val hint = TextView(context).apply {
            text = "🎯 ${mode.label}"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xCC0061A4.toInt())
        }
        val hintParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        overlay.addView(hint, hintParams)

        // 中央大提示
        val centerHint = TextView(context).apply {
            text = "👆\n\n点击屏幕任意位置\n选取坐标\n\n(点击屏幕取消)"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        overlay.addView(centerHint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // 触摸监听: 点击时获取坐标
        overlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    val x = event.rawX
                    val y = event.rawY
                    stop()
                    Handler(Looper.getMainLooper()).post {
                        callback(x, y)
                    }
                    true
                }
                else -> true
            }
        }

        overlayView = overlay
        windowManager?.addView(overlay, params)
        isActive = true
    }

    /** 停止取点 */
    fun stop() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        isActive = false
    }

    private fun windowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
