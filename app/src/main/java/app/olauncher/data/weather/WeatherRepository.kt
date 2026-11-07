package app.olauncher.data.weather

import android.content.Context
import android.location.Geocoder
import android.location.Location
import app.olauncher.data.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository(private val context: Context) {
    private val prefs = Prefs(context)

    fun cached(): WeatherSnapshot? {
        if (prefs.weatherUpdatedAt == 0L) return null
        return WeatherSnapshot(prefs.weatherTemperature.toDouble(), prefs.weatherCode, prefs.weatherLocationName, prefs.weatherUpdatedAt)
    }

    fun load(location: Location, force: Boolean = false, locationName: String? = null): WeatherSnapshot {
        val cached = cached()
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < CACHE_MS) return cached
        val connection = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&temperature_unit=celsius&timezone=auto"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "Olauncher/${context.packageName}")
        val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        connection.disconnect()
        val current = json.getJSONObject("current")
        val name = locationName?.takeIf { it.isNotBlank() } ?: resolveName(location).ifBlank { "%.2f, %.2f".format(Locale.US, location.latitude, location.longitude) }
        return WeatherSnapshot(current.getDouble("temperature_2m"), current.getInt("weather_code"), name, System.currentTimeMillis()).also {
            prefs.weatherTemperature = it.temperature.toFloat()
            prefs.weatherCode = it.weatherCode
            prefs.weatherLocationName = it.locationName
            prefs.weatherUpdatedAt = it.updatedAt
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveName(location: Location): String = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()?.let { it.subLocality ?: it.locality ?: it.subAdminArea ?: it.adminArea }.orEmpty()
    }.getOrDefault("")

    companion object { const val CACHE_MS = 30L * 60L * 1000L }
}
