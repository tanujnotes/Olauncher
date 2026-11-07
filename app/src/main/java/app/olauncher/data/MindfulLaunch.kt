package app.olauncher.data

import android.os.UserHandle

object MindfulLaunchDelay {
    const val INCREMENT_SECONDS = 2
    const val MAX_DELAY_SECONDS = 10

    fun secondsFor(openingNumber: Int): Int =
        ((openingNumber - 1).coerceAtLeast(0) * INCREMENT_SECONDS)
            .coerceAtMost(MAX_DELAY_SECONDS)
}

data class PendingAppLaunch(
    val appName: String,
    val packageName: String,
    val activityClassName: String?,
    val user: UserHandle,
    val openingNumber: Int,
    val delaySeconds: Int,
)
