package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MindfulLaunchDelayTest {
    @Test
    fun `first opening has no delay and following openings grow by two seconds`() {
        assertEquals(0, MindfulLaunchDelay.secondsFor(1))
        assertEquals(2, MindfulLaunchDelay.secondsFor(2))
        assertEquals(4, MindfulLaunchDelay.secondsFor(3))
        assertEquals(8, MindfulLaunchDelay.secondsFor(5))
    }

    @Test
    fun `delay is capped at ten seconds`() {
        assertEquals(10, MindfulLaunchDelay.secondsFor(6))
        assertEquals(10, MindfulLaunchDelay.secondsFor(20))
    }
}
