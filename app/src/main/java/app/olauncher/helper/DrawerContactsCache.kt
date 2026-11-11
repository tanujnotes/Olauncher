package app.olauncher.helper

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local cache. The application resolver owns the observer, never an Activity. */
object DrawerContactsCache {
    private val lock = Any()
    private val reads = Mutex()
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private var observer: ContentObserver? = null
    private var cached: List<DrawerContact>? = null

    fun invalidate() = synchronized(lock) {
        cached = null
        revision.value += 1
    }

    suspend fun get(context: Context): List<DrawerContact> = reads.withLock {
        val app = context.applicationContext
        synchronized(lock) {
            if (observer == null) {
                val listener = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { invalidate() }
                }
                app.contentResolver.registerContentObserver(
                    ContactsContract.AUTHORITY_URI, true, listener,
                )
                observer = listener
            }
        }
        val version = synchronized(lock) {
            cached?.let { return@withLock it }
            revision.value
        }
        val contacts = readDrawerContacts(app)
        synchronized(lock) {
            // A notification during a read must not make an outdated snapshot reusable.
            if (version == revision.value) cached = contacts
        }
        contacts
    }
}
