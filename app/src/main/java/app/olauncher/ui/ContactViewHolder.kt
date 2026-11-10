package app.olauncher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.databinding.AdapterDrawerContactBinding
import app.olauncher.helper.ContactAction
import app.olauncher.helper.DrawerContact
import app.olauncher.helper.DrawerContactPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal class ContactViewHolder(private val binding: AdapterDrawerContactBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private var photoJob: Job? = null

    fun bind(
        contact: DrawerContact,
        scope: CoroutineScope,
        photos: LruCache<DrawerContactPhoto, Bitmap>,
        onClick: (DrawerContact, ContactAction) -> Unit,
    ) = with(binding) {
        contactName.text = contact.name
        bindAvatar(contact, scope, photos)
        contactDetails.setOnClickListener { onClick(contact, ContactAction.OPEN) }

        bindAction(contactCall, R.string.drawer_contact_call, contact, ContactAction.CALL, onClick)
        bindAction(contactText, R.string.drawer_contact_text, contact, ContactAction.TEXT, onClick)
        bindAction(contactWhatsApp, R.string.drawer_contact_whatsapp, contact, ContactAction.WHATSAPP, onClick)
    }

    private fun bindAvatar(
        contact: DrawerContact,
        scope: CoroutineScope,
        photos: LruCache<DrawerContactPhoto, Bitmap>,
    ) = with(binding) {
        clearPhoto()
        val initial = Character.toUpperCase((contact.givenName ?: contact.name).codePointAt(0))
        contactInitial.text = String(Character.toChars(initial))
        val photo = contact.photo ?: return
        photos.get(photo)?.let {
            showPhoto(it)
            return
        }
        val resolver = root.context.contentResolver
        photoJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            } ?: return@launch
            photos.put(photo, bitmap)
            showPhoto(bitmap)
        }
    }

    private fun showPhoto(bitmap: Bitmap) = with(binding) {
        contactPhoto.setImageBitmap(bitmap)
        contactInitial.isInvisible = true
    }

    fun clearPhoto() = with(binding) {
        photoJob?.cancel()
        photoJob = null
        contactPhoto.setImageDrawable(null)
        contactInitial.isInvisible = false
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

    companion object {
        fun create(parent: ViewGroup) = ContactViewHolder(
            AdapterDrawerContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
}
