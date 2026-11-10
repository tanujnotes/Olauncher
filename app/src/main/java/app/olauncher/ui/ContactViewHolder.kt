package app.olauncher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.databinding.AdapterDrawerContactBinding
import app.olauncher.helper.ContactAction
import app.olauncher.helper.DrawerContact

internal class ContactViewHolder(private val binding: AdapterDrawerContactBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(contact: DrawerContact, onClick: (DrawerContact, ContactAction) -> Unit) = with(binding) {
        contactName.text = contact.name
        val words = contact.name.split(Regex("\\s+"))
        contactInitials.text = buildString {
            append(initialOf(words.first()))
            if (words.size > 1) append(initialOf(words.last()))
        }
        val colorIndex = Math.floorMod(contact.lookupKey.hashCode(), avatarColors.size)
        ViewCompat.setBackgroundTintList(contactInitials, ColorStateList.valueOf(avatarColors[colorIndex]))
        contactDetails.setOnClickListener { onClick(contact, ContactAction.OPEN) }

        bindAction(contactCall, R.string.drawer_contact_call, contact, ContactAction.CALL, onClick)
        bindAction(contactText, R.string.drawer_contact_text, contact, ContactAction.TEXT, onClick)
        bindAction(contactWhatsApp, R.string.drawer_contact_whatsapp, contact, ContactAction.WHATSAPP, onClick)
    }

    private fun bindAction(
        button: View,
        label: Int,
        contact: DrawerContact,
        action: ContactAction,
        onClick: (DrawerContact, ContactAction) -> Unit,
    ) {
        button.contentDescription = button.context.getString(label, contact.name)
        button.setOnClickListener { onClick(contact, action) }
        button.isEnabled = contact.numbers.isNotEmpty()
        button.alpha = if (button.isEnabled) 1f else 0.35f
    }

    private fun initialOf(word: String): String =
        word.substring(0, word.offsetByCodePoints(0, 1)).uppercase()

    companion object {
        private val avatarColors = intArrayOf(
            Color.rgb(215, 169, 244), Color.rgb(164, 190, 121),
            Color.rgb(252, 192, 152), Color.rgb(155, 204, 233),
        )

        fun create(parent: ViewGroup) = ContactViewHolder(
            AdapterDrawerContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
}
