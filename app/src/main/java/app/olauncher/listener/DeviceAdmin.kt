package app.olauncher.listener

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast


class DeviceAdmin : DeviceAdminReceiver() {
    fun onEnabled(intent: Intent?, context: Context) {
        super.onEnabled(context, intent!!)
        Toast.makeText(context, "Enabled", Toast.LENGTH_SHORT).show()
    }

    fun onDisabled(intent: Intent?, context: Context) {
        super.onDisabled(context, intent!!)
        Toast.makeText(context, "Disabled", Toast.LENGTH_SHORT).show()
    }
}