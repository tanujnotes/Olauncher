package app.olauncher.helper

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import app.olauncher.R
import java.util.function.Consumer
import kotlin.math.abs

/** Lighter dim for dialogs while the system is blurring what's behind them */
private const val DIM_WITH_BLUR = 0.3f

/**
 * Cross-window blur behind a dialog [window] on Android 12+, ramped in and out
 * together with the dialog's own fade. The system can turn blur off at any time
 * (battery saver, the developer option, unsupported hardware); the dialog then
 * keeps the dim its theme asked for. Not used on e-ink displays, where the blur
 * would only smear the screen.
 */
class WindowBlur(private val window: Window) {

    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !window.context.isEinkDisplay()
    private val duration = window.context.resources.getInteger(R.integer.dialog_anim_duration).toLong()
    private val radius = window.context.resources.getDimensionPixelSize(R.dimen.dialog_blur_behind_radius)
    private val dimWithoutBlur = window.attributes.dimAmount
    private val interpolator = DecelerateInterpolator()
    private var blurAvailable = false
    private var fraction = 0f
    private var animator: ValueAnimator? = null

    /** Call after the window is shown so its theme attributes have been resolved. */
    fun fadeIn() {
        if (!supported) return
        start()
    }

    /** Fades the window and its blur out together, then runs [onEnd]. */
    fun fadeOut(onEnd: () -> Unit) {
        if (!supported) {
            onEnd()
            return
        }
        if (blurAvailable) animateTo(0f)
        window.decorView.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .withEndAction(onEnd)
            .start()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun start() {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val onAvailabilityChanged = Consumer<Boolean> { available ->
            blurAvailable = available
            if (available) {
                animateTo(1f)
            } else {
                animator?.cancel()
                window.setDimAmount(dimWithoutBlur)
            }
        }
        window.windowManager.addCrossWindowBlurEnabledListener(onAvailabilityChanged)
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                animator?.cancel()
                window.windowManager.removeCrossWindowBlurEnabledListener(onAvailabilityChanged)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(fraction, target).apply {
            duration = (this@WindowBlur.duration * abs(target - fraction)).toLong()
            interpolator = this@WindowBlur.interpolator
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun apply(fraction: Float) {
        this.fraction = fraction
        window.attributes = window.attributes.apply { blurBehindRadius = (radius * fraction).toInt() }
        window.setDimAmount(DIM_WITH_BLUR * fraction)
    }
}
