package app.olauncher.helper

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window to be transparent
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast("Invalid pin request")
        }

        finish()
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast("Widgets are not supported")

            else -> showToast("Unknown action not supported")
        }
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
}