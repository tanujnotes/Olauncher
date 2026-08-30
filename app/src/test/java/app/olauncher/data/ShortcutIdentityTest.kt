package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShortcutIdentityTest {
    @Test
    fun `identity is stable`() {
        assertEquals(
            shortcutIdentity("org.browser", "https://example.com", "UserHandle{0}"),
            shortcutIdentity("org.browser", "https://example.com", "UserHandle{0}"),
        )
    }

    @Test
    fun `same shortcut id from different browsers has different identity`() {
        assertNotEquals(
            shortcutIdentity("org.browser.one", "https://example.com", "UserHandle{0}"),
            shortcutIdentity("org.browser.two", "https://example.com", "UserHandle{0}"),
        )
    }

    @Test
    fun `field boundaries cannot collide`() {
        assertNotEquals(
            shortcutIdentity("ab", "c", "d"),
            shortcutIdentity("a", "bc", "d"),
        )
    }
}
