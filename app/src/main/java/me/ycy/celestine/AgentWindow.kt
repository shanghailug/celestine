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

import android.app.Service
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AgentWindow(s: Service) {
    val _s = s
    lateinit var view: LinearLayout
    lateinit var params: WindowManager.LayoutParams
    lateinit var wm: WindowManager

    var initialX: Int = 0
    var initialY: Int = 0
    var initialTouchX: Float = 0f
    var initialTouchY: Float = 0f

    val TAG = Const.TAG + "/pop"

    fun setMoveView(v: View) {
        v.setOnTouchListener {v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    params.x = newX;
                    params.y = newY;
                    wm.updateViewLayout(view, params)
                    true
                }

                else -> false
            }
        }
    }

    fun init() {
        wm = _s.getSystemService(WINDOW_SERVICE) as WindowManager
        //root = _s.layoutInflater.inflate(R.layout.activity_popup, null)
        view = LinearLayout(_s)
        view.orientation = LinearLayout.VERTICAL

        val btn = Button(_s)
        btn.text = "TEST"
        btn.alpha = 0.5f

        val tv = TextView(_s)
        tv.text = "MOVE"
        tv.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        tv.alpha = 0.5f

        view.addView(tv)
        view.addView(btn)

        params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 500
        params.y = 500

        //this code is for dragging the chat head
        setMoveView(tv)

        btn.setOnClickListener {v ->
            Log.i(TAG, "clicked")
        }

        Log.i(TAG, "add view")
        wm.addView(view, params)
    }

    fun deinit() {
        wm.removeView(view)
    }
}