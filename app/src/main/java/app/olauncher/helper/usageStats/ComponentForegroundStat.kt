package app.olauncher.helper.usageStats

import java.util.Date

/**
 * Object representing a timespan that an application was in the foreground.
 *
 * @property beginTime The start of the timespan, in milliseconds since the epoch.
 * @property endTime The end of the timespan, in milliseconds since the epoch.
 * @property packageName The package name of the application.
 */
data class ComponentForegroundStat(
    val beginTime: Long,
    val endTime: Long,
    val packageName: String
) {
    /**
     * Overriding toString for a more readable log output, similar to the original Java class.
     * The default data class toString() would also work, but this one is more explicit
     * about formatting the timestamps as Instants.
     */
    override fun toString(): String {
        return "ComponentForegroundStat(" +
                "beginTime=${Date(beginTime)}, " +
                "endTime=${Date(endTime)}, " +
                "packageName='$packageName')"
    }
}
