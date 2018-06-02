package me.ycy.celestine

import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import io.reactivex.internal.operators.completable.CompletableFromAction
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.opencv.core.Rect
import kotlin.math.roundToInt
import android.graphics.Rect as ARect

sealed class Message() {
    abstract val from: String
    abstract val self: Boolean

    data class Text(override val from: String,
                    override val self: Boolean,
                    val text: String) : Message()
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

    val _m = m

    fun roiScroll(): List<Rect> {
        // 检查PageMain的一下区域：
        // 1. 左边7.5%
        // 2. 右边7.5%
        // 3. 最上面，中间位置7.5%的正方形
        var b0 = AgentMain.Profile.R_CHAT
        val w75 = (b0.width * 0.075 + 0.5).roundToInt()

        return listOf(
                Rect(b0.x, b0.y, w75, b0.height),
                Rect(b0.width - w75, b0.y, w75, b0.height),
                Rect(b0.width / 2 - w75 / 2, b0.y, w75, w75)
        )
    }

    suspend fun doScrollToBegin() {
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

        _m.waitStable(VERI_WAIT_FRAME, roiScroll())

        jobScroll.cancel()
    }

    suspend fun rowFindValid(): AccessibilityNodeInfo? {
        var res: AccessibilityNodeInfo? = null

        Log.i(TAG, "curr thread =" + Thread.currentThread())

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
    suspend fun rowPrepare(row: AccessibilityNodeInfo) {
        // TODO
        Log.i(TAG, "prepare: " + row)
    }

    suspend fun bubbleLongClick(n: AccessibilityNodeInfo) {
        var b = ARect()
        n.getBoundsInScreen(b)
        _m.click(_m.DURATION_LONGCLICK, b.exactCenterX(), b.top.toFloat() + 5f,
                listOf(), listOf(Rect(b.left, b.top, b.width(), b.height())))

    }

    suspend fun  bubleCopyText(n: AccessibilityNodeInfo): String {
        val clipboard = _m._cm
        Log.i(TAG, "clip: " + clipboard)
        clipboard.primaryClip = ClipData.newPlainText("empty", "")

        bubbleLongClick(n)

        _m.withNode(_m.waitText(Const.Loc.Chat.STR_COPY), {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        })

        var text = ""
        while (true) {
            val cnt = clipboard.primaryClip.itemCount
            if (cnt > 0) {
                text = clipboard.primaryClip.getItemAt(0).text.toString()
                Log.i(TAG, "clipboard: ${text}")
                break
            }

            delay(50)
        }

        _m.waitStable(2, listOf(Utils.nodeRect(n)))

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
            val desc = nl4[0].contentDescription.toString()

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

            _m.click(_m.DURATION_LONGCLICK, x, y, listOf(), listOf())
        })

        _m.withNode(_m.waitText(Const.Loc.Chat.STR_DELETE)) {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    suspend fun doProcMsg() {
        // 1. 找到第一个有效的行（通过ID_ROW，获取列表），若没有，则跳到步骤3
        // 2. 进行处理（准备、提取、删除）。然后重复改1、2
        // 3. 向下滚动
        // 4. 找到有效的行。若没有，则退出。否则进入步骤3。

        while (true) {
            // step 1
            val row = rowFindValid()

            // step 2
            if (row != null) {
                rowPrepare(row)

                val m = rowExtrace(row)
                Log.i(TAG, "msg: " + m)

                rowDelete(row)

                row.recycle()
                continue
            }

            // step 3
            _m.withNode(_m.waitId(Const.Loc.Chat.ID_MAIN), {
                it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            })

            _m.waitStable(VERI_WAIT_FRAME, roiScroll())

            val row1 = rowFindValid()
            row1?.recycle()

            if (row1 == null) {
                break
            }
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