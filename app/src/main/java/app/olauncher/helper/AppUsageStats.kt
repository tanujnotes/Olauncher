package app.olauncher.helper

import android.os.Build
import androidx.annotation.RequiresApi

class AppUsageStats(
    val lastTimeUsedMillis: Long,
    val totalTimeInForegroundMillis: Long,
    @get:RequiresApi(Build.VERSION_CODES.Q) val lastTimeForegroundServiceUsedMillis: Long,
    @get:RequiresApi(Build.VERSION_CODES.Q) val totalTimeForegroundServiceUsedMillis: Long,
)

class AppUsageStatsBucket {
    var startMillis: Long = 0L
    var endMillis: Long = 0L
    var totalTime: Long = 0L

    fun addTotalTime() {
        this.totalTime += endMillis - startMillis
    }
}