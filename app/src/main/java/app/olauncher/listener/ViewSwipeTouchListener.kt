package app.olauncher.listener

import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowInsets
import app.olauncher.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Touch listener for individual views (like home apps) with swipe gesture support.
 * Updated: Added system gesture exclusion zone to allow both app gestures and
 * system gestures (back, home, recent) to coexist without conflict.
 */
internal open class ViewSwipeTouchListener(c: Context?, v: View) : OnTouchListener {
    private var longPressOn = false
    private val gestureDetector: GestureDetector

    // Cache for gesture insets to avoid repeated calculations
    private var cachedSystemGestureLeft: Int = 0
    private var cachedSystemGestureRight: Int = 0
    private var cachedMandatoryGestureBottom: Int = 0
    private var cachedScreenWidth: Int = 0
    private var cachedScreenHeight: Int = 0
    private var hasCachedInsets: Boolean = false

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> view.isPressed = true
            MotionEvent.ACTION_UP -> view.isPressed = false
        }

        // Check if touch is in system gesture exclusion zone
        // If so, don't intercept - let system handle it
        if (isInSystemGestureZone(view, motionEvent)) {
            return false
        }

        return gestureDetector.onTouchEvent(motionEvent)
    }

    /**
     * Determines if the touch event is in the system gesture zone.
     * System gesture zones are:
     * - Left edge: Back gesture (swipe from left to right)
     * - Right edge: Back gesture (swipe from right to left)
     * - Bottom edge: Home/Recent gesture (swipe up from bottom)
     *
     * We exclude these zones from app gesture handling so both can coexist:
     * - System handles gestures from edges
     * - App handles gestures from the center area
     */
    private fun isInSystemGestureZone(view: View, motionEvent: MotionEvent): Boolean {
        // Only apply exclusion on Android 10+ (API 29) where gesture navigation exists
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val x = motionEvent.x
        val y = motionEvent.y

        // Cache the gesture insets for performance
        if (!hasCachedInsets) {
            cacheGestureInsets(view)
        }

        // Check left edge - system back gesture zone
        if (x < cachedSystemGestureLeft) {
            return true
        }

        // Check right edge - system back gesture zone
        if (x > cachedScreenWidth - cachedSystemGestureRight) {
            return true
        }

        // Check bottom edge - system home/recent gesture zone
        // Only exclude if touch starts from bottom zone
        if (y > cachedScreenHeight - cachedMandatoryGestureBottom) {
            return true
        }

        return false
    }

    /**
     * Cache the system gesture insets to avoid repeated calculations.
     * This is called once when the first touch event is received.
     */
    private fun cacheGestureInsets(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = view.rootWindowInsets
            if (insets != null) {
                // System gesture insets - area where system handles edge swipes (back gesture)
                val systemGestureInsets = insets.getInsets(WindowInsets.Type.systemGestures())
                cachedSystemGestureLeft = systemGestureInsets.left
                cachedSystemGestureRight = systemGestureInsets.right

                // Mandatory system gesture insets - area where system handles bottom swipes (home/recent)
                val mandatoryGestureInsets = insets.getInsets(WindowInsets.Type.mandatorySystemGestures())
                cachedMandatoryGestureBottom = mandatoryGestureInsets.bottom
            }
        } else {
            // Fallback for Android 9-10: Use default edge size (typically 20-32dp)
            val density = view.context.resources.displayMetrics.density
            val defaultEdgeSize = (32 * density).toInt() // 32dp default
            cachedSystemGestureLeft = defaultEdgeSize
            cachedSystemGestureRight = defaultEdgeSize
            cachedMandatoryGestureBottom = (48 * density).toInt() // 48dp for bottom
        }

        cachedScreenWidth = view.width
        cachedScreenHeight = view.height
        hasCachedInsets = true
    }

    private inner class GestureListener(private val view: View) : SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD: Int = 100
        private val SWIPE_VELOCITY_THRESHOLD: Int = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick(view)
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            GlobalScope.launch {
                delay(Constants.LONG_PRESS_DELAY_MS)
                withContext(Dispatchers.Main) {
                    if (isActive && longPressOn)
                        onLongClick(view)
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
    open fun onLongClick(view: View) {}
    private fun onDoubleClick() {}
    open fun onClick(view: View) {}

    init {
        gestureDetector = GestureDetector(c, GestureListener(v))
    }
}