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

class PageSerializationTest {

    private fun buildPrefs(screenWidthDp: Int = 400): Prefs {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        // Configuration.screenWidthDp is a plain public field: Mockito only proxies methods, so
        // assigning it directly on the mock instance works and is read back as-is.
        configuration.screenWidthDp = screenWidthDp
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
        assertEquals(1, pages[0].items.size)
        val item = pages[0].items[0]
        assertEquals(GridItemType.APP, item.type)
        assertEquals("Phone", item.appName)
        assertEquals("com.android.dialer", item.appPackage)
        assertEquals("com.android.dialer.DialerActivity", item.appActivityClassName)
        assertEquals("UserHandle{0}", item.appUser)
        assertTrue(item.spanX > 0)
    }

    @Test
    fun `unset PAGES with all flat slots blank defaults to a single blank page`() {
        val prefs = buildPrefs()

        val pages = prefs.pages

        assertEquals(1, pages.size)
        assertTrue(pages[0].items.isEmpty())
    }
}
