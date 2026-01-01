package app.olauncher.listener

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import app.olauncher.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : OnTouchListener {
    private var longPressOn = false
    private val gestureDetector: GestureDetector

    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lastEvent: MotionEvent? = null

    private val tapRunnable = Runnable {
        when (tapCount) {
            1 -> onClick()
            2 -> onDoubleClick()
            3 -> lastEvent?.let { onTripleClick(it) }
        }
        tapCount = 0
        lastEvent?.recycle()
        lastEvent = null
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_UP) {
            longPressOn = false
        }
        return gestureDetector.onTouchEvent(motionEvent)
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD: Int = 100
        private val SWIPE_VELOCITY_THRESHOLD: Int = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        // Wir nutzen onSingleTapUp für jeden Tap. 
        // Um den GestureDetector daran zu hindern, onDoubleTap zu priorisieren, 
        // verarbeiten wir die Taps manuell.
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tapCount++
            lastEvent?.recycle()
            lastEvent = MotionEvent.obtain(e)
            handler.removeCallbacks(tapRunnable)
            if (tapCount >= 3) {
                handler.post(tapRunnable)
            } else {
                handler.postDelayed(tapRunnable, 400)
            }
            return true
        }

        // Wir überschreiben onDoubleTap und geben false zurück, 
        // damit onSingleTapUp weiterhin für jeden Klick aufgerufen wird.
        override fun onDoubleTap(e: MotionEvent): Boolean {
            return false 
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            GlobalScope.launch {
                delay(Constants.LONG_PRESS_DELAY_MS)
                withContext(Dispatchers.Main) {
                    if (isActive && longPressOn)
                        onLongClick()
                }
            }
            super.onLongPress(e)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            try {
                val diffY = event2.y - (event1?.y ?: 0F)
                val diffX = event2.x - (event1?.x ?: 0F)
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) onSwipeUp() else onSwipeDown()
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return false
        }
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}
    open fun onLongClick() {}
    open fun onDoubleClick() {}
    open fun onTripleClick(e: MotionEvent) {}
    open fun onClick() {}

    init {
        gestureDetector = GestureDetector(c, GestureListener())
        // Deaktiviert die interne DoubleTap-Logik, damit wir sie manuell im tapRunnable steuern können
        gestureDetector.setOnDoubleTapListener(null)
    }
}