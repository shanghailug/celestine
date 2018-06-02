package me.ycy.celestine

import android.view.accessibility.AccessibilityNodeInfo
import org.opencv.core.Rect
import android.graphics.Rect as ARect


object Utils {
    fun nodeRect(n: AccessibilityNodeInfo): Rect {
        val b = ARect()
        n.getBoundsInScreen(b)

        return Rect(b.left, b.top, b.width(), b.height())
    }

}