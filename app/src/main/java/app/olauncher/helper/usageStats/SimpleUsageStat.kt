package app.olauncher.helper.usageStats

import android.app.usage.UsageStats
import android.icu.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A data class to hold simplified usage statistics.
 *
 * @property day The day since epoch that this object concerns.
 * @property timeUsed The time that the application has been in the foreground in milliseconds on this day.
 * @property applicationId The package name of the application that this object concerns.
 */
data class SimpleUsageStat(
    val day: Long,
    val timeUsed: Long,
    val applicationId: String
) {
    /**
     * Secondary constructor to create a SimpleUsageStat from the system's [UsageStats].
     */
    constructor(systemUsageStat: UsageStats) : this(
        day = getEpochDay(systemUsageStat.lastTimeUsed),
        timeUsed = systemUsageStat.totalTimeInForeground,
        applicationId = systemUsageStat.packageName
    )

    companion object {
        /**
         * Converts a list of system [UsageStats] to a list of [SimpleUsageStat].
         * This function is kept for direct compatibility with the original Java static method.
         */
        @JvmStatic
        fun asSimpleStats(usageStats: List<UsageStats>): List<SimpleUsageStat> {
            return usageStats.map { SimpleUsageStat(it) }
        }

        /**
         * Calculates the epoch day from a timestamp in milliseconds, compatible with API 24+.
         * It manually calculates the day by using integer division on the milliseconds.
         */
        private fun getEpochDay(lastTimeUsed: Long): Long {
            val timeZoneOffset = Calendar.getInstance().timeZone.getOffset(lastTimeUsed)
            return TimeUnit.MILLISECONDS.toDays(lastTimeUsed + timeZoneOffset)
        }
    }
}

