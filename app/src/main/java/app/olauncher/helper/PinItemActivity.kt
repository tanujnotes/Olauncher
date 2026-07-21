package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

@SuppressLint("NewApi")
class PinItemActivity : AppCompatActivity() {
    private var pinRequest: LauncherApps.PinItemRequest? = null
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }
        pendingWidgetId = savedInstanceState?.getInt(
            STATE_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val launcherApps = getSystemService(LauncherApps::class.java)
        pinRequest = launcherApps.getPinItemRequest(intent)
        val request = pinRequest
        if (request == null) {
            showToast("Invalid pin request")
            finish()
            return
        }

        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) handleRequestType(request)
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest).also { finish() }

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                confirmWidgetRequest(pinItemRequest)

            else -> {
                showToast("Unknown action not supported")
                finish()
            }
        }
    }

    private fun confirmWidgetRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val prefs = Prefs(this)
        if (prefs.widgetIdList.size >= Constants.Widget.MAX_WIDGETS) {
            showToast(getString(R.string.widget_limit_reached))
            finish()
            return
        }
        val info = pinItemRequest.getAppWidgetProviderInfo(this) ?: run {
            showToast(getString(R.string.widget_pin_failed))
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_widget_confirmation)
            .setMessage(info.loadLabel(packageManager))
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .setPositiveButton(R.string.add_widget_action) { _, _ ->
                beginWidgetPin(info.provider)
            }
            .show()
    }

    private fun beginWidgetPin(provider: android.content.ComponentName) {
        val hostManager = WidgetHostManager.get(this)
        pendingWidgetId = hostManager.allocateId()
        if (hostManager.bindIfAllowed(pendingWidgetId, provider)) {
            completeWidgetPin()
            return
        }
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        runCatching { startActivityForResult(bindIntent, Constants.REQUEST_CODE_BIND_WIDGET) }
            .onFailure { cancelWidgetPin() }
    }

    private fun completeWidgetPin() {
        val id = pendingWidgetId
        val accepted = pinRequest?.accept(
            Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }
        ) == true
        if (accepted) {
            val prefs = Prefs(this)
            prefs.widgetIdList = prefs.widgetIdList + id
            prefs.widgetCurrentPage = prefs.widgetIdList.lastIndex
            showToast(getString(R.string.widget_pinned))
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            finish()
        } else {
            cancelWidgetPin()
        }
    }

    private fun cancelWidgetPin() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetHostManager.get(this).deleteId(pendingWidgetId)
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        showToast(getString(R.string.widget_pin_failed))
        finish()
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            val message = when (success) {
                true -> "Shortcut pinned successfully"
                false -> "Failed to pin shortcut"
            }
            showToast(message)
        } else {
            showToast("Invalid shortcut info")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_WIDGET_ID, pendingWidgetId)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Constants.REQUEST_CODE_BIND_WIDGET) return
        if (resultCode == Activity.RESULT_OK) completeWidgetPin() else cancelWidgetPin()
    }

    companion object {
        private const val STATE_WIDGET_ID = "pendingWidgetId"
    }
}