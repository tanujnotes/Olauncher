package app.olauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.olauncher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A display-only home widget. Tapping cycles time, battery and date, never search. */
class HomeCapsuleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val bolt = ContextCompat.getDrawable(context, R.drawable.ic_drawer_bolt)!!.mutate().apply {
        setTint(Color.BLACK)
    }
    private val state = context.getSharedPreferences("home_capsule", Context.MODE_PRIVATE)
    private var mode = state.getInt("mode", 0).coerceIn(0, 2)
    private var value = ""
    private var badge = ""
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { cycle() }
        refresh()
    }

    fun cycle() {
        mode = (mode + 1) % 3
        state.edit().putInt("mode", mode).apply()
        refresh()
    }

    private var thunderTouch = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val center = height / 2f
            val radius = minOf(23f * resources.displayMetrics.density,
                center - 7f * resources.displayMetrics.density)
            val dx = event.x - center
            val dy = event.y - center
            thunderTouch = dx * dx + dy * dy <= radius * radius
        }
        // Consume the entire thunder gesture so it cannot cycle this view or the home.
        if (thunderTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                thunderTouch = false
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // All of these are system broadcasts; registration is scoped to the attached view.
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        refresh()
    }

    override fun onDetachedFromWindow() {
        context.unregisterReceiver(receiver)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) refresh()
    }

    private fun refresh() {
        val now = Date()
        val locale = Locale.getDefault()
        when (mode) {
            0 -> {
                value = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(now)
                badge = SimpleDateFormat("a", locale).format(now)
            }
            1 -> {
                val level = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                value = if (level in 0..100) "$level%" else "—%"
                badge = ""
            }
            else -> {
                value = SimpleDateFormat("EEE, MMM", locale).format(now)
                badge = SimpleDateFormat("d", locale).format(now)
            }
        }
        val spoken = if (mode == 2) "$value $badge" else if (mode == 0 && !DateFormat.is24HourFormat(context)) "$value $badge" else value
        contentDescription = context.getString(R.string.home_capsule_description, spoken,
            context.getString(when (mode) { 0 -> R.string.home_capsule_battery; 1 -> R.string.home_capsule_date; else -> R.string.home_capsule_time }))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val h = height.toFloat()
        val radius = h / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(14, 14, 14)
        bounds.set(1.5f * d, 1.5f * d, width - 1.5f * d, h - 1.5f * d)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = Color.WHITE
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.FILL
        val circle = minOf(23f * d, radius - 7f * d)
        val left = radius
        val right = width - radius
        canvas.drawCircle(left, radius, circle, paint)
        canvas.drawCircle(right, radius, circle, paint)
        val boltSize = 15f * d
        bolt.setBounds((left - boltSize).toInt(), (radius - boltSize).toInt(),
            (left + boltSize).toInt(), (radius + boltSize).toInt())
        bolt.draw(canvas)
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 26f * resources.displayMetrics.scaledDensity
        val available = (width - 2f * (left + circle + 12f * d)).coerceAtLeast(1f)
        if (paint.measureText(value) > available) paint.textSize *= available / paint.measureText(value)
        canvas.drawText(value, width / 2f, radius - (paint.ascent() + paint.descent()) / 2, paint)
        paint.color = Color.BLACK
        if (mode == 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f * d
            bounds.set(right - 13f*d, radius - 8f*d, right + 11f*d, radius + 8f*d)
            canvas.drawRoundRect(bounds, 2f*d, 2f*d, paint)
            paint.style = Paint.Style.FILL
            canvas.drawRect(right + 12f*d, radius - 3f*d, right + 15f*d, radius + 3f*d, paint)
            for (i in 0..2) canvas.drawRect(right + (-9 + i*6)*d, radius - 4f*d,
                right + (-5 + i*6)*d, radius + 4f*d, paint)
        } else {
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (if (mode == 0) 15f else 23f) * resources.displayMetrics.scaledDensity
            val maxWidth = circle * 1.65f
            if (paint.measureText(badge) > maxWidth) paint.textSize *= maxWidth / paint.measureText(badge)
            canvas.drawText(badge, right, radius - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
}
