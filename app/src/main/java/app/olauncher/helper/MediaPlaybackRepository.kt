package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
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
        val preferred = controllers.firstOrNull { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()
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
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
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

    companion object {
        fun hasNotificationListenerPermission(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
