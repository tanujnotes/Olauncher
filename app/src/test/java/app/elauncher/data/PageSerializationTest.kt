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

class PageSerializationTest {

    private fun buildPrefs(screenWidthDp: Int = 400, screenHeightDp: Int = 800): Prefs {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        // Configuration.screenWidthDp is a plain public field: Mockito only proxies methods, so
        // assigning it directly on the mock instance works and is read back as-is.
        configuration.screenWidthDp = screenWidthDp
        configuration.screenHeightDp = screenHeightDp
        `when`(resources.configuration).thenReturn(configuration)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(FakeSharedPreferences())
        return Prefs(context)
    }

    @Test
    fun `list of pages survives toJson then toPages round trip`() {
        val appItem = GridItem(
            type = GridItemType.APP,
            col = 1,
            row = 2,
            spanX = 3,
            spanY = 1,
            appName = "Camera",
            appPackage = "com.android.camera",
            appActivityClassName = "com.android.camera.CameraActivity",
            appUser = "UserHandle{0}",
            isShortcut = true,
            shortcutId = "shortcut-1",
        )
        val widgetItem = GridItem(
            type = GridItemType.WIDGET,
            col = 0,
            row = 0,
            spanX = 2,
            spanY = 2,
            appWidgetId = 42,
        )
        val pages = listOf(
            Page(id = "page-1", name = "Home", items = mutableListOf(appItem, widgetItem)),
        )

        val roundTripped = pages.toJson().toPages()

        assertEquals(pages, roundTripped)
    }

    @Test
    fun `APP_LIST item with populated app slots survives toJson then toPages round trip`() {
        val appListItem = GridItem(
            type = GridItemType.APP_LIST,
            col = 2,
            row = 0,
            spanX = 2,
            spanY = 4,
            appSlots = mutableListOf(
                AppSlot(
                    appName = "Camera",
                    customLabel = "Cam",
                    appPackage = "com.android.camera",
                    appActivityClassName = "com.android.camera.CameraActivity",
                    appUser = "UserHandle{0}",
                    isShortcut = false,
                    shortcutId = null,
                ),
                AppSlot(
                    appName = "Phone",
                    customLabel = null,
                    appPackage = "com.android.dialer",
                    appActivityClassName = "com.android.dialer.DialerActivity",
                    appUser = "UserHandle{0}",
                    isShortcut = true,
                    shortcutId = "shortcut-9",
                ),
                AppSlot(), // unfilled slot - appPackage == null
            ),
            alignment = Gravity.CENTER_HORIZONTAL,
            showScreenTime = true,
        )
        val pages = listOf(
            Page(id = "page-1", name = "Home", items = mutableListOf(appListItem)),
        )

        val roundTripped = pages.toJson().toPages()

        assertEquals(pages, roundTripped)
    }

    @Test
    fun `blank or malformed json returns empty list instead of throwing`() {
        assertEquals(emptyList<Page>(), "".toPages())
        assertEquals(emptyList<Page>(), "   ".toPages())
        assertEquals(emptyList<Page>(), "not json".toPages())
    }

    @Test
    fun `unset PAGES with populated flat slot 1 migrates into a single page`() {
        val prefs = buildPrefs()
        prefs.appName1 = "Phone"
        prefs.appPackage1 = "com.android.dialer"
        prefs.appActivityClassName1 = "com.android.dialer.DialerActivity"
        prefs.appUser1 = "UserHandle{0}"

        val pages = prefs.pages

        assertEquals(1, pages.size)
        // The flat-slot page is built as APP items and then goes straight through the App List
        // migration (see Prefs.migrateToAppLists) and the clock/date split (Prefs.splitDateTimeItems),
        // so what comes back out is an App List plus the synthesized Date & Screen Time item already
        // split into a Clock and a Date item - three items, not the raw APP one.
        assertEquals(3, pages[0].items.size)
        assertEquals(1, pages[0].items.count { it.type == GridItemType.CLOCK })
        val item = pages[0].items.single { it.type == GridItemType.APP_LIST }
        val slot = item.appSlots.single()
        assertEquals("Phone", slot.appName)
        assertEquals("com.android.dialer", slot.appPackage)
        assertEquals("com.android.dialer.DialerActivity", slot.appActivityClassName)
        assertEquals("UserHandle{0}", slot.appUser)
        assertTrue(item.spanX > 0)
        assertEquals(1, pages[0].items.count { it.type == GridItemType.DATE_TIME })
    }

    @Test
    fun `unset PAGES with all flat slots blank defaults to a single empty page`() {
        val prefs = buildPrefs()

        val pages = prefs.pages

        // A fresh install: one blank "Home" page and nothing on it. newPageDefaultItems seeds
        // nothing, and neither migration in the chain synthesizes an item for a page that started
        // empty - every widget is long-press-added (plan 003 Step 7).
        assertEquals(1, pages.size)
        assertTrue(pages[0].items.isEmpty())
    }
}
