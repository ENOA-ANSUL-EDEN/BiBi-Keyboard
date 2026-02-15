package com.brycewg.asrkb.ime

import android.content.res.Resources
import androidx.core.view.WindowInsetsCompat

/**
 * 统一解析 IME 底部系统占位，避免不同 ROM 在导航手势/工具条场景下高度漏算。
 */
internal object ImeInsetsResolver {
    private const val FALLBACK_NAV_BAR_FRAME_HEIGHT_DP = 48

    fun resolveBottomInset(insets: WindowInsetsCompat, resources: Resources): Int {
        val navBarsBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val mandatoryBottom = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
        val tappableBottom = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
        val systemGesturesBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom

        var resolvedBottom = maxOf(navBarsBottom, mandatoryBottom, tappableBottom)
        if (resolvedBottom <= 0) {
            resolvedBottom = maxOf(systemGesturesBottom, getNavigationBarFrameHeight(resources))
        }
        return resolvedBottom.coerceAtLeast(0)
    }

    private fun getNavigationBarFrameHeight(resources: Resources): Int {
        val resId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
        if (resId > 0) {
            try {
                return resources.getDimensionPixelSize(resId)
            } catch (_: Resources.NotFoundException) {
                // ignore and fallback
            }
        }
        val density = resources.displayMetrics.density
        return (FALLBACK_NAV_BAR_FRAME_HEIGHT_DP * density + 0.5f).toInt()
    }
}
