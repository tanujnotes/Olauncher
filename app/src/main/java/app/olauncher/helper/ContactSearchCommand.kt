package app.olauncher.helper

/** Commands activate only after a literal space, never from a name's initial. */
enum class ContactSearchCommand(val action: ContactAction) {
    PHONE(ContactAction.CALL), MESSAGE(ContactAction.TEXT), WHATSAPP(ContactAction.WHATSAPP);

    companion object {
        fun prefixLength(text: String, contactsOnly: Boolean): Int = when {
            text.length >= 3 && text[0] == '.' && text[2] == ' ' -> 3
            contactsOnly && text.length >= 2 && text[1] == ' ' -> 2
            else -> 0
        }

        fun parsePrefix(text: String, contactsOnly: Boolean = true): ContactSearchCommand? {
            if (prefixLength(text, contactsOnly) == 0) return null
            val letterIndex = if (text[0] == '.') 1 else 0
            return when (text[letterIndex].lowercaseChar()) {
                'p' -> PHONE
                'm' -> MESSAGE
                'w' -> WHATSAPP
                else -> null
            }
        }
    }
}
