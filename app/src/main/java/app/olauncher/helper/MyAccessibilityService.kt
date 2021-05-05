package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import app.olauncher.R
import app.olauncher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Prefs(applicationContext).lockModeOn = true
        showToastShort(this, "Double tap to lock enabled")
        super.onServiceConnected()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val source: AccessibilityNodeInfo = event.source ?: return
        if ((source.className == "android.widget.FrameLayout") and
            (source.contentDescription == getString(R.string.lock_layout_description))
        )
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onInterrupt() {

    }
}