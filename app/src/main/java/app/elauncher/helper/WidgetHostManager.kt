package app.elauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app's single [AppWidgetHost] - the object that lets this launcher embed other apps' widgets.
 *
 * Hosting a widget needs three things kept in sync for the whole process lifetime: a host with a
 * stable id (widget ids allocated under one host id are only meaningful to that host), a listening
 * window (widget views only receive remote updates between [startListening] and [stopListening]),
 * and the [AppWidgetManager] used to look a bound widget's provider back up. All three live here
 * so no caller has to construct its own.
 *
 * Implemented as a plain object with `context` parameters - mirroring [FontManager] - rather than a
 * property on a custom Application subclass: this codebase has neither an Application subclass nor
 * a DI framework, and an object already provides the same process-lifetime guarantee. No Context is
 * stored; the lazily-built host/manager are created from `applicationContext` so they don't pin an
 * Activity.
 */
object WidgetHostManager {
    private const val TAG = "WidgetHostManager"

    /**
     * Arbitrary but stable: Android scopes host ids per package, so this only has to stay constant
     * across launches of this app. Changing it would orphan every already-allocated widget id.
     */
    private const val HOST_ID = 1024

    @Volatile
    private var host: AppWidgetHost? = null

    @Volatile
    private var manager: AppWidgetManager? = null

    /**
     * Notified whenever the set of installed widget providers changes - see
     * [addProvidersChangedListener]. CopyOnWriteArrayList because listeners are added/removed from
     * view attach/detach while a dispatch may be iterating (a listener's own reaction rebuilds a
     * grid, which can detach views).
     */
    private val providersChangedListeners = CopyOnWriteArrayList<() -> Unit>()

    private fun host(context: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: ProviderAwareHost(context.applicationContext, HOST_ID).also { host = it }
        }

    /**
     * An [AppWidgetHost] that forwards the framework's "providers changed" callback on.
     *
     * This is the only *live* signal a host gets when a widget's provider app is uninstalled or
     * disabled while its widget is on screen: the per-widget [AppWidgetHost.onProviderChanged]
     * callback only fires for providers that still exist. Fired on the main thread (AppWidgetHost
     * dispatches its callbacks through a handler on the context's main looper), and only while the
     * host is listening - which is exactly the foreground window, the only time a widget is visible.
     */
    private class ProviderAwareHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onProvidersChanged() {
            super.onProvidersChanged()
            providersChangedListeners.forEach { listener ->
                runCatching { listener() }.onFailure { Log.e(TAG, "providersChanged listener failed", it) }
            }
        }
    }

    /**
     * Registers [listener] to be told, on the main thread, that the set of installed widget
     * providers changed - i.e. some app that provides widgets was installed, updated, disabled or
     * uninstalled. Callers must pair this with [removeProvidersChangedListener] (this object
     * outlives every view, so a listener that is never removed leaks it).
     */
    fun addProvidersChangedListener(listener: () -> Unit) {
        providersChangedListeners.addIfAbsent(listener)
    }

    fun removeProvidersChangedListener(listener: () -> Unit) {
        providersChangedListeners.remove(listener)
    }

    private fun manager(context: Context): AppWidgetManager =
        manager ?: synchronized(this) {
            manager ?: AppWidgetManager.getInstance(context.applicationContext)
                .also { manager = it }
        }

    /**
     * Starts receiving widget updates. Required by the [AppWidgetHost] contract for hosted views to
     * refresh at all - call from MainActivity.onStart().
     */
    fun startListening(context: Context) {
        runCatching { host(context).startListening() }
            .onFailure { Log.e(TAG, "startListening failed", it) }
    }

    /** Counterpart to [startListening] - call from MainActivity.onStop(). */
    fun stopListening(context: Context) {
        runCatching { host(context).stopListening() }
            .onFailure { Log.e(TAG, "stopListening failed", it) }
    }

    /** Reserves a new widget id. The caller must either bind it or hand it back to [deleteWidgetId]. */
    fun allocateWidgetId(context: Context): Int = host(context).allocateAppWidgetId()

    /** Releases a widget id (unbinding the widget, if bound). */
    fun deleteWidgetId(context: Context, appWidgetId: Int) {
        host(context).deleteAppWidgetId(appWidgetId)
    }

    /**
     * Inflates the hosted view for an already-bound widget.
     *
     * Deliberately builds the view against `context.applicationContext`, not [context] itself:
     * [AppWidgetHostView] resolves its `LayoutInflater` via `LayoutInflater.from(context)`, and for
     * any context descending from this app's (AppCompatActivity-based) MainActivity that inflater
     * already has AppCompat's view factory installed, which swaps plain framework views for their
     * AppCompat counterparts (e.g. ImageView -> AppCompatImageView) on every inflate. Widget
     * providers' RemoteViews layouts expect plain framework views - RemoteViews.apply() calls
     * framework-only methods like ImageView.setImageLevel() by reflection, which AppCompatImageView
     * doesn't support, so inflating through the swapped factory crashes the widget's rendering
     * (confirmed on-device: "AppCompatImageView can't use method with RemoteViews: setImageLevel").
     * The application context's LayoutInflater was never touched by AppCompatDelegate, so it stays
     * plain.
     */
    fun createHostView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
    ): AppWidgetHostView = host(context).createView(context.applicationContext, appWidgetId, info)

    /**
     * Tells a widget's provider how much room it now has, in dp, after the user resized it.
     *
     * Resizing the hosted view alone only stretches whatever the provider last rendered; a provider
     * reflows its own content (more calendar rows, a bigger clock) solely in response to this
     * options update, so a resize that skips it looks like a widget that ignores its new size.
     * Min and max are set to the same value: the grid gives the widget an exact cell-multiple box,
     * not a range.
     */
    fun updateWidgetSize(
        context: Context,
        appWidgetId: Int,
        minWidthDp: Int,
        minHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int,
    ) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeightDp)
        }
        runCatching { manager(context).updateAppWidgetOptions(appWidgetId, options) }
            .onFailure { Log.e(TAG, "updateAppWidgetOptions failed", it) }
    }

    /** The provider behind a bound widget id, or null if the id isn't bound (or no longer valid). */
    fun providerInfoFor(context: Context, appWidgetId: Int): AppWidgetProviderInfo? =
        manager(context).getAppWidgetInfo(appWidgetId)
}
