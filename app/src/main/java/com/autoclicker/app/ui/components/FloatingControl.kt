package com.autoclicker.app.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclicker.app.R

/**
 * 悬浮窗控制 (悬浮球 + 控制面板)
 *
 * 功能:
 * - 悬浮球可自由拖动
 * - 点击悬浮球弹出/收起控制面板
 * - 面板提供: 开始/停止、快速点击、快速滑动、录制、关闭悬浮窗
 */
class FloatingControl(
    private val context: Context,
    private val callbacks: Callbacks
) {

    /** 回调接口, 由 MainActivity 实现 */
    interface Callbacks {
        fun onToggleRun()
        fun onQuickTap()
        fun onQuickSwipe()
        fun onRecord()
        fun onOpenApp()
    }

    private var windowManager: WindowManager? = null

    // 悬浮球
    private var floatingBall: View? = null
    private var ballParams: WindowManager.LayoutParams? = null

    // 控制面板
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelVisible = false

    // 面板内控件引用
    private var statusText: TextView? = null
    private var runButtonText: TextView? = null

    private var isDragging = false

    /** 显示悬浮球 */
    fun show() {
        if (floatingBall != null) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 悬浮球: 圆形 ⚡ 按钮
        floatingBall = ImageView(context).apply {
            setImageResource(R.drawable.ic_ball)
            setBackgroundResource(R.drawable.bg_floating_ball)
            contentDescription = "连点器悬浮球"
        }

        ballParams = WindowManager.LayoutParams(
            dp(52), dp(52),
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(320)
        }

        // 点击/拖拽
        floatingBall?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    startX = event.rawX
                    startY = event.rawY
                    startLeft = ballParams!!.x
                    startTop = ballParams!!.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!isDragging && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        ballParams!!.x = (startLeft + dx).toInt()
                        ballParams!!.y = (startTop + dy).toInt()
                        windowManager?.updateViewLayout(view, ballParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()  // 触发 OnClickListener → togglePanel()
                    }
                    true
                }
                else -> false
            }
        }

        floatingBall?.setOnClickListener { togglePanel() }
        windowManager?.addView(floatingBall, ballParams)
    }

    private var startX = 0f
    private var startY = 0f
    private var startLeft = 0
    private var startTop = 0

    /** 切换控制面板显示 */
    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    /** 显示控制面板 */
    private fun showPanel() {
        if (panelView != null) return

        panelView = buildPanel()
        panelParams = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 放在悬浮球旁边
            val bx = ballParams?.x ?: dp(16)
            val by = ballParams?.y ?: dp(320)
            x = if (bx + dp(280) > getScreenWidth()) bx - dp(280) else bx + dp(60)
            y = (by - dp(120)).coerceAtLeast(dp(10))
        }

        windowManager?.addView(panelView, panelParams)
        panelVisible = true
    }

    /** 隐藏控制面板 */
    private fun hidePanel() {
        panelView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        panelVisible = false
    }

    /** 构建面板视图 */
    private fun buildPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundResource(R.drawable.bg_panel)
            elevation = dp(8).toFloat()
        }

        // 标题行: 标题 + 收起
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = "连点器"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        })
        titleRow.addView(makeTextButton("×", 32f) {
            hidePanel()
        })
        panel.addView(titleRow)

        // 状态文本
        statusText = TextView(context).apply {
            text = "状态: 就绪"
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, dp(4), 0, dp(8))
        }
        panel.addView(statusText)

        // 分隔线
        panel.addView(divider())

        // 按钮列表
        runButtonText = null
        panel.addView(makePanelButton("▶ 开始脚本") {
            callbacks.onToggleRun()
        }.also { runButtonText = it })

        panel.addView(makePanelButton("👆 快速点击") {
            hidePanel()
            callbacks.onQuickTap()
        })

        panel.addView(makePanelButton("↔ 快速滑动") {
            hidePanel()
            callbacks.onQuickSwipe()
        })

        panel.addView(makePanelButton("● 录制脚本") {
            hidePanel()
            callbacks.onRecord()
        })

        panel.addView(makePanelButton("☰ 打开应用") {
            hidePanel()
            callbacks.onOpenApp()
        })

        // 底部按钮行
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        bottomRow.addView(makeTextButton("收起", 13f) { hidePanel() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        })
        bottomRow.addView(makeTextButton("关闭悬浮窗", 13f) { hide() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        })
        panel.addView(bottomRow)

        return panel
    }

    /** 创建面板主按钮 */
    private fun makePanelButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundResource(R.drawable.bg_panel_button)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
        }
    }

    /** 创建小文字按钮 */
    private fun makeTextButton(label: String, size: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = size
            setTextColor(0xFF80D8FF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
            )
            setOnClickListener { onClick() }
        }
    }

    /** 分隔线 */
    private fun divider(): View {
        return View(context).apply {
            setBackgroundColor(0x33FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = dp(4); bottomMargin = dp(10) }
        }
    }

    /**
     * 更新运行状态 (面板与悬浮球)
     */
    fun setRunning(running: Boolean) {
        runButtonText?.text = if (running) "■ 停止脚本" else "▶ 开始脚本"
        statusText?.text = if (running) "状态: 脚本运行中..." else "状态: 就绪"
    }

    /**
     * 更新状态文本
     */
    fun updateStatus(text: String) {
        statusText?.text = text
    }

    private fun windowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun getScreenWidth(): Int {
        val dm = context.resources.displayMetrics
        return dm.widthPixels
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    /** 移除悬浮窗 */
    fun hide() {
        hidePanel()
        floatingBall?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            floatingBall = null
        }
        windowManager = null
    }
}
