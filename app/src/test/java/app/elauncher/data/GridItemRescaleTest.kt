package app.elauncher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Guards the one-time 80dp -> 48dp grid rescale.
 *
 * The failure this exists for is silent: items are stored as cell *counts*, so inverting the ratio
 * (48/80 instead of 80/48) still produces plausible-looking integers, just at ~36% of the correct
 * size, and the migration is guarded so it can never re-run to correct itself.
 */
class GridItemRescaleTest {

    private val old = Constants.Grid.LEGACY_CELL_SIZE_DP // 80
    private val new = Constants.Grid.CELL_SIZE_DP // 48

    private fun item(col: Int, row: Int, spanX: Int, spanY: Int) = GridItem(
        type = GridItemType.APP,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        appName = "Camera",
        appPackage = "com.android.camera",
    )

    /**
     * [appListMigrationDone] is pre-set so these tests exercise the rescale alone - otherwise the
     * App List migration (AppListMigrationTest's subject) also runs on the first read and rewrites
     * the very items being asserted on.
     */
    private fun buildPrefs(screenWidthDp: Int = 400, screenHeightDp: Int = 800): Prefs {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        // Configuration's screen size fields are plain public fields: Mockito only proxies methods,
        // so assigning them directly on the mock works and is read back as-is.
        configuration.screenWidthDp = screenWidthDp
        configuration.screenHeightDp = screenHeightDp
        `when`(resources.configuration).thenReturn(configuration)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(FakeSharedPreferences())
        return Prefs(context).apply { appListMigrationDone = true }
    }

    @Test
    fun `rescale multiplies cell counts by old over new cell size`() {
        // 80/48 = 1.6667: round(1*r)=2, round(2*r)=3, round(3*r)=5, round(1*r)=2
        val rescaled = item(col = 1, row = 2, spanX = 3, spanY = 1)
            .rescaleForCellSize(old, new, columnCount = 20, rowCount = 20)

        assertEquals(2, rescaled.col)
        assertEquals(3, rescaled.row)
        assertEquals(5, rescaled.spanX)
        assertEquals(2, rescaled.spanY)
    }

    @Test
    fun `a smaller cell grows cell counts rather than shrinking them`() {
        // The inverted-ratio bug in reverse: with a 0.6 multiplier every one of these would be
        // smaller than it started, and the item would occupy ~36% of its old physical area.
        val original = item(col = 3, row = 4, spanX = 5, spanY = 3)

        val rescaled = original.rescaleForCellSize(old, new, columnCount = 30, rowCount = 30)

        assertTrue(rescaled.col > original.col)
        assertTrue(rescaled.row > original.row)
        assertTrue(rescaled.spanX > original.spanX)
        assertTrue(rescaled.spanY > original.spanY)
    }

    @Test
    fun `physical size and position are preserved to within one cell`() {
        val original = item(col = 2, row = 3, spanX = 4, spanY = 2)

        val rescaled = original.rescaleForCellSize(old, new, columnCount = 30, rowCount = 30)

        listOf(
            original.col * old to rescaled.col * new,
            original.row * old to rescaled.row * new,
            original.spanX * old to rescaled.spanX * new,
            original.spanY * old to rescaled.spanY * new,
        ).forEach { (beforeDp, afterDp) ->
            assertTrue("$beforeDp dp -> $afterDp dp", kotlin.math.abs(beforeDp - afterDp) <= new)
        }
    }

    @Test
    fun `rescaled item is clamped inside the new grid bounds`() {
        // A full-width 5-column item on a 5-column grid would rescale to 8 columns.
        val rescaled = item(col = 0, row = 0, spanX = 5, spanY = 1)
            .rescaleForCellSize(old, new, columnCount = 7, rowCount = 10)

        assertEquals(7, rescaled.spanX)
        assertEquals(0, rescaled.col)
        assertTrue(rescaled.col + rescaled.spanX <= 7)
        assertTrue(rescaled.row + rescaled.spanY <= 10)
    }

    @Test
    fun `an item near the far edge stays inside the grid`() {
        val rescaled = item(col = 6, row = 8, spanX = 2, spanY = 2)
            .rescaleForCellSize(old, new, columnCount = 8, rowCount = 12)

        assertTrue(rescaled.col >= 0)
        assertTrue(rescaled.row >= 0)
        assertTrue(rescaled.col + rescaled.spanX <= 8)
        assertTrue(rescaled.row + rescaled.spanY <= 12)
    }

    @Test
    fun `rescaling to the same cell size changes nothing`() {
        val original = item(col = 1, row = 1, spanX = 2, spanY = 2)

        assertEquals(original, original.rescaleForCellSize(new, new, columnCount = 10, rowCount = 10))
    }

    @Test
    fun `rescale covers every item on every page`() {
        val pages = listOf(
            Page("p1", "Home", mutableListOf(item(1, 1, 1, 1))),
            Page("p2", "Work", mutableListOf(item(0, 2, 3, 1), item(2, 0, 1, 1))),
        )

        val rescaled = pages.rescaleForCellSize(old, new, columnCount = 20, rowCount = 20)

        assertEquals(listOf(1, 3, 1), pages.flatMap { page -> page.items.map { it.spanX } })
        assertEquals(listOf(2, 5, 2), rescaled.flatMap { page -> page.items.map { it.spanX } })
    }

    @Test
    fun `stored pages are rescaled once on read and never again`() {
        val prefs = buildPrefs()
        // Write pre-migration data the way an older install left it: cell counts in 80dp units,
        // with no recorded cell size at all.
        prefs.pages = listOf(Page("p1", "Home", mutableListOf(item(1, 2, 3, 1))))
        assertEquals(Constants.Grid.LEGACY_CELL_SIZE_DP, prefs.gridCellSizeDp)

        val migrated = prefs.pages.single().items.single()

        assertEquals(2, migrated.col)
        assertEquals(3, migrated.row)
        assertEquals(5, migrated.spanX)
        assertEquals(2, migrated.spanY)
        assertEquals(Constants.Grid.CELL_SIZE_DP, prefs.gridCellSizeDp)

        // Second read must be a no-op: re-running would rescale the already-rescaled values.
        val reRead = prefs.pages.single().items.single()
        assertEquals(migrated, reRead)
    }

    @Test
    fun `pages created after the migration are not rescaled`() {
        val prefs = buildPrefs()
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
        val page = Page("p1", "Home", mutableListOf(item(1, 2, 3, 1)))

        prefs.pages = listOf(page)

        assertEquals(page.items.single(), prefs.pages.single().items.single())
    }

    @Test
    fun `defaultColumnCount fits inside the measured grid width`() {
        val screenWidthDp = 411 // Pixel 9 Pro, portrait
        val prefs = buildPrefs(screenWidthDp = screenWidthDp)

        val usableWidthDp = screenWidthDp - Constants.Grid.HORIZONTAL_MARGIN_DP

        assertTrue(prefs.defaultColumnCount() * Constants.Grid.CELL_SIZE_DP <= usableWidthDp)
    }
}
