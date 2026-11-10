package app.olauncher.helper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.appcompat.app.AlertDialog
import app.olauncher.R
import kotlinx.coroutines.ensureActive
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class DrawerContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val numbers: List<DrawerContactNumber>,
)

data class DrawerContactNumber(val display: String, val normalized: String?)

enum class ContactAction { OPEN, CALL, TEXT, WHATSAPP }

suspend fun readDrawerContacts(context: Context): List<DrawerContact> {
    val resolver = context.contentResolver
    val numbers = mutableMapOf<Long, MutableList<DrawerContactNumber>>()
    try {
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.NORMALIZED_NUMBER),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val number = cursor.getString(1)?.trim().orEmpty()
                if (number.isBlank()) continue
                val entry = DrawerContactNumber(number, cursor.getString(2))
                val existing = numbers.getOrPut(cursor.getLong(0)) { mutableListOf() }
                if (existing.none { phoneKey(it) == phoneKey(entry) }) existing.add(entry)
            }
        }
        return buildList {
            resolver.query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY),
                null, null, Contacts.SORT_KEY_PRIMARY + " ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(0)
                    val key = cursor.getString(1) ?: continue
                    val name = cursor.getString(2)?.trim().orEmpty()
                    val contactNumbers = numbers[id].orEmpty()
                    add(DrawerContact(
                        id, key,
                        name.ifBlank {
                            contactNumbers.firstOrNull()?.display ?: context.getString(R.string.drawer_contact_unnamed)
                        },
                        contactNumbers,
                    ))
                }
            }
        }
    } catch (_: SecurityException) {
        return emptyList()
    }
}

private fun phoneKey(number: DrawerContactNumber): String =
    number.normalized ?: PhoneNumberUtils.normalizeNumber(number.display)

fun openDrawerContact(context: Context, contact: DrawerContact, action: ContactAction) {
    if (action == ContactAction.OPEN) {
        val uri = Contacts.getLookupUri(contact.id, contact.lookupKey)
        launchContactIntent(context, Intent(Intent.ACTION_VIEW, uri))
        return
    }

    val numbers = contact.numbers
    when (numbers.size) {
        0 -> return
        1 -> openNumber(context, numbers.single(), action)
        else -> AlertDialog.Builder(context)
            .setTitle(R.string.drawer_contact_choose_number)
            .setItems(numbers.map { it.display }.toTypedArray()) { _, index ->
                openNumber(context, numbers[index], action)
            }
            .show()
    }
}

private fun openNumber(context: Context, number: DrawerContactNumber, action: ContactAction) {
    when (action) {
        ContactAction.CALL -> launchContactIntent(context, Intent(
            Intent.ACTION_DIAL, Uri.fromParts("tel", number.display, null)
        ))
        ContactAction.TEXT -> launchContactIntent(context, Intent(
            Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.display, null)
        ))
        ContactAction.WHATSAPP -> {
            val normalized = phoneKey(number)
            val telephony = context.getSystemService(TelephonyManager::class.java)
            val country = telephony?.simCountryIso.orEmpty().ifBlank {
                context.resources.configuration.locales[0].country
            }.uppercase(Locale.ROOT)
            val international = if (normalized.matches(Regex("\\+[1-9][0-9]{1,14}"))) {
                normalized
            } else {
                PhoneNumberUtils.formatNumberToE164(number.display, country)
            }
            if (international == null) {
                context.showToast(context.getString(R.string.drawer_contact_international_number))
                return
            }
            val uri = Uri.parse("https://wa.me/${international.removePrefix("+")}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (!tryContactIntent(context, Intent(intent).setPackage("com.whatsapp")) &&
                !tryContactIntent(context, Intent(intent).setPackage("com.whatsapp.w4b"))
            ) launchContactIntent(context, intent)
        }
        ContactAction.OPEN -> Unit
    }
}

private fun launchContactIntent(context: Context, intent: Intent) {
    if (!tryContactIntent(context, intent)) {
        context.showToast(context.getString(R.string.drawer_contact_action_unavailable))
    }
}

private fun tryContactIntent(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
