package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.reactivex.Observable
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.yield
import org.opencv.core.Rect
import java.util.concurrent.TimeUnit

class AgentService: AccessibilityService() {
    val TAG = Const.TAG + "/agent"
    init {
    }

    override fun onCreate() {
        Log.i(TAG, "on create")
        //ses = Executors.newScheduledThreadPool(5)
    }

    override fun onInterrupt() {
        Log.i(TAG, "on interrupt")
    }

    override fun onDestroy() {
        //ses?.shutdown()
        Log.i(TAG, "on destroy")
    }

    override fun onServiceConnected() {
        Log.i(TAG, "on service connected")

        // Hack
        launch {
            while (Shared.screenReader == null) {
                delay(100)
            }

            launch {
                while (true) {
                    Log.i(TAG, "curr pkg is: " + rootInActiveWindow.packageName)
                    delay(1000)
                }
            }

            val reader = Shared.screenReader!!

            val fullScreen = Rect(0, 0, reader.width, reader.height)
            Log.i(Const.TAG, "waiting for frame change")
            reader.waitChange(1, listOf(fullScreen))
            Log.i(Const.TAG, "frame changed, wait for stable")
            reader.waitStable(4, listOf(fullScreen))
            Log.i(Const.TAG, "frame stable")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO


        return Service.START_STICKY
    }


    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e != null && e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.i(TAG, "notified: " + e.text)
        }

        if ((e == null) || (e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            Log.v(TAG, "e~ ${e}")
        }
    }
}
