package app.olauncher.helper.usageStats

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.icu.util.Calendar
import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import kotlin.math.max
import kotlin.math.min

class EventLogWrapper(private val context: Context) {
    private val usageStatsManager by lazy { context.getSystemService("usagestats") as UsageStatsManager }
    private val guardian = UnmatchedCloseEventGuardian(usageStatsManager)


    /**
     * Collects event information from system to calculate and aggregate precise
     * foreground time statistics for the specified period.
     *
     * Comments refer to the cases from
     * [the documentation.](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios)
     *
     * @param start First point in time to include in results
     * @param end   Last point in time to include in results
     * @return A list of foreground stats for the specified period
     */
    fun getForegroundStatsByTimestamps(start: Long, end: Long): List<ComponentForegroundStat> {
        var queryStart = start // Can be mutated by DEVICE_STARTUP event

        /*
         * Because sometimes, open events do not have close events when they should, as a hack / workaround,
         * we query the apps currently in the foreground and match them against the apps that are currently
         * in the foreground if the query start date is very recent or in the future. Thus, we are using this
         * to tell apart True from Faulty unmatched open events.
         *
         * We query processes in the beginning of this method call in case querying the event log takes a
         * little longer.
         */
        val foregroundProcesses = mutableListOf<String>()
        if (end >= System.currentTimeMillis() - 1500) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.forEach { appProcess ->
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                ) {
                    if (context.packageName != appProcess.processName) {
                        foregroundProcesses.add(appProcess.processName)
                    }
                }
            }
        }

        // Assumption: events are ordered chronologically
        val events = usageStatsManager.queryEvents(queryStart, end)

        /* …except that sometimes, the events that are close to each other are swapped in a way that
         * breaks the assumption that all end times which do not have a matching start time have
         * started before start. We handle those as Duplicate close event and Duplicate open event.
         * Therefore, we keep null entries in our moveToForegroundMap instead of removing the entries
         * to prevent apps that had been opened previously in a period from being counted as "opened
         * before start" (as they are not a True unmatched close event).
         */
        // Map components to the last moveToForeground event. Null value means it was closed.
        val moveToForegroundMap = mutableMapOf<AppClass, Long?>()
        val componentForegroundStats = mutableListOf<ComponentForegroundStat>()

        // Iterate over events
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (context.packageName == event.packageName) continue

            val appClass = AppClass(event.packageName, event.className)

            when (event.eventType) {
                /*
                 * "An event type denoting that an android.app.Activity moved to the foreground."
                 * (old definition: "An event type denoting that a component moved to the foreground.")
                 */
                UsageEvents.Event.ACTIVITY_RESUMED,
                    /*
                     * public static final int android.app.usage.UsageEvents.Event.CONTINUE_PREVIOUS_DAY = 4;
                     * (annotated as @hide)
                     * "An event type denoting that a component was in the foreground the previous day.
                     * This is effectively treated as a MOVE_TO_FOREGROUND."
                     */
                4 -> {
                    // Store open timestamp in map, overwriting earlier timestamps in case of Duplicate open event
                    moveToForegroundMap[appClass] = event.timeStamp
                }

                /*
                 * "An event type denoting that an android.app.Activity moved to the background."
                 * (old definition: "An event type denoting that a component moved to the background.")
                 */
                UsageEvents.Event.ACTIVITY_PAUSED,
                    /*
                     * "An activity becomes invisible on the UI, corresponding to Activity.onStop()
                     * of the activity's lifecycle."
                     */
                UsageEvents.Event.ACTIVITY_STOPPED,
                    /*
                     * public static final int android.app.usage.UsageEvents.Event.END_OF_DAY = 3;
                     * (annotated as @hide)
                     * "An event type denoting that a component was in the foreground when the stats
                     * rolled-over. This is effectively treated as a {@link #MOVE_TO_BACKGROUND}."
                     */
                3 -> {
                    val eventBeginTime: Long? = moveToForegroundMap[appClass]?.also {
                        // Open and close events in order. Mark as closed.
                        moveToForegroundMap[appClass] = null
                    } ?: if (
                    // App has not been in this query yet (test for Duplicate close event)
                        moveToForegroundMap.keys.none { it.packageName == event.packageName } &&
                        // Test if this unmatched close event is True by asking the Guardian to scan for it
                        guardian.test(event, queryStart)
                    ) {
                        // Identified as True unmatched close event. Take start as a starting timestamp.
                        queryStart
                    } else {
                        null // Ignore Faulty unmatched close event
                    }

                    if (eventBeginTime != null) {
                        // Check if another of the app's components have moved to the foreground in the meantime
                        val endTime = moveToForegroundMap.entries
                            .filter { (key, value) -> key.packageName == event.packageName && value != null }
                            .mapNotNull { it.value }
                            .minOrNull() ?: event.timeStamp

                        componentForegroundStats.add(
                            ComponentForegroundStat(eventBeginTime, endTime, event.packageName)
                        )
                    }
                }

                /*
                 * "An event type denoting that the Android runtime underwent a shutdown process..."
                 */
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    // Per docs: iterate over remaining start events and treat them as closed
                    moveToForegroundMap.forEach { (key, value) ->
                        if (value != null) { // If it's a remaining start event
                            componentForegroundStats.add(
                                ComponentForegroundStat(value, event.timeStamp, key.packageName)
                            )
                            // Set entire app to closed
                            moveToForegroundMap.keys
                                .filter { it.packageName == key.packageName }
                                .forEach { samePackageKey -> moveToForegroundMap[samePackageKey] = null }
                        }
                    }
                }

                /*
                 * "An event type denoting that the Android runtime started up..."
                 */
                UsageEvents.Event.DEVICE_STARTUP -> {
                    // Per docs: remove pending open events
                    moveToForegroundMap.clear()

                    /* No package could be open longer than a reboot. Thus, we set the `start`
                     * timestamp to the boot event's timestamp in case we later assume that a
                     * package has been open "since the start of the period". It is not logical
                     * that this would happen but we can never know with this API.
                     */
                    queryStart = event.timeStamp
                }
            }
        }

        // Iterate over remaining start events
        moveToForegroundMap.forEach { (key, value) ->
            if (value != null) { // If it's a remaining start event
                // Test if it's a foreground app (True unmatched open event)
                if (foregroundProcesses.any { it.contains(key.packageName) }) {
                    componentForegroundStats.add(
                        ComponentForegroundStat(
                            value,
                            min(System.currentTimeMillis(), end),
                            key.packageName
                        )
                    )
                }
                // If app is not in foreground, drop event (Assume Faulty unmatched open event)
            }
        }

        /* If nothing happened during the timespan but there is an app in the foreground,
         * then this app was used the whole period time and there was No event for it.
         * Because the foreground applications API call is documented as not to be used
         * for purposes like this, we first query whether the process name is a valid
         * package name and if not, we drop it.
         */
        if (moveToForegroundMap.isEmpty()) {
            val packageManager = context.packageManager
            foregroundProcesses.forEach { foregroundProcess ->
                if (packageManager.getLaunchIntentForPackage(foregroundProcess) != null) {
                    componentForegroundStats.add(
                        ComponentForegroundStat(
                            queryStart,
                            min(System.currentTimeMillis(), end),
                            foregroundProcess
                        )
                    )
                    Log.d("EventLogWrapper", "Assuming that application $foregroundProcess has been used the whole query time")
                }
            }
        }

        return componentForegroundStats
    }

    /**
     * Collects event information from system to calculate and aggregate precise
     * foreground time statistics for the specified relative day.
     *
     * @param offset Day to query back in time relative to today
     */
    fun getForegroundStatsByRelativeDay(offset: Int): List<ComponentForegroundStat> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        // Set to start of the day
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val beginTime = cal.timeInMillis

        // Set to start of the next day
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = cal.timeInMillis

        return getForegroundStatsByTimestamps(beginTime, endTime)
    }

    /**
     * Collects event information from system to calculate and aggregate precise
     * foreground time statistics starting at `start` and ending at
     * the end of the day that contains `start`.
     *
     * @param start Starting time of query and point in time in day to query
     */
    fun getForegroundStatsByPartialDay(start: Long): List<ComponentForegroundStat> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        // Set to start of the next day
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val endTime = cal.timeInMillis
        return getForegroundStatsByTimestamps(start, endTime)
    }

    /**
     * Takes a list of foreground stats and aggregates them to usage stats.
     * Assumes all provided usage stats to be on the same day.
     *
     * @param endConsumer Consumer that accepts ending times of component
     *                    foreground stats with their package name
     */
    @JvmOverloads
    fun aggregateForegroundStats(
        foregroundStats: List<ComponentForegroundStat>,
        endConsumer: BiConsumer<String, Long>? = null
    ): List<SimpleUsageStat> {
        if (foregroundStats.isEmpty()) return emptyList()

        // Group by package name and sum the duration for each
        val applicationTotalTime = foregroundStats
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.sumOf { it.endTime - it.beginTime } }

        val firstBeginTime = foregroundStats.first().beginTime
        val timeZoneOffset = Calendar.getInstance().timeZone.getOffset(firstBeginTime)
        val day = TimeUnit.MILLISECONDS.toDays(firstBeginTime + timeZoneOffset)

        // Optionally consume end times
        endConsumer?.let { consumer ->
            foregroundStats.forEach { consumer.accept(it.packageName, it.endTime) }
        }

        // Map the aggregated times to SimpleUsageStat objects
        return applicationTotalTime.map { (packageName, totalTime) ->
            SimpleUsageStat(day, totalTime, packageName)
        }
    }

    /**
     * Collects <b>all</b> event information from system to calculate and aggregate precise
     * foreground time statistics for the provided day and presents this information as
     * [SimpleUsageStat]s.
     *
     * @param day Day since epoch
     */
    private fun getForegroundStatsByDay(day: Long): List<ComponentForegroundStat> {
        val cal = Calendar.getInstance()
        val timeZoneOffset = cal.timeZone.getOffset(System.currentTimeMillis())

        // To convert epoch day to milliseconds, we first get the UTC millis and then adjust for timezone
        val start = TimeUnit.DAYS.toMillis(day) - timeZoneOffset
        val end = TimeUnit.DAYS.toMillis(day + 1) - timeZoneOffset
        return getForegroundStatsByTimestamps(start, end)
    }

    /**
     * Collects <b>all</b> event information from system to calculate and aggregate precise
     * foreground time statistics and presents this information as [SimpleUsageStat]s.
     * **This method call causes lag** if called with a low since value.
     *
     * @param daySince Return data from this day on
     * @param endConsumer Consumer that accepts ending times of component
     *                    foreground stats with their package name
     */
    fun getAllSimpleUsageStats(daySince: Long, endConsumer: BiConsumer<String, Long>): List<SimpleUsageStat> {
        val usageStats = mutableListOf<SimpleUsageStat>()
        var currentDay = daySince

        val timeZoneOffset = Calendar.getInstance().timeZone.getOffset(System.currentTimeMillis())
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() + timeZoneOffset)

        // Maximum event log size
        currentDay = max(today - 10, currentDay)

        while (currentDay <= today) {
            val foregroundStats = getForegroundStatsByDay(currentDay)
            usageStats.addAll(aggregateForegroundStats(foregroundStats, endConsumer))
            currentDay++
        }

        return usageStats
    }

    /**
     * Returns only usage statistics that have not been counted yet for
     * only the day that contains `timestamp`
     *
     * @param endConsumer Consumer that accepts ending times of component
     *                    foreground stats with their package name
     */
    fun getIncrementalSimpleUsageStats(timestamp: Long, endConsumer: BiConsumer<String, Long>): List<SimpleUsageStat> {
        val foregroundStats = getForegroundStatsByPartialDay(timestamp)
        return aggregateForegroundStats(foregroundStats, endConsumer)
    }

    fun aggregateSimpleUsageStats(usageStats: List<SimpleUsageStat>): Long {
        return usageStats.sumOf { it.timeUsed }
    }
    /**
     * Stores a class name and its corresponding package.
     */
    private data class AppClass(val packageName: String, val className: String?)
}