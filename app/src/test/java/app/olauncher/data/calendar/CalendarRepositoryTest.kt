package app.olauncher.data.calendar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarRepositoryTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `seven day window starts today and excludes same weekday next week`() {
        val now = calendar(2026, Calendar.NOVEMBER, 6, 14, 30).timeInMillis

        val (start, end) = CalendarRepository.eventWindow(now, 7)

        assertEquals(calendar(2026, Calendar.NOVEMBER, 6, 0, 0).timeInMillis, start)
        assertEquals(calendar(2026, Calendar.NOVEMBER, 13, 0, 0).timeInMillis, end)
    }

    @Test
    fun `window uses calendar days across daylight saving changes`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val now = calendar(2026, Calendar.MARCH, 7, 12, 0).timeInMillis

        val (start, end) = CalendarRepository.eventWindow(now, 2)

        assertEquals(calendar(2026, Calendar.MARCH, 7, 0, 0).timeInMillis, start)
        assertEquals(calendar(2026, Calendar.MARCH, 9, 0, 0).timeInMillis, end)
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }
}
