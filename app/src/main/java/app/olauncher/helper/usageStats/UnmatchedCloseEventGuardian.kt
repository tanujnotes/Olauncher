package app.olauncher.helper.usageStats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log

/**
 * “…a diminutive Guardian who traveled backward through time…”
 *
 * Guards [EventLogWrapper] against Faulty unmatched close events (per
 * [the documentation](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios))
 * by seeking backwards through time and scanning for the open event.
 */
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 // 24 hours
    }

    /**
     * @param event      Event to validate
     * @param queryStart Timestamp at which original query o
     * @return True if the event is valid, false otherwise
     */
    fun test(event: UsageEvents.Event, queryStart: Long): Boolean {
        val events = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart)

        // Reusable event object for iteration
        val e = UsageEvents.Event()

        // Track whether the package is currently in foreground or background
        var open = false // Not open until opened

        while (events.hasNextEvent()) {
            events.getNextEvent(e)

            if (e.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                // Consider all apps closed after startup according to docs
                open = false
            }

            // Only consider events concerning our package otherwise
            if (event.packageName == e.packageName) {
                when (e.eventType) {
                    // see EventLogWrapper
                    UsageEvents.Event.ACTIVITY_RESUMED, 4 -> {
                        open = true
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, 3 -> {
                        if (e.timeStamp != event.timeStamp) {
                            // Don't flip to 'false' if we're looking at the original event itself
                            open = false
                        }
                    }
                }
            }
        }

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")

        // Event is valid if it was previously opened (within SCAN_INTERVAL)
        return open
    }
}
