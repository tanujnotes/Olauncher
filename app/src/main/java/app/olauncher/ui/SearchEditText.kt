package app.olauncher.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/** Reports a deletion only when it starts with an empty field. */
class SearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var onEmptyBackspace: (() -> Unit)? = null
    private var backspaceDown = false
    private var consumeBackspace = false
    private var lastImeDelete = Long.MIN_VALUE

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_DEL || onEmptyBackspace == null) {
            return super.dispatchKeyEvent(event)
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!backspaceDown) {
                    backspaceDown = true
                    consumeBackspace = text.isNullOrEmpty()
                    if (consumeBackspace && event.repeatCount == 0) onEmptyBackspace?.invoke()
                }
                // A hold that deletes the last character must never become a tab switch.
                if (consumeBackspace) return true
            }
            KeyEvent.ACTION_UP -> {
                val consumed = consumeBackspace
                backspaceDown = false
                consumeBackspace = false
                if (consumed) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL) return dispatchKeyEvent(event)
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (handleImeDelete(beforeLength, afterLength)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (handleImeDelete(beforeLength, afterLength)) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    private fun handleImeDelete(beforeLength: Int, afterLength: Int): Boolean {
        if (onEmptyBackspace == null || beforeLength <= 0 || afterLength != 0) return false
        val now = SystemClock.uptimeMillis()
        // IMEs using deletion calls provide no key-up. Require a quiet interval between
        // gestures, updating this timestamp even while normal text is being deleted.
        val separatePress = lastImeDelete == Long.MIN_VALUE || now - lastImeDelete >= 400L
        lastImeDelete = now
        if (!text.isNullOrEmpty()) return false
        if (separatePress && !backspaceDown) onEmptyBackspace?.invoke()
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            backspaceDown = false
            consumeBackspace = false
            lastImeDelete = Long.MIN_VALUE
        }
    }
}
