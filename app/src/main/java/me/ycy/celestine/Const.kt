package me.ycy.celestine

import android.app.Activity
import android.content.Context
import android.graphics.Point

object Const {
    val TAG = "celestine"

    fun width(a: Context): Int {
        return a.resources.displayMetrics.widthPixels
    }

    fun height(a: Context): Int {
        return a.resources.displayMetrics.heightPixels
    }

    fun dpi(a: Context): Int {
        return a.resources.displayMetrics.densityDpi
    }
}