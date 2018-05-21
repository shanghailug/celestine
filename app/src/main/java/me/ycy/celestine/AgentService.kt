package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.reactivex.Observable
import kotlinx.coroutines.experimental.*
import org.opencv.core.Rect
import java.util.concurrent.TimeUnit

class AgentService: AccessibilityService() {
    val TAG = Const.TAG + "/agent"

    companion object {
        var agent: Job? = null
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

        agent?.cancel()
        agent = null
    }

    override fun onServiceConnected() {
        Log.i(TAG, "on service connected")

        if (agent == null) {
            Log.i(TAG, "agent main is null, starting")
            val agentMain = AgentMain(this)

            // Hack
            agent = launch {
                Log.i(TAG, "wait screen reader...")

                while (MainActivity.screenReader == null) {
                    delay(100)
                }

                Log.i(TAG, "run agent")
                agentMain.run()
            }
        }
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
