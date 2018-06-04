package me.ycy.celestine

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        // NOTE: sometimes, rootInActiveWindow will be null
        // run rootInActiveWindow in event callback thread
        // or other thread will no different
        // and callback, e.source is also NULL

        if (rootInActiveWindow == null) {
            Log.w(TAG, "root is NULL")
        }

        if (e != null) {
            var r: AccessibilityNodeInfo? = e.source

            while (true) {
                if (r?.parent == null) break
                r = r?.parent
            }

            //Log.v(TAG, "event ancestor: " + r)
        }

        //Log.v(TAG, "event: " + e)
    }
}
