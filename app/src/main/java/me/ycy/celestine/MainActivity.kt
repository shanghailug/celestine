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

object Shared {
    var screenReader: ScreenReaderImpl? = null
}

class MainActivity : AppCompatActivity() {
    lateinit var _helper: MediaProjectionHelper
    lateinit var _screenReader: ScreenReaderImpl

    companion object {
        init {
            if (!OpenCVLoader.initDebug()) {
                Log.e(Const.TAG, "OpenCV init fail!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _screenReader = ScreenReaderImpl(Const.width(this), Const.height(this))
        _helper = MediaProjectionHelper(this, _screenReader.reader)

        Shared.screenReader = _screenReader
        //_helper.stop()

        _helper.start()

        Log.i(Const.TAG, "start")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        _helper.onActivityResult(requestCode, resultCode, data)
    }
}
