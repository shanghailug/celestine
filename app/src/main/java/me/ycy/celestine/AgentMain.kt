package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.support.v4.app.ShareCompat
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withTimeout
import org.opencv.core.Rect
import android.graphics.Rect as ARect

// 说明
// 1. WeChat的主界面，在子界面里面也能访问到。感觉像子界面覆盖在主界面上。
//    因此，不能通过是否有主界面元素判断是否是主界面，而是通过是否无子界面元素判断


class AgentMain(c: AccessibilityService) {
    val TAG = Const.TAG + "/agent/m"

    val POLL_DELAY = 10
    val HORI_WAIT_FRAME = 4
    val CLICK_VALID_FRAME = 2
    val CLICK_STABLE_FRAME = 4
    val WAIT_TIMEOUT_NUMBER = 1000 // 1000 * POLL_DELAY = 10sec

    val DURATION_CLICK = 150L
    val DURATION_LONGCLICK = 800L

    val _c = c

    object Profile {
        lateinit var R_SCREEN: Rect // 屏幕的尺寸
        lateinit var R_APP: Rect // 去除手机状态栏之后的内容
        lateinit var R_HEADER: Rect // 上面的一条状态栏，包含“+”、搜索、群名称、返回或“x”等内容
        lateinit var R_FOOTER: Rect // 包含Wechat、Contacts、Discover和Me的底部按钮栏
        lateinit var R_INPUT: Rect // 聊天的输入栏位置
    }

    val root: () -> AccessibilityNodeInfo? = {
        _c.rootInActiveWindow
    }

    suspend fun waitStable(n: Int, l: List<Rect>) = MainActivity.waitStable(n, l)
    suspend fun waitChange(n: Int, l: List<Rect>) = MainActivity.waitChange(n, l)

    suspend fun <R>withNode(node: AccessibilityNodeInfo,
                            func: suspend (AccessibilityNodeInfo) -> R): R {
        val res = func(node)

        node.recycle()
        return res
    }

    // 获取微信底部4个按钮的字符串位置
    suspend fun waitActStr(str: String, idx: Int): AccessibilityNodeInfo {
        var res: AccessibilityNodeInfo? = null
        val N = 4
        var n = 0
        val s = Profile.R_SCREEN

        while (true) {
            val l = waitTextList(str)

            for (i in l) {
                if (res == null) {
                    var b0 = ARect()
                    i.getBoundsInScreen(b0)

                    Log.d(TAG, "waitActStr: " + b0)
                    Log.d(TAG, "-- " + (s.height * 3 / 4))
                    Log.d(TAG, "-- " + (s.height))
                    Log.d(TAG, "-- " + (s.width * idx / N))
                    Log.d(TAG, "-- " + (s.width * (idx + 1) / N))


                    if ((b0.top > s.height * 3 / 4) &&
                            (b0.bottom <= s.height) &&
                            (b0.left >= s.width * idx / N) &&
                            (b0.right <= s.width * (idx + 1) / N)) {
                        res = i
                    }
                }
            }

            for (i in l) { if (i != res) { i.recycle() } }

            if (res != null) break

            n++
            if (n > WAIT_TIMEOUT_NUMBER) {
                throw RuntimeException("timeout wait action str: " + str + ", idx: " + idx)
            }

            delay(POLL_DELAY)
        }

        return res!!
    }

    // 通过back回到主界面
    // 1. 判断当前画面：
    //   a. 无content-desc="Back"的元素
    // 2. 若是主界面，则退出。否则调用全局Back
    // 3. 等待画面稳定
    // 4. 重新进入第一步
    suspend fun doBackToMainScreen() {
        val CONTENT_BACK = "Back"

        waitApp()

        while (true) {
            var hasBack = false

            val res = _c.rootInActiveWindow?.
                    findAccessibilityNodeInfosByText(CONTENT_BACK)

            if (res != null) {
                for (i in res) {
                    if (i.contentDescription == CONTENT_BACK) {
                        hasBack = true
                    }
                    i.recycle()
                }
            }

            // is already main screen
            if (!hasBack) break

            // goto main
            _c.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            // wait stable
            waitStable(HORI_WAIT_FRAME, listOf(Profile.R_HEADER))
        }

        // goto wechat
        withNode(waitActStr(Const.Loc.Main.STR_ACTION_WECHAT, 0), {
            // click, not wait change, only wait stable
            click(it, listOf(), listOf(Profile.R_APP))
        })

        Log.i(TAG, "goto main page")
    }

    // 通过直接按startIntent的方式
    suspend fun doLaunchApp() {
        val intent = _c.packageManager.getLaunchIntentForPackage(Const.App.PACKAGE)
        Log.i(TAG, "intent = ${intent}")
        if (intent != null) {
            _c.startActivity(intent)
        }

        waitApp()
        waitStable(HORI_WAIT_FRAME, listOf(Profile.R_APP))
    }

    // wait App be target app
    suspend fun waitApp() {
        while (_c.rootInActiveWindow?.packageName != Const.App.PACKAGE) {
            delay(POLL_DELAY)
        }
    }

    suspend fun waitId(id: String,
                       node: () -> AccessibilityNodeInfo? = root
    ): AccessibilityNodeInfo {
        var res: AccessibilityNodeInfo? = null
        var n = 0

        while (true) {
            var l = node()?.findAccessibilityNodeInfosByViewId(id)
            if (l != null) {
                for (i in l) {
                    if (res == null) { res = i }
                    else { i.recycle() }
                }
            }

            if (res != null) break

            n++
            if (n > WAIT_TIMEOUT_NUMBER) {
                throw RuntimeException("timeout when wait id:" + id)
            }

            delay(POLL_DELAY)
        }

        return res!!
    }

    suspend fun waitIdList(id: String,
                        node: () -> AccessibilityNodeInfo? = root
    ): List<AccessibilityNodeInfo> {
        var n  = 0
        while (true) {
            var l = node()?.findAccessibilityNodeInfosByViewId(id)
            if (l != null) return l!!

            n++
            if (n > WAIT_TIMEOUT_NUMBER) {
                throw RuntimeException("timeout when wait id list: " + id)
            }

            delay(POLL_DELAY)
        }
    }

    suspend fun waitTextList(t: String,
                             node: () -> AccessibilityNodeInfo? = root
    ): List<AccessibilityNodeInfo> {
        var n  = 0
        while (true) {
            var l = node()?.findAccessibilityNodeInfosByText(t)
            if (l != null) return l!!

            n++
            if (n > WAIT_TIMEOUT_NUMBER) {
                throw RuntimeException("timeout when wait text list: " + t)
            }

            delay(POLL_DELAY)
        }
    }

    suspend fun waitText(t: String): AccessibilityNodeInfo {
        var res: AccessibilityNodeInfo? = null
        var n = 0

        while (true) {
            var l = _c.rootInActiveWindow?.findAccessibilityNodeInfosByText(t)
            if (l != null) {
                for (i in l) {
                    if (res == null) { res = i }
                    else { i.recycle() }
                }
            }

            if (res != null) break

            n++
            if (n > WAIT_TIMEOUT_NUMBER) {
                throw RuntimeException("timeout wait text: " + t)
            }

            delay(POLL_DELAY)
        }

        return res!!
    }

    suspend fun click(
            duration: Long,
            x: Float, y: Float,
            waitValid: suspend () -> Unit = {}, // with timeout
            waitStable: suspend () -> Unit = {}
    ) {
        Log.i(TAG, "click: " + Pair(x, y))

        fun doClick() {
            val path = Path()
            path.moveTo(x, y)

            val gd = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                            path, 0, duration))
                    .build()

            _c.dispatchGesture(gd, null, null)
        }

        while (true) {
            doClick()
            try {
                waitValid()
                break
            }
            catch (e: TimeoutCancellationException) {
                Log.w(TAG, "timeout, retry")
                Log.w(TAG, e)
            }
        }

        waitStable()
    }

    suspend fun click(x: Float, y: Float,
                      roiListV: List<Rect>,
                      roiListS: List<Rect> = roiListV) {
        click(DURATION_CLICK, x, y,
                { withTimeout<Unit>(300) {
                    if (roiListV.isNotEmpty()) {
                        Log.i(TAG, "wait change: " + roiListV)
                        waitChange(CLICK_VALID_FRAME, roiListV)
                    }
                    else {
                        Log.i(TAG, "skip change wait")
                    }
                }},
                {
                    if (roiListS.isNotEmpty()) {
                        Log.i(TAG, "wait stable: " + roiListS)
                        waitStable(CLICK_STABLE_FRAME, roiListS)
                    }
                    else {
                        Log.i(TAG, "skip stable wait")
                    }
                })
    }

    suspend fun click(n: AccessibilityNodeInfo,
                      roiListV: List<Rect>,
                      roiListS: List<Rect> = roiListV) {
        var b = ARect()
        n.getBoundsInScreen(b)
        click(b.exactCenterX(), b.exactCenterY(), roiListV, roiListS)
    }


    // profile while
    suspend fun doProfile() {
        val TAG1 = TAG + "/p"

        // wait app first
        waitApp()
        Log.d(TAG1, "app wait ok")

        Profile.R_SCREEN = Rect(0, 0, Const.width(_c), Const.height(_c))
        Log.i(TAG1, "R_SCREEN: " + Profile.R_SCREEN)

        val b0 = withNode(waitId(Const.Loc.ID_STATUS_BAR), {
            var b = ARect()
            it.getBoundsInScreen(b)
            b
        })

        Profile.R_APP = Rect(0, b0.height(),
                Profile.R_SCREEN.width, Profile.R_SCREEN.height - b0.height())
        Log.i(TAG1, "R_APP: " + Profile.R_APP)

        // NOTE: here we assume HEADER always accessible, which I think is true
        Profile.R_HEADER = withNode(waitId(Const.Loc.ID_HEADER), {
            var b = ARect()
            it.getBoundsInScreen(b)
            Rect(b.left, b.top, b.width(), b.height())
        })
        Log.i(TAG1, "R_HEADER: " + Profile.R_HEADER)

        // back to main screen
        doBackToMainScreen()
        Log.i(TAG1, "back to main screen")

        // measure footer
        Profile.R_FOOTER = withNode(waitActStr(Const.Loc.Main.STR_ACTION_DISCOVER, 2), {
            withNode(it.parent, {
                var b = ARect()
                it.getBoundsInScreen(b)
                Rect(0, b.top, Profile.R_SCREEN.width, b.height())
            })
        })

        Log.i(TAG1, "R_FOOTER: " + Profile.R_FOOTER)

        // goto first group
        withNode(waitId(Const.Loc.Main.ID_CHAT_ENTRY), {
            click(it, listOf(Profile.R_HEADER))
        })

        waitStable(HORI_WAIT_FRAME, listOf(Profile.R_HEADER))

        Log.i(TAG1, "chat page stable")

        // check input
        Profile.R_INPUT = withNode(waitId(Const.Loc.Chat.ID_INPUT), {
            var b = ARect()
            it.getBoundsInScreen(b)
            Rect(b.left, b.top, b.width(), b.height())
        })

        Log.i(TAG1, "R_INPUT: " + Profile.R_INPUT)

        doBackToMainScreen()
        Log.d(TAG1, "back to main screen again")
    }


    suspend fun doProcEachChat(list: List<String>) {
        val nEntrys = waitIdList(Const.Loc.Main.ID_CHAT_ENTRY)

        for (n in nEntrys) {
            withNode(waitId(Const.Loc.Main.ID_CHAT_ENTRY_NAME, { n }), {
                val text = it.text.toString()
                Log.i(TAG, "chat entry:<" + text + ">")

                if (list.contains(text)) {
                    Log.i(TAG, "  --> matched")
                    click(n, listOf(Profile.R_HEADER))

                    AgentChat(this).run()
                    doBackToMainScreen()
                }
            })

            n.recycle()
        }
    }

    suspend fun run() {
        val GROUP_LIST = listOf(
                "SHLUG技术讨论群🚫💦",
                "SHLUG闲聊群"
        )

        doProfile()
        doProcEachChat(GROUP_LIST)
    }
}