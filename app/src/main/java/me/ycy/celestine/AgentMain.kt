package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.support.v4.app.ShareCompat
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.*
import org.opencv.core.Rect
import java.util.*
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
    val WAIT_TIMEOUT = (1000 * 10) // 1000 * POLL_DELAY = 10sec
    val ROOT_TIMEOUT = 1000 // wait for fixRoot
    val DURATION_CLICK = 50L
    val DURATION_LONGCLICK = 800L

    val _c = c
    val _cm: ClipboardManager

    init {
        _cm = _c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    object Profile {
        lateinit var R_SCREEN: Rect // 屏幕的尺寸
        lateinit var R_APP: Rect // 去除手机状态栏之后的内容
        lateinit var R_HEADER: Rect // 上面的一条状态栏，包含“+”、搜索、群名称、返回或“x”等内容
        lateinit var R_FOOTER: Rect // 包含Wechat、Contacts、Discover和Me的底部按钮栏
        lateinit var R_INPUT: Rect // 聊天的输入栏位置
        lateinit var R_CHAT: Rect // 聊天主界面
    }

    val root: () -> AccessibilityNodeInfo? = {
        var res = _c.rootInActiveWindow

        res
    }

    fun performGesture(path: Path, t0: Long, duration: Long) {
        val gd = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                        path, t0, duration))
                .build()

        _c.dispatchGesture(gd, null, null)
    }

    suspend fun waitStable(n: Int, l: List<Rect>) = MainActivity.waitStable(n, l)
    suspend fun waitChange(n: Int, l: List<Rect>) = MainActivity.waitChange(n, l)
    suspend fun waitNextFrame() = MainActivity.waitNextFrame()

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
        var t0 = Date().time
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

            if ((Date().time - t0) > WAIT_TIMEOUT) {
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
        val t0 = Date().time

        while (true) {
            var l = node()?.findAccessibilityNodeInfosByViewId(id)
            if (l != null) {
                for (i in l) {
                    if (res == null) { res = i }
                    else { i.recycle() }
                }
            }

            if (res != null) break

            if ((Date().time - t0) > WAIT_TIMEOUT) {
                // NOTE: sometime, node() will return NULL
                Log.w(TAG, "get node() = " + node())
                Log.w(TAG, "curr thread =" + Thread.currentThread())
                throw RuntimeException("timeout when wait id:" + id)
            }

            delay(POLL_DELAY)
        }

        return res!!
    }

    suspend fun waitIdList(id: String,
                        node: () -> AccessibilityNodeInfo? = root
    ): List<AccessibilityNodeInfo> {
        val t0 = Date().time
        while (true) {
            var l = node()?.findAccessibilityNodeInfosByViewId(id)
            if (l != null) return l!!

            if ((Date().time - t0) > WAIT_TIMEOUT) {
                throw RuntimeException("timeout when wait id list: " + id)
            }

            delay(POLL_DELAY)
        }
    }

    suspend fun waitTextList(t: String,
                             node: () -> AccessibilityNodeInfo? = root
    ): List<AccessibilityNodeInfo> {
        val t0 = Date().time
        while (true) {
            var l = node()?.findAccessibilityNodeInfosByText(t)
            if (l != null) return l!!

            if ((Date().time - t0) > WAIT_TIMEOUT) {
                throw RuntimeException("timeout when wait text list: " + t)
            }

            delay(POLL_DELAY)
        }
    }

    suspend fun waitText(t: String, to: Int): AccessibilityNodeInfo? {
        var res: AccessibilityNodeInfo? = null
        val t0 = Date().time

        while (true) {
            var l = _c.rootInActiveWindow?.findAccessibilityNodeInfosByText(t)
            if (l != null) {
                for (i in l) {
                    if (res == null) { res = i }
                    else { i.recycle() }
                }
            }

            if (res != null) break

            if (to < 0) {
                if ((Date().time - t0) > WAIT_TIMEOUT) {
                    throw RuntimeException("timeout wait text: " + t)
                }
            }
            else {
                if ((Date().time - t0) > to) {
                    break
                }
            }

            delay(POLL_DELAY)
        }

        return res
    }

    suspend fun waitText(t: String): AccessibilityNodeInfo {
        return waitText(t, -1)!!
    }

    suspend fun waitAnyText(ts: List<String>): Pair<AccessibilityNodeInfo, String> {
        var res: AccessibilityNodeInfo? = null
        var res1: String = ""
        val t0 = Date().time

        while (true) {
            for (t in ts) {
                var l = _c.rootInActiveWindow?.findAccessibilityNodeInfosByText(t)
                if (l != null) {
                    for (i in l) {
                        if (res == null) {
                            res = i
                            res1 = t
                        } else {
                            i.recycle()
                        }
                    }
                }

                if (res != null) break
            }

            if (res != null) break

            if ((Date().time - t0) > WAIT_TIMEOUT) {
                throw RuntimeException("timeout wait any text: " + ts)
            }

            delay(POLL_DELAY)
        }

        return Pair(res!!, res1)
    }

    suspend fun click(
            duration: Long,
            x: Float, y: Float,
            validTimeout: Long,
            waitValid: suspend () -> Unit = {},
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

        // should start waitValid first
        // and run waitValid across whole click & retry click period
        val a0 = async { waitValid() }
        val a1 = async {
            while (true) {
                doClick()
                delay(validTimeout)
                Log.i(TAG, "timeout, retry")
            }
        }

        a0.await()
        a1.cancel()

        waitStable()
    }

    suspend fun click(duration: Long, x: Float, y: Float,
                      roiListV: List<Rect>,
                      roiListS: List<Rect> = roiListV,
                      timeout: Int = WAIT_TIMEOUT) {
        click(duration, x, y, 300, {
            if (roiListV.isNotEmpty()) {
                Log.i(TAG, "wait change: " + roiListV)
                // TODO: waitChange or waitID
                withTimeout(timeout) {
                    async { waitChange(CLICK_VALID_FRAME, roiListV) }.await()
                }
            }
            else {
                Log.i(TAG, "skip change wait")
            }
        }, {
            if (roiListS.isNotEmpty()) {
                Log.i(TAG, "wait stable: " + roiListS)
                withTimeout(timeout) {
                    async { waitStable(CLICK_STABLE_FRAME, roiListS) }.await()
                }
            }
            else {
                Log.i(TAG, "skip stable wait")
            }
        })
    }

    suspend fun click(x: Float, y: Float,
                      roiListV: List<Rect>,
                      roiListS: List<Rect> = roiListV,
                      timeout: Int = WAIT_TIMEOUT) {
        click(DURATION_CLICK, x, y, roiListV, roiListS, timeout)
    }

    suspend fun click(n: AccessibilityNodeInfo,
                      roiListV: List<Rect>,
                      roiListS: List<Rect> = roiListV,
                      timeout: Int = WAIT_TIMEOUT) {
        var b = ARect()
        n.getBoundsInScreen(b)
        click(b.exactCenterX(), b.exactCenterY(), roiListV, roiListS, timeout)
    }

    suspend fun fixRoot(n: Int = ROOT_TIMEOUT) {
        var flag = true

        while (flag) {
            try {
                withTimeout(n, {
                    while (root() == null) {
                        delay(POLL_DELAY)
                    }

                    flag = false
                })
            } catch (e: TimeoutCancellationException) {
                _c.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                delay(50)
                doLaunchApp()
            }
        }
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

        Log.i(TAG1, "chat page stable")

        // check input
        Profile.R_INPUT = withNode(waitId(Const.Loc.Chat.ID_INPUT), {
            var b = ARect()
            it.getBoundsInScreen(b)
            Rect(b.left, b.top, b.width(), b.height())
        })

        Log.i(TAG1, "R_INPUT: " + Profile.R_INPUT)

        Profile.R_CHAT = withNode(waitId(Const.Loc.Chat.ID_MAIN), {
            var b = ARect()
            it.getBoundsInScreen(b)
            Rect(b.left, b.top, b.width(), b.height())
        })

        Log.i(TAG1, "R_CHAT: " + Profile.R_CHAT)

        doBackToMainScreen()
        Log.d(TAG1, "back to main screen again")
    }


    suspend fun doProcChat(chat: String) {
        val nEntrys = waitIdList(Const.Loc.Main.ID_CHAT_ENTRY)

        for (n in nEntrys) {
            withNode(waitId(Const.Loc.Main.ID_CHAT_ENTRY_NAME, { n }), {
                val text = it.text.toString()
                Log.i(TAG, "chat entry:<" + text + ">")

                if (text == chat) {
                    Log.i(TAG, "  --> matched")
                    // TODO, use waitId for validChecking
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
                //"Yù Chāngyuǎn",
                //"test",
                "SHLUG闲聊群"
        )

        while (true) {
            try {
                doProfile()
                break
            }
            catch (e: Exception) {
                Log.w(TAG, "profile error!")
                Log.w(TAG, e)
            }
        }

        Log.i(TAG, "profile done")

        while (true) {
            waitApp()
            doBackToMainScreen()

            try {
                while (true) {
                    for (chat in GROUP_LIST) {
                        doProcChat(chat)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "error: ")
                Log.w(TAG, e)
            }
        }
    }
}