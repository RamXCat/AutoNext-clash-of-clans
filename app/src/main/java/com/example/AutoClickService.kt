package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected!")
        instance = this
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        private const val TAG = "AutoClickService"
        private var instance: AutoClickService? = null

        val isConnected: Boolean
            get() = instance != null

        fun tapAt(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "Performing tap gesture at ($x, $y)")
            
            val path = Path().apply {
                moveTo(x, y)
            }
            
            val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
            val gesture = GestureDescription.Builder().apply {
                addStroke(stroke)
            }.build()

            var success = false
            try {
                success = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Tap completed successfully at ($x, $y)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Tap gesture cancelled at ($x, $y)")
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching gesture", e)
            }
            return success
        }
    }
}
