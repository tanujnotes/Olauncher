package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactSearchCommandTest {
    @Test fun commandsRequireSpaceAndIgnoreCase() {
        for ((letter, command) in listOf(
            "p" to ContactSearchCommand.PHONE,
            "m" to ContactSearchCommand.MESSAGE,
            "w" to ContactSearchCommand.WHATSAPP,
        )) {
            assertNull(ContactSearchCommand.parsePrefix(letter))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter "))
            assertEquals(command, ContactSearchCommand.parsePrefix("${letter.uppercase()} "))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter Jamie"))
        }
    }

    @Test fun regularNamesAndOtherWhitespaceDoNotActivateCommands() {
        for (text in listOf("", "Paul", "Mary Smith", "Will", " p ", "p\tJamie", "m\nJamie", "x Jamie")) {
            assertNull(text, ContactSearchCommand.parsePrefix(text))
        }
    }

    @Test fun commandsOutsideContactsRequirePeriodAndSpace() {
        for ((letter, command) in listOf(
            "p" to ContactSearchCommand.PHONE,
            "m" to ContactSearchCommand.MESSAGE,
            "w" to ContactSearchCommand.WHATSAPP,
        )) {
            for (prefix in listOf(letter, letter.uppercase())) {
                assertEquals(command, ContactSearchCommand.parsePrefix("$prefix. ", false))
                assertEquals(command, ContactSearchCommand.parsePrefix("$prefix. Jamie", false))
                assertEquals(3, ContactSearchCommand.prefixLength("$prefix. Jamie", false))
                assertEquals(command, ContactSearchCommand.parsePrefix("$prefix. ", true))
                for (text in listOf(prefix, "$prefix ", "$prefix.", "$prefix.Jamie", "$prefix.\tJamie")) {
                    assertNull(text, ContactSearchCommand.parsePrefix(text, false))
                }
            }
        }
        assertNull(ContactSearchCommand.parsePrefix("x. Jamie", false))
    }

    @Test fun commandsUseTheRequestedContactActions() {
        assertEquals(ContactAction.CALL, ContactSearchCommand.PHONE.action)
        assertEquals(ContactAction.TEXT, ContactSearchCommand.MESSAGE.action)
        assertEquals(ContactAction.WHATSAPP, ContactSearchCommand.WHATSAPP.action)
    }
}
