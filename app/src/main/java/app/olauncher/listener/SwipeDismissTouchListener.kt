package app.olauncher.listener

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sign

/**
 * Drags [target] horizontally and dismisses it once the gesture passes a distance or velocity
 * threshold, like a notification being swiped away.
 *
 * Can be attached to [target] itself and to any of its clickable children; vertical gestures and
 * taps are left untouched so existing click handling keeps working.
 */
internal class SwipeDismissTouchListener(
    private val target: View,
    private val onDismiss: () -> Unit,
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(target.context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                target.animate().cancel()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
            }

            MotionEvent.ACTION_MOVE -> {
                val offsetX = event.rawX - downX
                velocityTracker?.addMovement(event)
                if (!dragging) {
                    if (abs(offsetX) < touchSlop || abs(offsetX) <= abs(event.rawY - downY)) return false
                    dragging = true
                    view.isPressed = false
                }
                target.translationX = offsetX
                target.alpha = (1f - abs(offsetX) / dismissDistance()).coerceIn(MIN_DRAG_ALPHA, 1f)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val offsetX = event.rawX - downX
                val velocityX = velocityTracker?.let {
                    it.addMovement(event)
                    it.computeCurrentVelocity(1000)
                    it.xVelocity
                } ?: 0f
                releaseVelocityTracker()
                if (!dragging) return false
                dragging = false

                val flung = abs(velocityX) > minFlingVelocity && sign(velocityX) == sign(offsetX)
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    (abs(offsetX) > dismissDistance() || flung)
                ) {
                    animateOut(if (offsetX > 0) 1f else -1f)
                } else {
                    animateBack()
                }
                return true
            }
        }
        return false
    }

    private fun dismissDistance(): Float =
        (target.width * DISMISS_FRACTION).coerceAtLeast(touchSlop.toFloat())

    private fun animateOut(direction: Float) {
        target.animate()
            .translationX(direction * target.width)
            .alpha(0f)
            .setDuration(DISMISS_DURATION_MS)
            .withEndAction {
                onDismiss()
                target.translationX = 0f
                target.alpha = 1f
            }
            .start()
    }

    private fun animateBack() {
        target.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(SETTLE_DURATION_MS)
            .start()
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private companion object {
        const val DISMISS_FRACTION = 0.3f
        const val MIN_DRAG_ALPHA = 0.3f
        const val DISMISS_DURATION_MS = 180L
        const val SETTLE_DURATION_MS = 150L
    }
}
