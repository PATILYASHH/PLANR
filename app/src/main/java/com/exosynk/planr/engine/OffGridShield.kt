package com.exosynk.planr.engine

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * While the Off-Grid lock screen is in front, covers the navigation-bar and
 * status-bar strips with invisible accessibility overlays that swallow every
 * touch. Back / Home / Recents buttons and pulling down quick settings then do
 * nothing, instead of leaving the lock for a second before PLANR bounces back.
 *
 * Accessibility overlays sit above the system bars and need no extra permission.
 * The shield is only up while the lock screen itself is showing (never over the
 * phone, camera or the apps you kept) and never while a call is ringing or active.
 */
class OffGridShield(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private var views: List<View> = emptyList()

    val isShown: Boolean get() = views.isNotEmpty()

    fun show() {
        if (isShown || wm == null) return
        val insets = wm.currentWindowMetrics.windowInsets
        val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
        val gestures = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
        val status = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
        val density = service.resources.displayMetrics.density
        // Cover the nav bar (3-button) or the gesture area, whichever is taller, plus a margin.
        val bottom = maxOf(bars.bottom, gestures.bottom, (24 * density).toInt()) + (8 * density).toInt()
        val top = maxOf(status.top, (24 * density).toInt())
        views = listOfNotNull(
            add(Gravity.BOTTOM, WindowManager.LayoutParams.MATCH_PARENT, bottom),
            add(Gravity.TOP, WindowManager.LayoutParams.MATCH_PARENT, top),
            // Landscape / tablets: side-mounted navigation bars.
            bars.left.takeIf { it > 0 }?.let { add(Gravity.START, it, WindowManager.LayoutParams.MATCH_PARENT) },
            bars.right.takeIf { it > 0 }?.let { add(Gravity.END, it, WindowManager.LayoutParams.MATCH_PARENT) },
        )
    }

    fun hide() {
        views.forEach { v -> runCatching { wm?.removeView(v) } }
        views = emptyList()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(gravity: Int, width: Int, height: Int): View? {
        val view = View(service).apply {
            setOnTouchListener { _, _ -> true } // swallow
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        return runCatching { wm?.addView(view, lp); view }.getOrNull()
    }
}
