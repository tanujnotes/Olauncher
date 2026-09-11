package app.elauncher.helper

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import app.elauncher.R
import app.elauncher.data.Constants
import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import app.elauncher.data.Prefs
import kotlin.math.ceil

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(R.string.invalid_pin_request)
        }

        finish()
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                handleWidgetRequest(pinItemRequest)

            else -> showToast(R.string.pin_action_not_supported)
        }
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = runCatching { pinItemRequest.accept() }.getOrDefault(false)
            val message = when (success) {
                true -> R.string.shortcut_pinned
                false -> R.string.shortcut_pin_failed
            }
            showToast(message)
        } else {
            showToast(R.string.invalid_shortcut)
        }
    }

    /**
     * Accepts a widget pin request from another app: reserve a widget id, hand it to the requester
     * (accepting the request is what actually binds the widget to that id), then record the widget
     * on the current page so the home grid can host it.
     *
     * The item is appended at 0,0 without collision checking - overlap is acceptable here because
     * the user repositions/resizes widgets from the home grid afterwards.
     */
    private fun handleWidgetRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val appWidgetId = WidgetHostManager.allocateWidgetId(this)
        val accepted = runCatching {
            pinItemRequest.accept(bundleOf(AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId))
        }.getOrDefault(false)

        if (!accepted) {
            // Nothing got bound to the id, so give it straight back rather than leaking it.
            WidgetHostManager.deleteWidgetId(this, appWidgetId)
            showToast(R.string.widget_pin_failed)
            return
        }

        val info = WidgetHostManager.providerInfoFor(this, appWidgetId)
        val prefs = Prefs(this)
        val pages = prefs.pages
        val page = pages.getOrNull(prefs.currentPageIndex)
        if (page == null) {
            WidgetHostManager.deleteWidgetId(this, appWidgetId)
            showToast(R.string.widget_pin_failed)
            return
        }

        page.items.add(
            GridItem(
                type = GridItemType.WIDGET,
                col = 0,
                row = 0,
                spanX = spanFor(info?.minWidth),
                spanY = spanFor(info?.minHeight),
                appWidgetId = appWidgetId,
            )
        )
        prefs.pages = pages
        showToast(R.string.widget_pinned)
    }

    /**
     * Converts one of [AppWidgetProviderInfo]'s minimum dimensions (in px) to a whole number of
     * home-grid cells, rounding up so the widget is never given less room than it asked for.
     *
     * [minDimensionPx] is null when the provider info couldn't be read back (shouldn't happen for a
     * just-accepted pin, since accepting binds the widget) - fall back to the smallest valid span.
     */
    private fun spanFor(minDimensionPx: Int?): Int {
        if (minDimensionPx == null || minDimensionPx <= 0) return 1
        val minDimensionDp = minDimensionPx / resources.displayMetrics.density
        return ceil(minDimensionDp / Constants.Grid.CELL_SIZE_DP).toInt().coerceAtLeast(1)
    }
}
