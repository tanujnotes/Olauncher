package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicState(
    val hasPermission: Boolean = false,
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
)

class MediaPlaybackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponentName = ComponentName(appContext, MusicListenerService::class.java)

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var activeController: MediaController? = null
    private var sessionsListenerRegistered = false

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateFromControllers(controllers.orEmpty())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushStateFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushStateFromController(activeController)
        }

        override fun onSessionDestroyed() {
            setActiveController(null)
            refresh()
        }
    }

    fun start() {
        refresh()
    }

    fun stop() {
        unregisterSessionsListener()
        setActiveController(null)
    }

    fun refresh() {
        val hasPermission = hasNotificationListenerPermission(appContext)
        if (!hasPermission) {
            unregisterSessionsListener()
            setActiveController(null)
            _state.value = MusicState(hasPermission = false)
            return
        }

        registerSessionsListenerIfNeeded()
        val controllers = try {
            mediaSessionManager.getActiveSessions(listenerComponentName).orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        updateFromControllers(controllers)
    }

    /** Hides the currently shown track, mirroring a swipe away in the notification shade. */
    fun dismissCurrent() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        dismissed = DismissedMedia(
            token = controller.sessionToken,
            title = titleOf(metadata),
            artist = artistOf(metadata)
        )
        MusicListenerService.cancelMediaNotification(controller.packageName)
        pushStateFromController(controller)
    }

    fun togglePlayPause() {
        val controller = activeController ?: return
        val playbackState = controller.playbackState?.state
        if (playbackState == PlaybackState.STATE_PLAYING || playbackState == PlaybackState.STATE_BUFFERING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun openActiveMediaApp() {
        val controller = activeController ?: return
        val opened = controller.sessionActivity?.let { pendingIntent ->
            runCatching { pendingIntent.send() }.isSuccess
        } ?: false
        if (opened) return

        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(controller.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        runCatching { appContext.startActivity(launchIntent) }
    }

    private fun registerSessionsListenerIfNeeded() {
        if (sessionsListenerRegistered) return
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                listenerComponentName
            )
            sessionsListenerRegistered = true
        } catch (_: SecurityException) {
            sessionsListenerRegistered = false
        }
    }

    private fun unregisterSessionsListener() {
        if (!sessionsListenerRegistered) return
        runCatching {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        sessionsListenerRegistered = false
    }

    private fun updateFromControllers(controllers: List<MediaController>) {
        val dismissedToken = dismissed?.token
        if (dismissedToken != null && controllers.none { it.sessionToken == dismissedToken }) {
            dismissed = null
        }

        val wanted = controllers.filter { it.sessionToken != dismissedToken }
        val preferred = wanted.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: wanted.firstOrNull()
            ?: controllers.firstOrNull()
        setActiveController(preferred)
        pushStateFromController(preferred)
    }

    private fun setActiveController(controller: MediaController?) {
        if (activeController?.sessionToken == controller?.sessionToken) return
        activeController?.unregisterCallback(controllerCallback)
        activeController = controller
        activeController?.registerCallback(controllerCallback)
    }

    private fun pushStateFromController(controller: MediaController?) {
        if (!_state.value.hasPermission && !hasNotificationListenerPermission(appContext)) {
            _state.value = MusicState(hasPermission = false)
            return
        }

        if (controller == null) {
            _state.value = MusicState(hasPermission = true)
            return
        }

        val metadata = controller.metadata
        val title = titleOf(metadata)
        val artist = artistOf(metadata)

        // A dismissed track stays hidden until it is replaced or its session goes away
        val dismissedMedia = dismissed
        if (dismissedMedia != null) {
            if (dismissedMedia.token == controller.sessionToken &&
                dismissedMedia.title == title &&
                dismissedMedia.artist == artist
            ) {
                _state.value = MusicState(hasPermission = true)
                return
            }
            dismissed = null
        }

        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val playbackState = controller.playbackState?.state
        val isPlaying = playbackState == PlaybackState.STATE_PLAYING ||
            playbackState == PlaybackState.STATE_BUFFERING

        _state.value = MusicState(
            hasPermission = true,
            hasSession = true,
            title = title,
            artist = artist,
            artwork = artwork,
            isPlaying = isPlaying,
        )
    }

    private fun titleOf(metadata: MediaMetadata?): String =
        metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

    private fun artistOf(metadata: MediaMetadata?): String =
        metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

    private data class DismissedMedia(
        val token: MediaSession.Token,
        val title: String,
        val artist: String,
    )

    companion object {
        // Process wide so a dismissal survives the home screen view being recreated
        @Volatile
        private var dismissed: DismissedMedia? = null

        fun hasNotificationListenerPermission(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
