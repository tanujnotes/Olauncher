package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactSearchCommandTest {
    @Test fun commandsRequireDoubleSpaceAndIgnoreCase() {
        for ((letter, command) in listOf(
            "p" to ContactSearchCommand.PHONE,
            "m" to ContactSearchCommand.MESSAGE,
            "wa" to ContactSearchCommand.WHATSAPP,
            "w" to ContactSearchCommand.WEB,
            "ai" to ContactSearchCommand.AI,
        )) {
            assertNull(ContactSearchCommand.parsePrefix(letter, true))
            assertNull(ContactSearchCommand.parsePrefix("$letter ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter  ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("${letter.uppercase()}  ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter  Jamie", true))
        }
    }

    @Test fun androidKeyboardAutoPeriodAlsoActivatesCommands() {
        for ((alias, command) in listOf(
            "p" to ContactSearchCommand.PHONE,
            "m" to ContactSearchCommand.MESSAGE,
            "wa" to ContactSearchCommand.WHATSAPP,
            "w" to ContactSearchCommand.WEB,
            "ai" to ContactSearchCommand.AI,
        )) {
            assertEquals(command, ContactSearchCommand.parsePrefix("$alias. ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("${alias.uppercase()}. Jamie", true))
            assertEquals("Jamie", ContactSearchCommand.queryAfterPrefix("$alias. Jamie"))
            assertEquals("", ContactSearchCommand.queryAfterPrefix("$alias. "))
        }
    }

    @Test fun regularNamesAndOtherWhitespaceDoNotActivateCommands() {
        for (text in listOf("", "Paul", "Mary Smith", "Will", " p ", "p Jamie", "p\tJamie", "m\nJamie", "x Jamie")) {
            assertNull(text, ContactSearchCommand.parsePrefix(text, true))
        }
    }

    @Test fun commandsRejectNonCommandModeAndOldPeriodSyntax() {
        for (text in listOf("p  ", "M  Jamie", "w  ")) {
            assertNull(ContactSearchCommand.parsePrefix(text, false))
        }
        for (text in listOf(".p  ", ".m  Jamie", ".w  ", "p.  ")) {
            assertNull(ContactSearchCommand.parsePrefix(text, true))
        }
    }

    @Test fun commandsUseTheRequestedContactActions() {
        assertEquals(ContactAction.CALL, ContactSearchCommand.PHONE.action)
        assertEquals(ContactAction.TEXT, ContactSearchCommand.MESSAGE.action)
        assertEquals(ContactAction.WHATSAPP, ContactSearchCommand.WHATSAPP.action)
    }
}
