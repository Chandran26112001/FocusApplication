package com.example.focusastra.screentime

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.toArgb
import com.example.focusastra.R
import com.example.focusastra.data.MINUTE_MS
import com.example.focusastra.ui.theme.focusColors
import java.util.Locale

/** Two small accessibility windows: a touch-through progress line and a draggable time pill. */
internal class AmbientProgressOverlay(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float) = (value * density + 0.5f).toInt()

    private var bar: FrameLayout? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var progress: View? = null
    private var pill: TextView? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var latestFraction = 0f
    private var latestText = ""

    val visible get() = pill != null

    fun show(label: String, remainingMs: Long, allowanceMs: Long) {
        val minutes = (remainingMs + MINUTE_MS - 1) / MINUTE_MS
        latestText = if (minutes >= 60) String.format(Locale.ROOT, "%dH %02dM LEFT", minutes / 60, minutes % 60)
            else "${minutes}M LEFT"
        latestFraction = (1f - remainingMs.toFloat() / allowanceMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (!visible) attach()
        val textView = pill ?: return
        textView.text = latestText
        textView.contentDescription = "$label, $minutes minutes remaining. Drag to move."
        textView.post { clampCard() }
        progress?.animate()?.scaleX(latestFraction)?.setDuration(450)?.start()
        refreshTheme()
    }

    fun refreshTheme() {
        val colors = focusColors(context)
        val track = bar ?: return
        track.setBackgroundColor(colors.line.toArgb())
        progress?.setBackgroundColor(colors.crimson.toArgb())
        pill?.apply {
            setTextColor(colors.crimson.toArgb())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24f).toFloat()
                setColor(colors.background.toArgb())
                setStroke(dp(1f), colors.line.toArgb())
            }
        }
    }

    fun refreshLayout() {
        val line = bar
        val params = barParams
        if (line != null && params != null && line.isAttachedToWindow) {
            params.y = bounds(line).top
            manager.updateViewLayout(line, params)
        }
        pill?.post { clampCard() }
    }

    private fun attach() {
        val line = FrameLayout(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(View(context).also { progress = it; it.pivotX = 0f }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val lineParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(4f),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = bounds().top
            title = "ClutchState app time progress"
        }
        val card = DraggablePill(context).apply {
            textSize = 12f
            typeface = ResourcesCompat.getFont(context, R.font.space_mono_bold)
            gravity = Gravity.CENTER
            setPadding(dp(15f), dp(12f), dp(15f), dp(12f))
            elevation = dp(5f).toFloat()
            setOnTouchListener(DragListener())
        }
        val cardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ClutchState time left, drag to move"
            val safe = bounds()
            x = savedX ?: (safe.width - dp(112f) - dp(16f)).coerceAtLeast(0)
            y = savedY ?: safe.top + dp(25f)
        }
        try {
            manager.addView(line, lineParams)
            bar = line
            barParams = lineParams
            progress?.post { progress?.apply { pivotX = 0f; scaleX = latestFraction } }
            line.post { refreshLayout() }
            manager.addView(card, cardParams)
            pill = card
            pillParams = cardParams
            card.post { clampCard() }
        } catch (e: RuntimeException) {
            hide()
            throw e
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private var startRawX = 0f
        private var startRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = pillParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX; startRawY = event.rawY
                    startX = params.x; startY = params.y
                    view.isPressed = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - startRawX).toInt()
                    params.y = startY + (event.rawY - startRawY).toInt()
                    clampCard()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    savedX = params.x; savedY = params.y
                    view.performClick()
                    return true
                }
            }
            return false
        }
    }

    private data class SafeBounds(val width: Int, val height: Int, val top: Int, val bottom: Int)

    private class DraggablePill(context: Context) : TextView(context) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    @Suppress("DEPRECATION")
    private fun bounds(view: View? = pill): SafeBounds {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            return SafeBounds(metrics.bounds.width(), metrics.bounds.height(), insets.top, insets.bottom)
        }
        val size = Point()
        manager.defaultDisplay.getRealSize(size)
        val insets = view?.let { ViewCompat.getRootWindowInsets(it) }
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        return SafeBounds(size.x, size.y, insets?.top ?: dp(24f), insets?.bottom ?: dp(24f))
    }

    private fun clampCard() {
        val card = pill ?: return
        val params = pillParams ?: return
        val safe = bounds()
        val maxX = (safe.width - card.width.coerceAtLeast(dp(112f))).coerceAtLeast(0)
        params.x = params.x.coerceIn(dp(16f).coerceAtMost(maxX), maxX)
        params.y = params.y.coerceIn(safe.top, (safe.height - safe.bottom - card.height.coerceAtLeast(dp(42f))).coerceAtLeast(safe.top))
        manager.updateViewLayout(card, params)
        savedX = params.x; savedY = params.y
    }

    fun hide() {
        pill?.let { if (it.isAttachedToWindow) manager.removeViewImmediate(it) }
        bar?.let { if (it.isAttachedToWindow) manager.removeViewImmediate(it) }
        pill = null; bar = null; barParams = null; progress = null; pillParams = null
    }

    fun destroy() = hide()
}
