package me.ycy.celestine

import android.app.Activity
import android.content.Context
import android.graphics.Point

object Const {
    val TAG = "celestine"

    object App {
        val PACKAGE = "com.tencent.mm"
        //val ACTIVITY =

        fun Id(id: String): String = PACKAGE + ":id/" + id
    }

    fun width(a: Context): Int {
        return a.resources.displayMetrics.widthPixels
    }

    fun height(a: Context): Int {
        return a.resources.displayMetrics.heightPixels
    }

    fun dpi(a: Context): Int {
        return a.resources.displayMetrics.densityDpi
    }

    object Loc {
        val VERSION = "6.6.6" // wechat version
        val ID_STATUS_BAR = "android:id/statusBarBackground"

        // 最上面一条的状态栏，不光是在主屏幕出现
        val ID_HEADER = App.PACKAGE + ":id/h0"

        // 主屏幕
        object Main {
            val STR_ACTION_WECHAT = "WeChat"
            val STR_ACTION_CONTACTS = "Contacts"
            val STR_ACTION_DISCOVER = "Discover"
            val STR_ACTION_ME = "Me"

            // 聊天列表的条目，可以点击的，进入到具体的聊天人或群
            val ID_CHAT_ENTRY = App.Id("apt")
            // 聊天的名称，聊天人或者群名称，TextView
            val ID_CHAT_ENTRY_NAME = App.Id("apv")
        }

        object Chat {
            // 上面的状态栏
            //val ID_HEADER = Loc.ID_HEADER

            // 聊天的主页面，但不是顶层控件，而是其中大小一样的，scrollable的一个子控件（ListView）
            // 顶层的是一个Layout
            val ID_MAIN = App.Id("a_c")

            // 输入栏
            val ID_INPUT = App.Id("a_p")
        }
    }
}