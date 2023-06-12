package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R

class AlphabetFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var alphabetList: List<String> = emptyList()
    private var selectedLetter: String? = null
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private var itemHeight = 0
    private var selectedIndex = -1
    private var recyclerView: RecyclerView? = null

    init {
        setOnTouchListener { _, event ->
            val touchIndex = (event.y / itemHeight).toInt()
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (touchIndex in 0 until alphabetList.size) {
                        selectedIndex = touchIndex
                        val letter = alphabetList[selectedIndex]
                        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
                        val position = findFirstLetterPosition(layoutManager, letter)
                        if (position != -1) {
                            layoutManager?.scrollToPositionWithOffset(position, 0)
                        }
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    selectedIndex = -1
                    invalidate()
                }
            }
            true
        }
    }

    private fun findFirstLetterPosition(layoutManager: LinearLayoutManager?, letter: String): Int {
        val adapter = recyclerView?.adapter as? AppDrawerAdapter
        val dataList = adapter?.getAppModelList()
        return dataList?.indexOfFirst { it.appLabel.startsWith(letter, ignoreCase = true) } ?: -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (alphabetList.isNotEmpty() && measuredHeight != 0) {
            itemHeight = measuredHeight / alphabetList.size
        }    }
    fun highlightLetter(letter: String?) {
        selectedLetter = letter
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = measuredWidth / 2f
        val bounds = Rect()

        for ((index, letter) in alphabetList.withIndex()) {
            val y = (index + 1) * itemHeight - (itemHeight - bounds.height()) / 2f
            paint.color = if (letter == selectedLetter) {
                ContextCompat.getColor(context, R.color.colorAccent) // Highlighted color
            } else {
                Color.WHITE
            }
            canvas.drawText(letter, x, y, paint)
        }
    }

    fun setAlphabetList(alphabetList: List<String>) {
        this.alphabetList = alphabetList
        requestLayout()
    }

    fun setRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

}