package app.olauncher.data.weather

data class WeatherSnapshot(
    val temperature: Double,
    val weatherCode: Int,
    val locationName: String,
    val updatedAt: Long,
)

fun weatherSymbol(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "⛅"
    3 -> "☁"
    45, 48 -> "≋"
    in 51..57 -> "☂"
    in 61..67, in 80..82 -> "☔"
    in 71..77, 85, 86 -> "❄"
    in 95..99 -> "⚡"
    else -> "☁"
}
