package app.elauncher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Guards the one-time combined-clock+date -> CLOCK + DATE_TIME split.
 *
 * Same reasoning as AppListMigrationTest: this runs once, in place, against the only copy of the
 * user's real page layout, and the run-once guard means it can never correct itself on a later
 * load. The thing most worth pinning is *placement* - a split that puts the clock somewhere the
 * user did not leave it is the failure mode with no automatic recovery.
 */
class ClockDateSplitTest {

    private val columnCount = 8
    private val rowCount = 17

    private fun dateTimeItem(
        col: Int = 0,
        row: Int = 0,
        spanX: Int = 8,
        spanY: Int = 4,
        visibility: Int = Constants.DateTime.ON,
        showScreenTime: Boolean = false,
        alignment: Int = Gravity.START,
    ) = GridItem(
        type = GridItemType.DATE_TIME,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        alignment = alignment,
        showScreenTime = showScreenTime,
        dateTimeVisibility = visibility,
    )

    private fun appListItem(col: Int, row: Int, spanX: Int = 8, spanY: Int = 4) = GridItem(
        type = GridItemType.APP_LIST,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        appSlots = MutableList(spanY) { AppSlot() },
    )

    private fun List<GridItem>.split() = splitDateTimeIntoClockAndDate(columnCount, rowCount)

    private fun GridItem.overlapsItem(other: GridItem): Boolean =
        other.col < col + spanX && col < other.col + other.spanX &&
            other.row < row + spanY && row < other.row + other.spanY

    private fun buildPrefs(screenWidthDp: Int = 411, screenHeightDp: Int = 892): Prefs {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        configuration.screenWidthDp = screenWidthDp
        configuration.screenHeightDp = screenHeightDp
        `when`(resources.configuration).thenReturn(configuration)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(FakeSharedPreferences())
        return Prefs(context)
    }

    @Test
    fun `an ON item splits into a clock that keeps its position and a date directly below it`() {
        // Deliberately not at (0, 0): the clock has to keep the *item's* place, not find the first
        // free one, which at (0, 0) would look identical whether the code does that or not.
        val original = dateTimeItem(col = 0, row = 3, spanY = 4, alignment = Gravity.CENTER_HORIZONTAL)

        val split = listOf(original).split()

        assertEquals(2, split.size)
        val clock = split.single { it.type == GridItemType.CLOCK }
        val date = split.single { it.type == GridItemType.DATE_TIME }
        // The whole point of the placement rule - the clock stays exactly where it was.
        assertEquals(original.col, clock.col)
        assertEquals(original.row, clock.row)
        assertEquals(defaultClockSpanX(columnCount), clock.spanX)
        assertEquals(defaultClockSpanY(), clock.spanY)
        assertEquals(Gravity.CENTER_HORIZONTAL, clock.alignment)
        // Date stacked directly under it, at the height its own (now clock-free) content needs.
        assertEquals(original.col, date.col)
        assertEquals(original.row + defaultClockSpanY(), date.row)
        assertEquals(original.spanX, date.spanX)
        assertEquals(defaultDateTimeSpanY(showScreenTime = false), date.spanY)
        assertEquals(Gravity.CENTER_HORIZONTAL, date.alignment)
        assertTrue(!clock.overlapsItem(date))
    }

    @Test
    fun `the surviving date item keeps its screen time line and gets the taller span for it`() {
        val original = dateTimeItem(spanY = 4, showScreenTime = true)

        val date = listOf(original).split().single { it.type == GridItemType.DATE_TIME }

        assertTrue(date.showScreenTime)
        assertEquals(defaultDateTimeSpanY(showScreenTime = true), date.spanY)
    }

    @Test
    fun `a DATE_ONLY item survives in place with no clock split off`() {
        val original = dateTimeItem(
            col = 2,
            row = 4,
            spanX = 6,
            spanY = 3,
            visibility = Constants.DateTime.DATE_ONLY,
            showScreenTime = true,
        )

        val split = listOf(original).split()

        assertTrue(split.none { it.type == GridItemType.CLOCK })
        val date = split.single()
        assertEquals(GridItemType.DATE_TIME, date.type)
        assertEquals(2, date.col)
        assertEquals(4, date.row)
        assertEquals(6, date.spanX)
        assertEquals(defaultDateTimeSpanY(showScreenTime = true), date.spanY)
    }

    @Test
    fun `an OFF item with a screen time line survives as a date item`() {
        // Disclosed fidelity change: visibility is presence-only from here on, so this item's date
        // line becomes visible where the old tri-state hid it. Better than dropping an item whose
        // screen-time line the user could see - see splitDateTimeIntoClockAndDate's kdoc.
        val original = dateTimeItem(
            col = 1,
            row = 2,
            visibility = Constants.DateTime.OFF,
            showScreenTime = true,
        )

        val split = listOf(original).split()

        assertTrue(split.none { it.type == GridItemType.CLOCK })
        val date = split.single()
        assertEquals(GridItemType.DATE_TIME, date.type)
        assertEquals(1, date.col)
        assertEquals(2, date.row)
        assertTrue(date.showScreenTime)
    }

    @Test
    fun `an OFF item with no screen time line is dropped entirely`() {
        val original = dateTimeItem(visibility = Constants.DateTime.OFF, showScreenTime = false)
        val neighbour = appListItem(col = 0, row = 6)

        val split = listOf(original, neighbour).split()

        // Nothing of it was visible before the split either, so there is nothing to keep.
        assertEquals(listOf(neighbour), split)
    }

    @Test
    fun `items of every other type pass through untouched and in order`() {
        val appList = appListItem(col = 0, row = 8)
        val widget = GridItem(GridItemType.WIDGET, col = 0, row = 13, spanX = 4, spanY = 3, appWidgetId = 7)
        val legacyApp = GridItem(GridItemType.APP, col = 4, row = 13, spanX = 4, spanY = 1, appName = "Phone")

        val split = listOf(appList, widget, legacyApp).split()

        assertEquals(listOf(appList, widget, legacyApp), split)
    }

    @Test
    fun `when the stack cannot stay in place the date holds its spot and the clock finds free space`() {
        // A hand-shrunk combined item with an App List packed directly beneath it: the clock fits at
        // the original position, but the date would then land on rows 2-3, inside the App List.
        val original = dateTimeItem(col = 0, row = 0, spanY = 2)
        val appList = appListItem(col = 0, row = 2, spanY = 4) // rows 2-5

        val split = listOf(original, appList).split()

        assertEquals(3, split.size)
        val clock = split.single { it.type == GridItemType.CLOCK }
        val date = split.single { it.type == GridItemType.DATE_TIME }
        // The date keeps the position the user left it at; only the clock moves.
        assertEquals(0, date.col)
        assertEquals(0, date.row)
        // firstFreePosition, so the clock clears everything - including the item that forced the
        // fallback in the first place (the bug class plan 002's rescale pass had to be fixed for).
        assertTrue("clock overlaps the App List", !clock.overlapsItem(appList))
        assertTrue("clock overlaps its own date item", !clock.overlapsItem(date))
        assertTrue(clock.row + clock.spanY <= rowCount)
        assertEquals(appList, split.single { it.type == GridItemType.APP_LIST })
    }

    @Test
    fun `a clock too wide for the original column falls back rather than hanging off the grid`() {
        // Right-hand half of the grid: the default clock width (8) would run to column 12.
        val original = dateTimeItem(col = 4, row = 0, spanX = 4, spanY = 6)

        val clock = listOf(original).split().single { it.type == GridItemType.CLOCK }

        assertTrue(clock.col + clock.spanX <= columnCount)
        assertTrue(clock.row + clock.spanY <= rowCount)
    }

    @Test
    fun `a page with no free space still gets its clock rather than losing it`() {
        val original = dateTimeItem(col = 0, row = 0, spanY = 3)
        val full = GridItem(GridItemType.WIDGET, col = 0, row = 0, spanX = 8, spanY = 17, appWidgetId = 1)

        val split = listOf(original, full).split()

        // Visible and movable in edit mode at (0, 0), which silently dropping it would not be -
        // same last resort synthesizedDateTimeItem takes.
        val clock = split.single { it.type == GridItemType.CLOCK }
        assertEquals(0, clock.col)
        assertEquals(0, clock.row)
    }

    @Test
    fun `stored pages are split once on read and never again`() {
        val prefs = buildPrefs()
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP // isolate this from the cell-size rescale
        prefs.appListMigrationDone = true // and from the App List migration
        prefs.pages = listOf(
            Page("p1", "Home", mutableListOf(dateTimeItem(spanY = 4), appListItem(col = 0, row = 4))),
        )
        assertTrue(prefs.clockDateSplitDone.not())

        val split = prefs.pages.single()

        assertTrue(prefs.clockDateSplitDone)
        assertEquals(1, split.items.count { it.type == GridItemType.CLOCK })
        assertEquals(1, split.items.count { it.type == GridItemType.DATE_TIME })
        assertEquals(1, split.items.count { it.type == GridItemType.APP_LIST })
        // Re-running would add a second CLOCK item on every load.
        assertEquals(split, prefs.pages.single())
        assertEquals(split, prefs.pages.single())
    }

    @Test
    fun `the pre-split pages are backed up verbatim before the split writes over them`() {
        val prefs = buildPrefs()
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
        prefs.appListMigrationDone = true
        val before = listOf(
            Page("p1", "Home", mutableListOf(dateTimeItem(spanY = 4), appListItem(col = 0, row = 4))),
        )
        prefs.pages = before
        assertEquals("", prefs.pagesPreClockSplitBackup)

        prefs.pages.single() // triggers the split

        // The escape hatch for a one-way migration: whatever went in is recoverable verbatim.
        assertEquals(before, prefs.pagesPreClockSplitBackup.toPages())
    }

    @Test
    fun `a blank page gets no items synthesized and a new page starts empty`() {
        // The "no defaults anywhere" end of the change: newPageDefaultItems seeds nothing, and the
        // App List migration only synthesizes a combined item for a page that already held items.
        assertTrue(newPageDefaultItems(columnCount).isEmpty())

        val migrated = listOf(Page("p1", "Home", mutableListOf())).migrateAppItemsToAppLists(
            columnCount = columnCount,
            rowCount = rowCount,
            dateTimeAlignment = Gravity.START,
            dateTimeShowScreenTime = false,
            dateTimeVisibility = Constants.DateTime.ON,
        )

        assertTrue(migrated.single().items.isEmpty())
        assertTrue(migrated.single().items.split().isEmpty())
    }
}
