package app.olauncher.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

class CalendarRepository(private val context: Context) {
    fun upcoming(now: Long = System.currentTimeMillis(), days: Int = 7, limit: Int = 3): List<CalendarEvent> {
        val (start, end) = eventWindow(now, days)
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        return context.contentResolver.query(
            builder.build(), projection,
            "${CalendarContract.Instances.VISIBLE}=1 AND ${CalendarContract.Instances.STATUS}!=? AND ${CalendarContract.Instances.END}>? AND ${CalendarContract.Instances.BEGIN}<?",
            arrayOf(CalendarContract.Events.STATUS_CANCELED.toString(), now.toString(), end.toString()),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val result = mutableListOf<CalendarEvent>()
            while (cursor.moveToNext() && result.size < limit) {
                result += CalendarEvent(
                    id = cursor.getLong(0),
                    title = cursor.getString(1)?.ifBlank { "(Sem título)" } ?: "(Sem título)",
                    begin = cursor.getLong(2),
                    end = cursor.getLong(3),
                    allDay = cursor.getInt(4) == 1,
                    location = cursor.getString(5).orEmpty(),
                )
            }
            result
        }.orEmpty()
    }

    companion object {
        internal fun eventWindow(now: Long, days: Int): Pair<Long, Long> {
            require(days > 0)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, days)
            return start to calendar.timeInMillis
        }
    }
}
