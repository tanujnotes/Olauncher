package app.olauncher.helper

import android.app.Notification
import android.service.notification.NotificationListenerService

class MusicListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
    }

    override fun onListenerDisconnected() {
        connected = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var connected: MusicListenerService? = null

        /**
         * Dismisses the media notification of [packageName], the same way swiping it away in the
         * notification shade would. Ongoing notifications are kept by the system, so this is
         * best effort only.
         */
        fun cancelMediaNotification(packageName: String) {
            val service = connected ?: return
            val active = runCatching { service.activeNotifications }.getOrNull() ?: return
            active.forEach { notification ->
                if (notification.packageName != packageName) return@forEach
                if (!notification.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return@forEach
                runCatching { service.cancelNotification(notification.key) }
            }
        }
    }
}
