package app.olauncher.data.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSnapshotTest {
    @Test fun mapsRepresentativeWmoCodes() {
        assertEquals("☀", weatherSymbol(0))
        assertEquals("⛅", weatherSymbol(2))
        assertEquals("☁", weatherSymbol(3))
        assertEquals("☔", weatherSymbol(63))
        assertEquals("❄", weatherSymbol(75))
        assertEquals("⚡", weatherSymbol(95))
    }
}
