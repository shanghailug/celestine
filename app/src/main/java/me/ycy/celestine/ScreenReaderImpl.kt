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

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.util.Log
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.rx2.awaitLast
import kotlinx.coroutines.experimental.rx2.rxObservable
import kotlinx.coroutines.experimental.yield
import me.ycy.celestine.Const.TAG
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.util.concurrent.TimeUnit

class ScreenReaderImpl(width: Int, height: Int): ScreenReader {
    override val frameInterval = 100L

    override val width = width
    override val height = height

    val reader: ImageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888,
            2)

    var _fn: Long = 0

    val _mat0 = Mat.zeros(height, width, CvType.CV_8UC4)
    val _mat1 = Mat.zeros(height, width, CvType.CV_8UC4)
    var _frameChanged = false

    val observable = rxObservable {
        while (true) {
            screenshot()

            send(Pair(_fn, this@ScreenReaderImpl as ScreenReader))
            _fn ++

            // NOTE: might drop frame
            val remainTime  = frameInterval - (System.currentTimeMillis() % frameInterval)
            delay(remainTime)
        }
    }.share()

    override fun currFn() = _fn
    override fun cloneScreen(): Mat = _mat0.clone()

    override fun frameChanged(roiList: List<Rect>): Boolean {
        if (!_frameChanged) return false

        // NOTE: cnt all change, use for debug
        var cnt = 0

        for (i in roiList) {
            Log.v(TAG, "mat0 row/col: " + _mat0.rows() + "/" + _mat0.cols() +
                    ", roi x,y+w+h: " + i.x + "," + i.y + "+" + i.width + "+" + i.height)
            val s0 = _mat0.submat(i)
            val s1 = _mat1.submat(i)
            val d0 = Mat()
            val d1 = Mat()

            Core.absdiff(s0, s1, d0)
            Imgproc.cvtColor(d0, d1, Imgproc.COLOR_BGR2GRAY)

            cnt += Core.countNonZero(d1)

            s0.release()
            s1.release()
            d0.release()
            d1.release()
        }

        //Log.d(Const.TAG, "diff cnt = " + cnt)

        return (cnt > 0)
    }

    var _bitmap: Bitmap? = null

    private fun screenshot() {
        var image: Image? = null

        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowPadding = plane.rowStride - pixelStride * width

                if (_bitmap == null) {
                    // create bitmap
                    _bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                            Bitmap.Config.ARGB_8888)
                }

                _bitmap!!.copyPixelsFromBuffer(plane.buffer)

                _mat0.copyTo(_mat1)
                Utils.bitmapToMat(_bitmap, _mat0)

                _frameChanged = true
            }
            else {
                _frameChanged = false
            }
        }
        catch (e: Exception) { e.printStackTrace() }
        finally { if (image != null) { image.close() } }
    }

    override suspend fun waitStable(n: Int, roiList: List<Rect>) {
        async {
            observable.map {
                val reader = it.second
                val changed = reader.frameChanged(roiList)
                //Log.i(Const.TAG, "wait stable0: " + it)
                changed
            }.buffer(
                    n, 1
            ).takeWhile {
                //Log.i(Const.TAG, "wait stable1" + it)
                it.any { it }
            }.blockingSubscribe()
        }.await()
    }

    override suspend fun waitChange(n: Int, roiList: List<Rect>) {
        // NOTE: use awaitLast() will cause `observable' variable
        // crash due timeout excetpion
        async {
            observable.map {
                val reader = it.second
                val changed = reader.frameChanged(roiList)
                //Log.i(Const.TAG, "wait change0: " + it)
                !changed
            }.buffer(
                    n, 1
            ).takeWhile {
                //Log.i(Const.TAG, "wait change1: " + it)
                it.any { it }
            }.blockingSubscribe()
        }.await()
    }

    override suspend fun waitNextFrame() {
        async {
            observable.blockingSingle()
        }
    }
}