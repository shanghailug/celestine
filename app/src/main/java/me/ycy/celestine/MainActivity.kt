package me.ycy.celestine

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.ImageReader
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.opencv.android.OpenCVLoader
import org.opencv.core.Rect
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity() {
    val TAG = Const.TAG + "/main"

    lateinit var _helper: MediaProjectionHelper
    lateinit var _screenReader: ScreenReaderImpl

    companion object {
        var screenReader: ScreenReaderImpl? = null

        suspend fun waitStable(n: Int, roiList: List<Rect>) {
            screenReader?.waitStable(n, roiList)
        }

        suspend fun waitChange(n: Int, roiList: List<Rect>) {
            screenReader?.waitChange(n, roiList)
        }

        suspend fun waitNextFrame() {
            screenReader?.waitNextFrame()
        }

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.e(Const.TAG, "OpenCV init fail!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "on create")

        if (screenReader == null) {
            Log.i(TAG, "screen reader is null, init")
            _screenReader = ScreenReaderImpl(Const.width(this), Const.height(this))
            _helper = MediaProjectionHelper(this, _screenReader.reader)

            screenReader = _screenReader
            //_helper.stop()

            _helper.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        _helper.onActivityResult(requestCode, resultCode, data)
    }
}
