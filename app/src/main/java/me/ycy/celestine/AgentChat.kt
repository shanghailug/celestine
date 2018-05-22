package me.ycy.celestine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.opencv.core.Rect
import kotlin.math.roundToInt
import android.graphics.Rect as ARect

class AgentChat(m: AgentMain) {
    val TAG = Const.TAG + "/agent/c"

    val VERI_WAIT_FRAME = 4

    val _m = m

    suspend fun doScrollToBegin() {
        val nMain = _m.waitId(Const.Loc.Chat.ID_MAIN)

        val jobScroll = launch {
            val N = 5
            val dt = MainActivity.screenReader!!.frameInterval / N
            while (true) {
                var b = ARect()
                // NOTE: always get new one
                _m.withNode(_m.waitId(Const.Loc.Chat.ID_MAIN)) {
                    it.getBoundsInScreen(b)
                    //it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }

                //delay(interval)

                for (i in 1 .. (N - 1)) {
                    // perform manual scroll, to avoid some corner case
                    val path = Path()
                    val x = b.left + b.width() * i / N
                    path.moveTo(x.toFloat(), (b.top + b.height() * 1 / 3).toFloat())
                    path.rLineTo(0f, b.height().toFloat() / 3f)

                    _m.performGesture(path, 0, 10)

                    delay(dt)
                }
            }
        }

        // 检查PageMain的一下区域：
        // 1. 左边7.5%
        // 2. 右边7.5%
        // 3. 最上面，中间位置7.5%的正方形
        var b0 = ARect()
        nMain.getBoundsInScreen(b0)
        val w75 = (b0.width() * 0.075 + 0.5).roundToInt()

        val roiList = listOf(
                Rect(b0.left, b0.top, w75, b0.height()),
                Rect(b0.width() - w75, b0.top, w75, b0.height()),
                Rect(b0.centerX() - w75 / 2, b0.top, w75, w75)
        )
        _m.waitStable(VERI_WAIT_FRAME, roiList)

        jobScroll.cancel()

        nMain.recycle()
    }

    suspend fun run() {
        doScrollToBegin()

        Log.i(TAG, "done")
        //delay(60 * 1000 * 10)
    }
}