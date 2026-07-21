package app.olauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.LayoutInflater
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

class WidgetHostManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(appContext, Constants.WIDGET_HOST_ID)
    private var listening = false

    fun allocateId(): Int = appWidgetHost.allocateAppWidgetId()

    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean =
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)

    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return appWidgetHost.createView(WidgetInflationContext(context), appWidgetId, info).apply {
            setAppWidget(appWidgetId, info)
        }
    }

    /**
     * AppCompatActivity installs a LayoutInflater factory that swaps framework views
     * (ImageView, TextView, ...) for AppCompat variants. RemoteViews rejects methods on
     * those ("can't use method with RemoteViews"), which breaks widget rendering.
     * This wrapper hands out a factory-free inflater so widget layouts inflate
     * plain framework views, while keeping the activity theme and configuration.
     */
    private class WidgetInflationContext(base: Context) : ContextWrapper(base) {
        private val inflater: LayoutInflater by lazy {
            LayoutInflater.from(applicationContext).cloneInContext(this)
        }

        override fun getSystemService(name: String): Any? =
            if (Context.LAYOUT_INFLATER_SERVICE == name) inflater else super.getSystemService(name)
    }

    fun deleteId(appWidgetId: Int) {
        runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
    }

    fun startListening() {
        if (listening) return
        runCatching { appWidgetHost.startListening() }
            .onSuccess { listening = true }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { appWidgetHost.stopListening() }
        listening = false
    }

    fun pruneStaleWidgets(prefs: Prefs): List<Int> {
        val validIds = prefs.widgetIdList.filter { appWidgetManager.getAppWidgetInfo(it) != null }
        prefs.widgetIdList.filterNot(validIds::contains).forEach(::deleteId)
        if (validIds != prefs.widgetIdList) {
            prefs.widgetIdList = validIds
            prefs.widgetCurrentPage = prefs.widgetCurrentPage.coerceAtMost((validIds.size - 1).coerceAtLeast(0))
        }
        return validIds
    }

    fun updateSize(hostView: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        val density = hostView.resources.displayMetrics.density
        val widthDp = (widthPx / density).toInt().coerceAtLeast(1)
        val heightDp = (heightPx / density).toInt().coerceAtLeast(1)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        val existing = appWidgetManager.getAppWidgetOptions(hostView.appWidgetId)
        if (existing.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) == widthDp &&
            existing.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) == widthDp &&
            existing.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) == heightDp &&
            existing.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) == heightDp
        ) {
            return
        }
        appWidgetManager.updateAppWidgetOptions(hostView.appWidgetId, options)
        hostView.updateAppWidgetOptions(options)
    }

    companion object {
        @Volatile
        private var instance: WidgetHostManager? = null

        fun get(context: Context): WidgetHostManager =
            instance ?: synchronized(this) {
                instance ?: WidgetHostManager(context).also { instance = it }
            }
    }
}
