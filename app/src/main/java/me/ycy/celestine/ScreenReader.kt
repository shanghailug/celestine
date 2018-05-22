package me.ycy.celestine

import org.opencv.core.Mat
import org.opencv.core.Rect



interface ScreenReader {
    val width: Int
    val height: Int

    val frameInterval: Long

    fun currFn(): Long

    // copy from current screen
    fun cloneScreen(): Mat

    // do diff with previous frame
    fun frameChanged(roiList: List<Rect>): Boolean

    suspend fun waitStable(n: Int, roiList: List<Rect>)
    suspend fun waitChange(n: Int, roiList: List<Rect>)

    suspend fun waitNextFrame()
}