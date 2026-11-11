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
            assertNull(ContactSearchCommand.parsePrefix(letter, true))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("${letter.uppercase()} ", true))
            assertEquals(command, ContactSearchCommand.parsePrefix("$letter Jamie", true))
        }
    }

    @Test fun regularNamesAndOtherWhitespaceDoNotActivateCommands() {
        for (text in listOf("", "Paul", "Mary Smith", "Will", " p ", "p\tJamie", "m\nJamie", "x Jamie")) {
            assertNull(text, ContactSearchCommand.parsePrefix(text, true))
        }
    }

    @Test fun commandsRequireThunderModeAndRejectOldPeriodSyntax() {
        for (text in listOf("p ", "M Jamie", "w ")) {
            assertNull(ContactSearchCommand.parsePrefix(text, false))
        }
        for (text in listOf(".p ", ".m Jamie", ".w ", "p. ")) {
            assertNull(ContactSearchCommand.parsePrefix(text, true))
        }
    }

    @Test fun commandsUseTheRequestedContactActions() {
        assertEquals(ContactAction.CALL, ContactSearchCommand.PHONE.action)
        assertEquals(ContactAction.TEXT, ContactSearchCommand.MESSAGE.action)
        assertEquals(ContactAction.WHATSAPP, ContactSearchCommand.WHATSAPP.action)
    }
}
