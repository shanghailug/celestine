package me.ycy.celestine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import org.opencv.android.OpenCVLoader
import org.opencv.core.Rect

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

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {view ->
        }

        initPopup()
    }

    val POPUP_CODE = 11224

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);

        _helper.onActivityResult(requestCode, resultCode, data)

        if (requestCode == POPUP_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Log.i(TAG, "onActivityResult granted");
            }
        }
    }

    // ref: https://blog.csdn.net/self_study/article/details/52859790
    fun initPopup() {
        var res = true

        if (Build.VERSION.SDK_INT >= 23) {
            try {
                val clazz = Settings::class.java
                val canDrawOverlays = clazz.getDeclaredMethod("canDrawOverlays", Context::class.java)
                res = canDrawOverlays.invoke(null, this) as Boolean
                Log.i(TAG, "can draw overlay: " + res)
            }
            catch (e: Exception) {
                Log.w(TAG, e)
            }

            if (!res) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:" + packageName)
                startActivityForResult(intent, POPUP_CODE)
            }
        }
    }
}
