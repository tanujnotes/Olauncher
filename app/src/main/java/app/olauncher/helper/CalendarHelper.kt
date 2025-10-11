package app.olauncher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import android.util.Log
import app.olauncher.data.CalendarEvent
import java.util.Calendar

object CalendarHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getNextCalendarEvent(context: Context): CalendarEvent? {
        Log.d("CalendarHelper", "Getting next calendar event")
        if (!hasCalendarPermission(context)) {
            Log.d("CalendarHelper", "No calendar permission")
            return null
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        )

        val currentTime = System.currentTimeMillis()
        val endTime = currentTime + (24 * 60 * 60 * 1000) // Next 24 hours

        val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) OR " +
                "(${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DTEND} >= ?)"
        val selectionArgs = arrayOf(
            currentTime.toString(),
            endTime.toString(),
            currentTime.toString(),
            currentTime.toString()
        )

        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val events = mutableListOf<CalendarEvent>()
                Log.d("CalendarHelper", "Cursor count: ${it.count}")
                
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: ""
                    val startTime = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    
                    // DTEND might be null for recurring events, use DURATION or default to 1 hour
                    val endIndex = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val endTimeValue = if (endIndex != -1 && !it.isNull(endIndex)) {
                        it.getLong(endIndex)
                    } else {
                        startTime + (60 * 60 * 1000) // Default to 1 hour duration
                    }
                    
                    val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))
                    val allDay = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1

                    Log.d("CalendarHelper", "Event: $title, Start: $startTime, AllDay: $allDay")
                    
                    // Skip all-day events or events without a title
                    if (title.isBlank() || allDay) {
                        Log.d("CalendarHelper", "Skipping event: blank title or all day")
                        continue
                    }

                    // For ongoing events, check if they're still active
                    if (startTime <= currentTime && endTimeValue > currentTime) {
                        events.add(
                            CalendarEvent(
                                id = id,
                                title = title,
                                startTime = startTime,
                                endTime = endTimeValue,
                                location = location,
                                allDay = allDay
                            )
                        )
                    } else if (startTime > currentTime) {
                        // Future event
                        events.add(
                            CalendarEvent(
                                id = id,
                                title = title,
                                startTime = startTime,
                                endTime = endTimeValue,
                                location = location,
                                allDay = allDay
                            )
                        )
                    }
                }

                // Return the first upcoming or ongoing event
                val nextEvent = events.firstOrNull()
                Log.d("CalendarHelper", "Returning event: ${nextEvent?.title}")
                return nextEvent
            }
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Error fetching calendar events", e)
            e.printStackTrace()
        }

        return null
    }

    fun formatEventTime(startTime: Long, endTime: Long, currentTime: Long = System.currentTimeMillis()): String {
        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = startTime
        }
        
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endTime
        }
        
        fun formatTime(calendar: Calendar): String {
            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            val hourDisplay = if (hour == 0) 12 else hour
            val minuteDisplay = if (minute < 10) "0$minute" else minute.toString()
            return "$hourDisplay:$minuteDisplay $amPm"
        }
        
        // Check if event is ongoing
        if (startTime <= currentTime && endTime > currentTime) {
            return "Until ${formatTime(endCalendar)}"
        }
        
        // Check if event is today
        val todayCalendar = Calendar.getInstance()
        if (startCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR) &&
            startCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR)) {
            return formatTime(startCalendar)
        }
        
        // Event is tomorrow or later
        return "Tomorrow ${formatTime(startCalendar)}"
    }

    fun truncateTitle(title: String, maxLength: Int = 25): String {
        if (title.length <= maxLength) return title
        
        // Remove common filler words to save space
        val fillerWords = listOf("meeting", "call", "with", "and", "the", "for")
        var cleanTitle = title
        
        // Only remove filler words if the title is significantly longer
        if (title.length > maxLength + 10) {
            fillerWords.forEach { filler ->
                cleanTitle = cleanTitle.replace("\\b$filler\\b".toRegex(RegexOption.IGNORE_CASE), "")
                    .replace("\\s+".toRegex(), " ").trim()
            }
        }
        
        // If still too long, truncate at word boundary when possible
        if (cleanTitle.length > maxLength) {
            val truncated = cleanTitle.take(maxLength - 3)
            val lastSpace = truncated.lastIndexOf(' ')
            
            return if (lastSpace > maxLength / 2) {
                // Truncate at word boundary if we're not losing too much
                truncated.take(lastSpace) + "..."
            } else {
                // Otherwise truncate at character limit
                truncated + "..."
            }
        }
        
        return cleanTitle
    }
}