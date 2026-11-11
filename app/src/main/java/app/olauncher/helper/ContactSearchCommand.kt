package app.olauncher.helper

import androidx.annotation.StringRes
import app.olauncher.R

/** Commands activate only after a literal space, never from a name's initial. */
enum class ContactSearchCommand(
    val action: ContactAction,
    @StringRes val hintRes: Int,
) {
    PHONE(ContactAction.CALL, R.string.contact_command_phone_hint),
    MESSAGE(ContactAction.TEXT, R.string.contact_command_message_hint),
    WHATSAPP(ContactAction.WHATSAPP, R.string.contact_command_whatsapp_hint);

    companion object {
        fun parsePrefix(text: String, commandMode: Boolean = false): ContactSearchCommand? {
            if (!commandMode || text.length < 2 || text[1] != ' ') return null
            return when (text[0].lowercaseChar()) {
                'p' -> PHONE
                'm' -> MESSAGE
                'w' -> WHATSAPP
                else -> null
            }
        }
    }
}
