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

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class LockTouchListener(c: Context?) : OnTouchListener {
    private var doubleTapOn = false
    private val gestureDetector: GestureDetector

    init {
        gestureDetector = GestureDetector(c, GestureListener())
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(motionEvent)
    }

    private inner class GestureListener : SimpleOnGestureListener() {

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
    }

    open fun onDoubleClick() {}
    open fun onTripleClick() {}
}