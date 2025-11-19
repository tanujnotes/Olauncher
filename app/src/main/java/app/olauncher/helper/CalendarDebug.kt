package app.olauncher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat

object CalendarDebug {
    
    fun debugCalendars(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("CalendarDebug", "No calendar permission")
            return
        }
        
        // List all calendars
        val calendarProjection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )
        
        val calendarCursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            calendarProjection,
            null,
            null,
            null
        )
        
        calendarCursor?.use { cursor ->
            Log.d("CalendarDebug", "Found ${cursor.count} calendars")
            while (cursor.moveToNext()) {
                val calId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                val accountName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME))
                Log.d("CalendarDebug", "Calendar: ID=$calId, Name=$displayName, Account=$accountName")
            }
        }
        
        // List all events (no time filter)
        val eventProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.CALENDAR_ID
        )
        
        val eventCursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            eventProjection,
            null,
            null,
            CalendarContract.Events.DTSTART + " DESC"
        )
        
        eventCursor?.use { cursor ->
            Log.d("CalendarDebug", "Found ${cursor.count} total events")
            var count = 0
            while (cursor.moveToNext() && count < 5) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "No title"
                val startTime = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val calId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                Log.d("CalendarDebug", "Event: $title, Start: $startTime, Calendar: $calId")
                count++
            }
        }
    }
}