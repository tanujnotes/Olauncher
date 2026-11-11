package app.olauncher.helper

/** Commands activate only after a literal space, never from a name's initial. */
enum class ContactSearchCommand(val action: ContactAction) {
    PHONE(ContactAction.CALL), MESSAGE(ContactAction.TEXT), WHATSAPP(ContactAction.WHATSAPP);

    companion object {
        fun parsePrefix(text: String): ContactSearchCommand? {
            if (text.length < 2 || text[1] != ' ') return null
            return when (text[0].lowercaseChar()) {
                'p' -> PHONE
                'm' -> MESSAGE
                'w' -> WHATSAPP
                else -> null
            }
        }
    }
}
