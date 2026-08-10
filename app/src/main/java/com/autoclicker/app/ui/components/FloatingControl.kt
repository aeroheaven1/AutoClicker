package com.autoclicker.app.ui.components

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclicker.app.AutoClickerApp
import com.autoclicker.app.MainActivity
import com.autoclicker.app.service.ClickService

/**
 * 悬浮窗控制面板
 * 提供快捷的开始/停止和录制功能
 */
class FloatingControl(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var controlPanel: View? = null
    private var isPanelShown = false

    fun show() {
        if (floatingView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 创建主悬浮按钮
        floatingView = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF0061A4.toInt())
            // 使用简单文字视图代替
            val textView = TextView(context).apply {
                text = "⚡"
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            }
            addView(textView)
        }

        val params = WindowManager.LayoutParams(
            120,
            120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        floatingView?.setOnClickListener {
            toggleControlPanel()
        }

        // 添加拖拽功能
        setupDrag(floatingView!!, params)

        windowManager?.addView(floatingView, params)
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(deltaX) > 5 || kotlin.math.abs(deltaY) > 5) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleControlPanel() {
        if (isPanelShown) {
            hideControlPanel()
        } else {
            showControlPanel()
        }
    }

    private fun showControlPanel() {
        if (controlPanel != null) return

        controlPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF1A1C1E.toInt())
            alpha = 0.95f
        }

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 暂时只提示：悬浮控制面板需要通过 Compose 完全重写
        // 当前简化版本直接显示按钮
        windowManager?.addView(controlPanel, panelParams)
        isPanelShown = true
    }

    private fun hideControlPanel() {
        controlPanel?.let {
            windowManager?.removeView(it)
            controlPanel = null
        }
        isPanelShown = false
    }

    fun hide() {
        hideControlPanel()
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        windowManager = null
    }
}
