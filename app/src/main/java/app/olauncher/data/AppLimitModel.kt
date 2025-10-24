package app.olauncher.data

import org.json.JSONObject

data class AppLimitModel(
        val appPackage: String,
        val appName: String,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val lockDuringLimit: Boolean
) {
  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("appPackage", appPackage)
      put("appName", appName)
      put("startHour", startHour)
      put("startMinute", startMinute)
      put("endHour", endHour)
      put("endMinute", endMinute)
      put("lockDuringLimit", lockDuringLimit)
    }
  }

  companion object {
    fun fromJson(json: JSONObject): AppLimitModel {
      return AppLimitModel(
              appPackage = json.getString("appPackage"),
              appName = json.getString("appName"),
              startHour = json.getInt("startHour"),
              startMinute = json.getInt("startMinute"),
              endHour = json.getInt("endHour"),
              endMinute = json.getInt("endMinute"),
              lockDuringLimit = json.getBoolean("lockDuringLimit")
      )
    }
  }

  fun getStartTimeString(): String {
    return formatTime12Hour(startHour, startMinute)
  }

  fun getEndTimeString(): String {
    return formatTime12Hour(endHour, endMinute)
  }

  private fun formatTime12Hour(hour: Int, minute: Int): String {
    val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format("%d:%02d %s", hour12, minute, amPm)
  }

  fun isCurrentlyBlocked(): Boolean {
    val calendar = java.util.Calendar.getInstance()
    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
    val currentTimeInMinutes = currentHour * 60 + currentMinute
    val startTimeInMinutes = startHour * 60 + startMinute
    val endTimeInMinutes = endHour * 60 + endMinute

    return if (startTimeInMinutes <= endTimeInMinutes) {
      // Normal case: start before end (e.g., 09:00 to 17:00)
      currentTimeInMinutes in startTimeInMinutes until endTimeInMinutes
    } else {
      // Overnight case: start after end (e.g., 22:00 to 06:00)
      currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
    }
  }
}
