package me.ycy.celestine

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "celestine"
        init {
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV init fail!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
