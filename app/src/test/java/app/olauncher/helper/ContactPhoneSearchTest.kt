package app.olauncher.helper

import org.junit.Assert.*
import org.junit.Test

class ContactPhoneSearchTest {
    @Test fun matchesFormattedAndPartialNumbers() {
        assertTrue(contactPhoneMatches(contactPhoneQuery("(555) 123"), "+1 (555) 123-4567", null))
        assertTrue(contactPhoneMatches("4567", "+1 (555) 123-4567", null))
        assertTrue(contactPhoneMatches("1555", "555 123 4567", "+15551234567"))
        assertFalse(contactPhoneMatches("999", "555 123 4567", null))
    }
    @Test fun ignoresFormattingOnlyAndMixedNameQueries() {
        assertEquals("", contactPhoneQuery("+ ()-"))
        assertEquals("", contactPhoneQuery("Alice 123"))
        assertFalse(contactPhoneMatches("", "5551234567", null))
    }
}
