package app.elauncher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Guards the one-time APP -> APP_LIST migration and its synthesized DATE_TIME item.
 *
 * This runs once, in place, against the only copy of the user's real page layout: getting it wrong
 * loses app placements with nothing left to recover them from, and the run-once guard means it can
 * never correct itself on a later load. Same reasoning as GridItemRescaleTest.
 */
class AppListMigrationTest {

    private val columnCount = 8
    private val rowCount = 15

    private fun appItem(col: Int, row: Int, name: String, pkg: String) = GridItem(
        type = GridItemType.APP,
        col = col,
        row = row,
        spanX = 4,
        spanY = 1,
        appName = name,
        appPackage = pkg,
        appActivityClassName = "$pkg.MainActivity",
        appUser = "UserHandle{0}",
    )

    private fun List<Page>.migrate(
        alignment: Int = Gravity.START,
        showScreenTime: Boolean = false,
        dateTimeVisibility: Int = Constants.DateTime.ON,
    ) = migrateAppItemsToAppLists(
        columnCount = columnCount,
        rowCount = rowCount,
        dateTimeAlignment = alignment,
        dateTimeShowScreenTime = showScreenTime,
        dateTimeVisibility = dateTimeVisibility,
    )

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
    fun `every APP item becomes a one-slot APP_LIST item at the same position`() {
        val phone = appItem(col = 0, row = 3, name = "Phone", pkg = "com.android.dialer")
        val camera = appItem(col = 2, row = 6, name = "Camera", pkg = "com.android.camera")
        val pages = listOf(Page("p1", "Home", mutableListOf(phone, camera)))

        val migrated = pages.migrate().single()

        val appLists = migrated.items.filter { it.type == GridItemType.APP_LIST }
        assertEquals(2, appLists.size)
        // 1:1, never consolidated into one shared list - see migrateAppItemsToAppLists's kdoc.
        assertTrue(appLists.all { it.appSlots.size == 1 })
        listOf(phone, camera).zip(appLists).forEach { (original, converted) ->
            assertEquals(original.col, converted.col)
            assertEquals(original.row, converted.row)
            assertEquals(original.spanX, converted.spanX)
            assertEquals(original.spanY, converted.spanY)
            assertEquals(Gravity.START, converted.alignment)
            val slot = converted.appSlots.single()
            assertEquals(original.appName, slot.appName)
            assertEquals(original.appPackage, slot.appPackage)
            assertEquals(original.appActivityClassName, slot.appActivityClassName)
            assertEquals(original.appUser, slot.appUser)
            assertEquals(original.isShortcut, slot.isShortcut)
            assertEquals(original.shortcutId, slot.shortcutId)
        }
        assertTrue(migrated.items.none { it.type == GridItemType.APP })
    }

    @Test
    fun `a pinned shortcut keeps its shortcut identity through the conversion`() {
        val shortcut = GridItem(
            type = GridItemType.APP,
            col = 1,
            row = 1,
            spanX = 3,
            spanY = 1,
            appName = "Chat with Alice",
            appPackage = "com.example.chat",
            appActivityClassName = null,
            appUser = "UserHandle{0}",
            isShortcut = true,
            shortcutId = "shortcut-7",
        )

        val slot = listOf(Page("p1", "Home", mutableListOf(shortcut)))
            .migrate()
            .single()
            .items
            .single { it.type == GridItemType.APP_LIST }
            .appSlots
            .single()

        assertTrue(slot.isShortcut)
        assertEquals("shortcut-7", slot.shortcutId)
        assertEquals("Chat with Alice", slot.appName)
        assertEquals("com.example.chat", slot.appPackage)
    }

    @Test
    fun `exactly one DATE_TIME item is appended with the synthesized global settings`() {
        val pages = listOf(
            Page("p1", "Home", mutableListOf(appItem(0, 3, "Phone", "com.android.dialer"))),
        )

        val migrated = pages.migrate(alignment = Gravity.END, showScreenTime = true).single()

        val dateTime = migrated.items.single { it.type == GridItemType.DATE_TIME }
        assertEquals(Gravity.END, dateTime.alignment)
        assertTrue(dateTime.showScreenTime)
        assertEquals(Constants.DateTime.ON, dateTime.dateTimeVisibility)
        assertEquals(defaultDateTimeSpanX(columnCount), dateTime.spanX)
        assertEquals(defaultDateTimeSpanY(showScreenTime = true), dateTime.spanY)
        // The DATE_TIME item is full-width (spanX == columnCount) and, with showScreenTime true,
        // defaultDateTimeSpanY(true) rows tall - 3 since the clock moved out into its own CLOCK
        // item (plan 003 Step 7), so it covers rows 0-2 and clears the app item at row 3: (0, 0)
        // is free and the row-major scan stops there. (When it was 4 rows tall this landed at row
        // 4 instead, having overlapped that app item at (0, 0).)
        assertEquals(0, dateTime.col)
        assertEquals(0, dateTime.row)
    }

    @Test
    fun `a pre-existing OFF or DATE_ONLY choice survives the migration instead of coming back fully visible`() {
        // A page that held something: nothing is synthesized for a page that started empty (see
        // `a page that started empty gets nothing synthesized`).
        val pages = listOf(Page("p1", "Home", mutableListOf(appItem(0, 6, "Phone", "com.android.dialer"))))

        val off = pages.migrate(dateTimeVisibility = Constants.DateTime.OFF).single()
        assertEquals(
            Constants.DateTime.OFF,
            off.items.single { it.type == GridItemType.DATE_TIME }.dateTimeVisibility,
        )

        val dateOnly = pages.migrate(dateTimeVisibility = Constants.DateTime.DATE_ONLY).single()
        assertEquals(
            Constants.DateTime.DATE_ONLY,
            dateOnly.items.single { it.type == GridItemType.DATE_TIME }.dateTimeVisibility,
        )
    }

    @Test
    fun `a page that already has a DATE_TIME item does not get a second one`() {
        val existing = GridItem(
            type = GridItemType.DATE_TIME,
            col = 0,
            row = 5,
            spanX = 4,
            spanY = 2,
            alignment = Gravity.CENTER_HORIZONTAL,
            showScreenTime = true,
        )
        val pages = listOf(Page("p1", "Home", mutableListOf(existing)))

        val migrated = pages.migrate().single()

        assertEquals(listOf(existing), migrated.items)
    }

    @Test
    fun `every page that held something gets its own DATE_TIME item`() {
        val pages = listOf(
            Page("p1", "Home", mutableListOf(appItem(0, 0, "Phone", "com.android.dialer"))),
            Page("p2", "Play", mutableListOf(GridItem(GridItemType.WIDGET, 0, 0, 2, 2, appWidgetId = 9))),
        )

        val migrated = pages.migrate()

        migrated.forEach { page ->
            assertEquals(page.name, 1, page.items.count { it.type == GridItemType.DATE_TIME })
        }
    }

    @Test
    fun `a page that started empty gets nothing synthesized`() {
        // The "no defaults anywhere" rule (plan 003): every widget is long-press-added, so a fresh
        // install's blank first page has to come out of the migration chain still blank. The guard
        // reads the page's *original* items, which is the only thing that tells a genuinely blank
        // page apart from a legacy page whose items all converted to App Lists.
        val pages = listOf(
            Page("p1", "Home", mutableListOf()),
            Page("p2", "Work", mutableListOf(appItem(0, 0, "Phone", "com.android.dialer"))),
        )

        val migrated = pages.migrate()

        assertTrue(migrated[0].items.isEmpty())
        assertEquals(1, migrated[1].items.count { it.type == GridItemType.DATE_TIME })
    }

    @Test
    fun `the synthesized item is placed clear of what is already at the top left`() {
        // A full-width block across the top two rows: (0, 0) is taken, so the scan has to move on.
        val blocker = GridItem(GridItemType.WIDGET, col = 0, row = 0, spanX = 8, spanY = 2, appWidgetId = 1)
        val pages = listOf(Page("p1", "Home", mutableListOf(blocker)))

        val dateTime = pages.migrate().single().items.single { it.type == GridItemType.DATE_TIME }

        assertNotEquals(0, dateTime.row)
        val overlapsBlocker = dateTime.col < blocker.col + blocker.spanX &&
            blocker.col < dateTime.col + dateTime.spanX &&
            dateTime.row < blocker.row + blocker.spanY &&
            blocker.row < dateTime.row + dateTime.spanY
        assertTrue("synthesized item overlaps the blocker", !overlapsBlocker)
        assertTrue(dateTime.row + dateTime.spanY <= rowCount)
    }

    @Test
    fun `a page with no free space still gets its DATE_TIME item rather than losing it`() {
        val full = GridItem(GridItemType.WIDGET, col = 0, row = 0, spanX = 8, spanY = 15, appWidgetId = 1)
        val pages = listOf(Page("p1", "Home", mutableListOf(full)))

        val migrated = pages.migrate().single()

        val dateTime = migrated.items.single { it.type == GridItemType.DATE_TIME }
        assertEquals(0, dateTime.col)
        assertEquals(0, dateTime.row)
    }

    @Test
    fun `stored pages are migrated once on read and never again`() {
        val prefs = buildPrefs()
        // Pre-migration data: APP items, no marker set.
        prefs.gridCellSizeDp = Constants.Grid.CELL_SIZE_DP // isolate this from the cell-size rescale
        prefs.pages = listOf(
            Page("p1", "Home", mutableListOf(appItem(0, 4, "Phone", "com.android.dialer"))),
        )
        assertTrue(prefs.appListMigrationDone.not())

        val migrated = prefs.pages.single()

        assertEquals(1, migrated.items.count { it.type == GridItemType.APP_LIST })
        assertEquals(1, migrated.items.count { it.type == GridItemType.DATE_TIME })
        assertTrue(prefs.appListMigrationDone)

        // Re-running would wrap the App List in another App List and append a second clock.
        assertEquals(migrated, prefs.pages.single())
        assertEquals(migrated, prefs.pages.single())
    }

    @Test
    fun `a new page starts empty`() {
        // Every widget (App List, Clock, Date & Screen Time) is long-press-added as of plan 003, so
        // a new page is blank rather than pre-seeded with items the user then has to delete.
        assertTrue(newPageDefaultItems(columnCount).isEmpty())
    }
}
