package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLabelRankingTest {
    @Test
    fun `contact names rank best first and update with more typing`() {
        val names = listOf("Joanne", "Mary Ann", "Anna Smith", "Ann")
        assertEquals(
            listOf("Ann", "Anna Smith", "Mary Ann", "Joanne"),
            names.sortedWith(appLabelRanking("ann")),
        )
        assertEquals("Anna Smith", names.sortedWith(appLabelRanking("anna")).first())
    }

    @Test
    fun `exact then prefix then word prefix then substring`() {
        val labels = listOf("My Maps", "Roadmaps", "Maps Go", "Maps")
        assertEquals(
            listOf("Maps", "Maps Go", "My Maps", "Roadmaps"),
            labels.sortedWith(appLabelRanking("maps")),
        )
    }

    @Test
    fun `ranking updates when the query changes`() {
        val labels = listOf("Maps Go", "Maps")
        assertEquals("Maps", labels.sortedWith(appLabelRanking("maps")).first())
        assertEquals("Maps Go", labels.sortedWith(appLabelRanking("maps g")).first())
    }

    @Test
    fun `ranking ignores case diacritics and separators`() {
        val labels = listOf("My Cafe", "Cafe Racer", "Café")
        assertEquals("Café", labels.sortedWith(appLabelRanking(" CAFE ")).first())
        assertEquals("Google Maps", listOf("Google Maps Go", "Google Maps")
            .sortedWith(appLabelRanking("googlemaps")).first())
    }

    @Test
    fun `ties preserve existing order including separator only queries`() {
        val labels = listOf("Camera", "Calculator")
        assertEquals(labels, labels.sortedWith(appLabelRanking("ca")))
        assertEquals(labels, labels.sortedWith(appLabelRanking("---")))
    }
}
