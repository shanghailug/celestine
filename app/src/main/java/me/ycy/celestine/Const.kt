/*
 * Copyright (C) 2018 Yu Changyuan
 *
 * This file is part of Celestine.
 *
 * Celestine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Celestine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Celestine.  If not, see <http://www.gnu.org/licenses/>.
 */

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

            // 包含对话的子容器，是ID_MAIN的子容器的子容器
            val ID_ROW = App.Id("y")

            // 头像，ImageView
            val ID_AVATAR = App.Id("jx")

            // 名称, TextView
            val ID_FROM = App.Id("jy")

            // 文本消息
            val ID_C_TEXT = App.Id("jz")
            // 双击文本消息，全屏后的TextView ID
            val ID_C_TEXT_DETAIL = App.Id("afp")

            // 链接, NOTE: has child with id ad7
            val ID_C_LINK = App.Id("ad7")
            val ID_C_LINK_SUM = App.Id("adn")
            val ID_C_LINK_DESC = App.Id("adq")

            // 表情, framelayout, with content-desc
            val ID_C_STICKER = App.Id("aec")

            // 图片
            val ID_C_IMAGE = App.Id("ad8")
            val ID_C_IMAGE_1 = App.Id("aec") // imageview

            // 视频


            // 相关字符串
            val STR_COPY = "Copy"
            val STR_DELETE = "Delete"
            val STR_MORE = "More"
        }
    }
}