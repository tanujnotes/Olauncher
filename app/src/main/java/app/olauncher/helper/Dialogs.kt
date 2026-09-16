package app.olauncher.helper

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogBaseBinding

/**
 * Shows a popup menu hanging off the end edge of this view.
 * [configure] can add or tweak items before the menu is shown.
 */
fun View.showPopupMenu(
    @MenuRes menuRes: Int = 0,
    configure: (Menu) -> Unit = {},
    onItemClick: (MenuItem) -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.END)
    if (menuRes != 0) popup.menuInflater.inflate(menuRes, popup.menu)
    configure(popup.menu)
    popup.setOnMenuItemClickListener { item ->
        onItemClick(item)
        true
    }
    popup.show()
    return popup
}

/**
 * Builds a dialog using the app's own layout: a title row with a close icon,
 * an optional [message] or custom [content], and a text [action] at the end.
 * [content] receives the container so the inflated view keeps its XML margins.
 */
fun Context.createDialog(
    @StringRes title: Int,
    @StringRes action: Int,
    @StringRes message: Int = 0,
    @StringRes neutral: Int = 0,
    onNeutral: () -> Unit = {},
    onAction: () -> Unit = {},
    content: ((ViewGroup) -> View)? = null,
): AlertDialog {
    val builder = AlertDialog.Builder(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(builder.context))
    binding.tvTitle.setText(title)
    binding.tvAction.setText(action)
    if (message != 0) {
        binding.tvMessage.setText(message)
        binding.tvMessage.isVisible = true
    }
    if (neutral != 0) {
        binding.tvNeutral.setText(neutral)
        binding.tvNeutral.isVisible = true
    }
    content?.let {
        binding.contentContainer.addView(it(binding.contentContainer))
        binding.contentContainer.isVisible = true
    }
    val dialog = builder.setView(binding.root).create()
    binding.ivClose.setOnClickListener { dialog.dismiss() }
    binding.tvNeutral.setOnClickListener {
        onNeutral()
        dialog.dismiss()
    }
    binding.tvAction.setOnClickListener {
        onAction()
        dialog.dismiss()
    }
    return dialog
}

/** Title with a close icon, a message and a single action. */
fun Context.showMessageDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
): AlertDialog {
    val dialog = createDialog(title, action, message = message, onAction = onAction)
    dialog.showRespectingStatusBar()
    return dialog
}

/**
 * Shows the dialog without letting its window bring back a status bar
 * the user has hidden in settings.
 */
fun AlertDialog.showRespectingStatusBar() {
    val window = window
    if (window == null || Prefs(context).showStatusBar) {
        show()
        return
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    show()
    window.hideStatusBar()
    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}
