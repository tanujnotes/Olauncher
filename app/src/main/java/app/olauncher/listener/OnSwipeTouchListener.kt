package app.olauncher.listener

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import app.olauncher.data.Constants
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : OnTouchListener {
    private var longPressOn = false
    private var doubleTapOn = false
    private val gestureDetector: GestureDetector

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_UP)
            longPressOn = false
        return gestureDetector.onTouchEvent(motionEvent)
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD: Int = 100
        private val SWIPE_VELOCITY_THRESHOLD: Int = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (doubleTapOn) {
                doubleTapOn = false
                onTripleClick()
            }
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTapOn = true
            Timer().schedule(Constants.TRIPLE_TAP_DELAY_MS.toLong()) {
                if (doubleTapOn) {
                    onDoubleClick()
                    doubleTapOn = false
                }
            }
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            Timer().schedule(Constants.LONG_PRESS_DELAY_MS.toLong()) {
                if (longPressOn) onLongClick()
            }
            super.onLongPress(e)
        }

        override fun onFling(
            event1: MotionEvent,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            try {
                val diffY = event2.y - event1.y
                val diffX = event2.x - event1.x
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
    open fun onTripleClick() {}
    private fun onClick() {}

    init {
        gestureDetector = GestureDetector(c, GestureListener())
    }
}