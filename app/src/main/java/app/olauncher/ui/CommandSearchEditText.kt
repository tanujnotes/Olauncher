package app.olauncher.ui

import android.graphics.Rect
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/** Gives an empty search's command chip normal Backspace behavior on both keyboard types. */
class CommandSearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var onEmptyBackspace: (() -> Boolean)? = null
    private var consumedBackspace = false

    /** Restart the native cursor blink without replacing text, selection, or the IME connection. */
    fun refreshTypingCursor() {
        isCursorVisible = false
        isCursorVisible = true
        invalidate()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (isFocused) refreshTypingCursor()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) refreshTypingCursor()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isFocused) refreshTypingCursor()
    }

    private fun deleteCommand(): Boolean =
        text.isNullOrEmpty() && onEmptyBackspace?.invoke() == true

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DEL) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) consumedBackspace = deleteCommand()
                if (consumedBackspace) return true
            } else if (event.action == KeyEvent.ACTION_UP && consumedBackspace) {
                consumedBackspace = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && afterLength == 0 && deleteCommand()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && afterLength == 0 && deleteCommand()) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)
        }
    }
}
