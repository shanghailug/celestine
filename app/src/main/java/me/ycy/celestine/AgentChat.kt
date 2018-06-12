package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import io.reactivex.internal.operators.completable.CompletableFromAction
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.NULL_VALUE
import kotlinx.coroutines.experimental.channels.POLL_FAILED
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.yield
import org.opencv.core.Rect
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt
import android.graphics.Rect as ARect

sealed class Message() {
    abstract val from: String
    abstract val self: Boolean

    data class Text(override val from: String,
                    override val self: Boolean,
                    val text: String?) : Message()
    data class Link(override val from: String,
                    override val self: Boolean,
                    val sum: String, val desc: String): Message()
    data class Image(override val from: String,
                     override val self: Boolean): Message()
    data class Sticker(override val from: String,
                       override val self: Boolean,
                       val desc: String): Message()
    data class Unknown(override val from: String,
                       override val self: Boolean): Message()
}

class AgentChat(m: AgentMain) {
    val TAG = Const.TAG + "/agent/c"

    val VERI_WAIT_FRAME = 4
    val VERI_SCROLL_FRAME = 8

    val _m = m

    fun roiScrollV(): List<Rect> {
        // 1. 左边7.5%
        // 2. 右边7.5%
        var b0 = AgentMain.Profile.R_CHAT
        val w75 = (b0.width * 0.075 + 0.5).roundToInt()

        return listOf(
                Rect(b0.x, b0.y, w75, b0.height),
                Rect(b0.width - w75, b0.y, w75, b0.height))
    }

    fun roiScrollH(): List<Rect> {
        // 3. 最上面，中间位置7.5%的正方形
        var b0 = AgentMain.Profile.R_CHAT
        val w75 = (b0.width * 0.075 + 0.5).roundToInt()

        return listOf(Rect(b0.width / 2 - w75 / 2, b0.y, w75, w75))
    }

    fun roiScroll(): List<Rect> {
        // 检查PageMain的一下区域：
        return roiScrollH() + roiScrollV()
    }

    suspend fun doScrollToBegin() {
        // if can not find valid row, then already at begin
        if (rowFindValid() == null) {
            return
        }

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
                    // NOTE: to avoid float window
                    val x = b.left + b.width() * (i and 1) / N
                    path.moveTo(x.toFloat(), (b.top + b.height() * 1 / 3).toFloat())
                    path.rLineTo(0f, b.height().toFloat() / 3f)

                    _m.performGesture(path, 0, 10)

                    delay(dt)
                }
            }
        }

        val waitJob = launch {
            _m.waitStable(VERI_SCROLL_FRAME, roiScroll())
        }

        // sometimes, scroll wheel appears, but can not get more history
        val checkContent = launch {
            var lastNode: Any? = null
            var cnt = 0
            val INTERVAL = 500

            while (true) {
                val m = _m.waitId(Const.Loc.Chat.ID_MAIN)
                val n0 = m.getChild(0)

                if ((n0 != null) && (n0 == lastNode)) {
                    cnt++
                }
                else {
                    cnt = 0
                }

                lastNode = n0

                delay(INTERVAL)

                if (cnt > (_m.WAIT_TIMEOUT / INTERVAL)) {
                    break
                }
            }

            Log.w(TAG, "chat scroll timeout, just use as first messagae!")
            // cancel scroll job, an we should still wait page be stable

            // TODO
            Log.w(TAG, "something wrong with android system or wechat, please reboot")
            //jobScroll.cancel()
        }

        waitJob.join()

        checkContent.cancel()
        jobScroll.cancel()
    }

    suspend fun rowFindValid(): AccessibilityNodeInfo? {
        var res: AccessibilityNodeInfo? = null

        _m.fixRoot()

        val nMain = _m.waitId(Const.Loc.Chat.ID_MAIN)

        for (i in 0..(nMain.childCount-1)) {
            val ch = nMain.getChild(i)
            val rows = ch.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_ROW)
            if (rows.isEmpty()) {
                ch.recycle()
                continue
            }
            for (i in rows) { i.recycle() }

            res = ch

            break
        }

        nMain.recycle()

        return res
    }

    // 若当前行位置不好，将其移动到合适的位置
    // true: scrolled, need re-find first vaild row
    // false: GUI untouched, just return
    suspend fun rowPrepare(row: AccessibilityNodeInfo): Boolean {
        Log.i(TAG, "prepare: " + Utils.nodeRect(row))

        val rect = Utils.nodeRect(row)
        val RC = AgentMain.Profile.R_CHAT

        if (rect.y < RC.height * 0.25 + RC.y) {
            return false
        }

        Log.i(TAG, "need scroll up, do align")

        val path = Path()
        path.moveTo(RC.x + RC.width / 2f, rect.y + 0f)
        path.rLineTo(0f, 10f - rect.y + RC.y)

        _m.performGesture(path, 0, 100)

        _m.waitStable(VERI_WAIT_FRAME, roiScrollH())

        return true
    }

    // NOTE: when text not find, but find alt, will return false
    // this is for some weird text message, which 'NO' copy option
    suspend fun bubbleLongClickForText(x: Float, y: Float, text: String,
                                       alt: String = text): Boolean {
        var res = false;

        _m.click("long click for text <" + text + ">",
                _m.DURATION_LONGCLICK, x, y,
                // wait text appear
                3000, {
            _m.waitText(alt)

            if (_m.waitText(text, 1000) != null) {
                res = true
            }
        }, {})

        return res
    }

    suspend fun bubbleLongClickForText(n: AccessibilityNodeInfo, text: String,
                                       alt: String = text): Boolean {
        var b = ARect()
        n.getBoundsInScreen(b)
        return bubbleLongClickForText(b.exactCenterX(), b.top.toFloat() + 5f,
                text, alt)
    }

    suspend fun  bubleCopyText(n: AccessibilityNodeInfo): String? {
        val clipboard = _m._cm
        //Log.i(TAG, "clip: " + clipboard)
        clipboard.primaryClip = ClipData.newPlainText("empty", "")

        if (!bubbleLongClickForText(n, Const.Loc.Chat.STR_COPY, Const.Loc.Chat.STR_MORE)) {
            // NOTE: should click back if not find 'Delete', to make context menu disappear
            // NOTE: this not stable
            _m.performAction(AccessibilityService.GLOBAL_ACTION_BACK)

            return null
        }

        _m.withNode(_m.waitText(Const.Loc.Chat.STR_COPY), {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        })

        var text = ""
        while (true) {
            val cnt = clipboard.primaryClip.itemCount
            if (cnt > 0) {
                text = clipboard.primaryClip.getItemAt(0).text.toString()
                Log.v(TAG, "clipboard: ${text}")
                break
            }

            delay(50)
        }

        _m.waitStable(2, listOf(Utils.nodeRect(n)))

        return text
    }

    // copy text with double click
    // not stable, some time not back
    suspend fun bubbleCopyText1(n: AccessibilityNodeInfo): String? {
        val r = Utils.nodeRect(n)
        Log.i(TAG, "double click")
        var text: String? = null

        try {
            _m.click("double click", {
                it(10)
                delay(100)
                it(10)
            }, r.x + 10f, r.y + 10f, 2000, {
                _m.waitId(Const.Loc.Chat.ID_C_TEXT_DETAIL)
            }, {})

            text = _m.waitId(Const.Loc.Chat.ID_C_TEXT_DETAIL).text?.toString()

            // single click to back, not stable
            _m.click("text back click", _m.DURATION_CLICK,
                    r.x.toFloat(), r.y.toFloat(), 1000,
                    {}, {})
        }
        catch (e: Exception) {
            Log.w(TAG, "fail to get text by double click")
        }

        return text
    }

    // 提取行的信息
    suspend fun rowExtrace(row: AccessibilityNodeInfo): Message {
        var res: Message? = null

        fun recycle(nl: List<AccessibilityNodeInfo>) {
            for (n in nl) { n.recycle() }
        }

        // Order is *IMPORTANT*
        // 1. text
        // 2. link
        // 3. image
        // 4. sticker

        // get from
        val nl0 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_FROM)
        val from = if (nl0.isEmpty()) "" else nl0[0].text.toString()
        recycle(nl0)

        Log.i(TAG, "from: " + from)

        // get avatar
        val nlA = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_AVATAR)
        var self = false
        if (nlA.isNotEmpty()) {
            if (Utils.nodeRect(nlA[0]).x > AgentMain.Profile.R_SCREEN.width / 2) {
                self = true
            }
        }

        // try text
        val nl1 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_TEXT)

        if (nl1.isNotEmpty()) {
            val text = bubleCopyText(nl1[0])
            recycle(nl1)
            res = Message.Text(from, self, text)
        }

        if (res != null) return res

        // try link
        val nl2 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_LINK)
        if (nl2.isNotEmpty()) {
            val nl3 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_LINK_SUM)
            if (nl3.isNotEmpty()) {
                val sum = nl3[0].text.toString()

                val nl4 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_LINK_DESC)
                val desc = if (nl4.isEmpty()) "" else nl4[0].text.toString()
                recycle(nl4)

                res = Message.Link(from, self, sum, desc)
            }
            recycle(nl3)
        }
        recycle(nl2)

        if (res != null) return res

        // try image
        val nl3 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_IMAGE)
        if (nl3.isNotEmpty()) {
            val nl4 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_IMAGE_1)
            if (nl4.isNotEmpty()) {
                res = Message.Image(from, self)
            }
            recycle(nl4)
        }
        recycle(nl3)

        if (res != null) return res

        // try sticker

        val nl4 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_C_STICKER)
        if (nl4.isNotEmpty()) {
            val cd = nl4[0].contentDescription
            val desc = if (cd == null) "" else cd.toString()

            res = Message.Sticker(from, self, desc)
        }
        recycle(nl4)

        if (res != null) return res

        // unknown
        return Message.Unknown(from, self)
    }

    // 删除行
    suspend fun rowDelete(row: AccessibilityNodeInfo) {
        Log.i(TAG, "delete message")

        _m.fixRoot()

        // TODO, wait all menu disappear, necessary?

        // NOTE: 头像右面，下面3/4位置，再右偏1/2
        val nl0 = row.findAccessibilityNodeInfosByViewId(Const.Loc.Chat.ID_AVATAR)
        if (nl0.isEmpty()) return

        _m.withNode(nl0[0], {
            val rect = Utils.nodeRect(it)

            val x = if (rect.x < AgentMain.Profile.R_SCREEN.width / 2)
                rect.x + rect.width + rect.width * 0.5f
            else
                rect.x - rect.width * 0.5f

            val y = rect.y + rect.height * 3f / 4

            Log.i(TAG, "long click for menu")
            bubbleLongClickForText(x, y, Const.Loc.Chat.STR_DELETE)
        })

        Log.i(TAG, "begin wait delete")
        _m.withNode(_m.waitText(Const.Loc.Chat.STR_DELETE)) {
            Log.i(TAG, "click delete")
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        _m.fixRoot()
    }

    suspend fun doProcMsg() {
        // 1. 找到第一个有效的行（通过ID_ROW，获取列表），若没有，则跳到步骤3
        // 2. 进行处理（准备、提取、删除）。然后重复改1、2
        // 3. 向下滚动
        // 4. 找到有效的行。若没有，则退出。否则进入步骤3。

        while (true) {
            // step 1
            Log.v(TAG, "step 1: find valid row")
            var row = rowFindValid()

            // step 2
            if (row != null) {
                if (rowPrepare(row)) {
                    row = rowFindValid()
                    if (row == null) continue
                }

                val m = rowExtrace(row)
                Log.i(TAG, "msg: " + m)

                rowDelete(row)

                row.recycle()
                continue
            }

            // step 3
            Log.i(TAG, "step 3: start")
            var row1: AccessibilityNodeInfo? = null
            val job = async {
                _m.waitStable(5, roiScroll()) // wait 500ms
            }

            while (true) {
                _m.withNode(_m.waitId(Const.Loc.Chat.ID_MAIN), {
                    it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                })

                _m.waitStable(2, roiScroll())

                row1 = rowFindValid()
                row1?.recycle()

                if (row1 != null) {
                    job.cancel()
                    break
                }

                if (job.isCompleted) break
            }

            Log.i(TAG, "step 3: end")
            if (row1 == null) break
        }
    }

    suspend fun run() {
        doScrollToBegin()
        try {
            doProcMsg()
        }
        catch (e: Exception) {
            Log.w(TAG, "error: ")
            Log.w(TAG, e)

            throw(e)
        }

        Log.i(TAG, "done")
        //delay(60 * 1000 * 10)
    }
}
