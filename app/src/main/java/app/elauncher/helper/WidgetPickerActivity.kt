package app.elauncher.helper

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.elauncher.R
import app.elauncher.data.Prefs

/**
 * Runs the full "add a widget to this cell" round trip - pick a provider, bind it to a freshly
 * allocated widget id, and (when the provider asks for one) run its configuration activity -
 * then hands the resulting widget id plus the target cell back to HomeFragment through Prefs and
 * finishes.
 *
 * Like [FontPickerActivity], this exists solely to work around a real, confirmed-on-device
 * Android platform issue: MainActivity is `android:launchMode="singleTask"` (required for a
 * HOME/launcher app), and registerForActivityResult()'s callback silently never fires when a
 * singleTask Activity is the one waiting on a result from another app's flow - verified on device
 * in Steps 14-15 with the font picker. Every stage of this flow (ACTION_APPWIDGET_PICK,
 * ACTION_APPWIDGET_BIND, ACTION_APPWIDGET_CONFIGURE) is exactly that kind of cross-app round trip,
 * so the whole thing runs here, in a plain (default `standard` launch mode) activity, and
 * HomeFragment never waits on a result at all - it just observes the pendingWidget* Prefs fields
 * when the user naturally returns home (see HomeFragment.consumePendingWidgetPlacement()).
 *
 * Extends plain [ComponentActivity], not AppCompatActivity: this activity stays alive across the
 * picker round trips, so AppCompatActivity's onPostCreate() would reach AppCompatDelegate's
 * subdecor setup and crash against this manifest entry's plain Theme.Translucent.NoTitleBar (same
 * reasoning as FontPickerActivity - see its kdoc). registerForActivityResult() is an
 * androidx.activity API, so it is available here regardless.
 *
 * All three launchers are registered as fields: registerForActivityResult() may only be called
 * before the activity is STARTED, so a multi-stage flow cannot register a launcher mid-flow. The
 * stage-to-stage state (allocated id, target cell, chosen provider) lives in the fields below.
 */
class WidgetPickerActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var targetCol: Int = -1
    private var targetRow: Int = -1

    /** Set once a provider is bound, so the configure-result callback can confirm what it configured. */
    private var pendingProviderInfo: AppWidgetProviderInfo? = null

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                abort(showError = false)
                return@registerForActivityResult
            }
            // The system picker usually binds the widget itself (it holds BIND_APPWIDGET), in which
            // case the provider is already readable back off our id and there is nothing to bind.
            val boundInfo = WidgetHostManager.providerInfoFor(this, appWidgetId)
            if (boundInfo != null) {
                onProviderBound(boundInfo)
                return@registerForActivityResult
            }
            val provider = result.data?.parcelableExtra<ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
            if (provider == null) {
                Log.e(TAG, "Picker returned OK with neither a bound widget nor a provider")
                abort(showError = true)
                return@registerForActivityResult
            }
            bindProvider(provider, result.data?.parcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE))
        }

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                // User declined the "allow this launcher to add widgets" prompt.
                abort(showError = false)
                return@registerForActivityResult
            }
            val info = WidgetHostManager.providerInfoFor(this, appWidgetId)
            if (info == null) {
                Log.e(TAG, "Bind reported OK but widget $appWidgetId is still unbound")
                abort(showError = true)
                return@registerForActivityResult
            }
            onProviderBound(info)
        }

    private val configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) placeWidget()
            else abort(showError = false) // configuration cancelled - nothing to place
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)

        targetCol = intent.getIntExtra(EXTRA_COL, -1)
        targetRow = intent.getIntExtra(EXTRA_ROW, -1)
        appWidgetId = WidgetHostManager.allocateWidgetId(this)

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        if (pickIntent.resolveActivity(packageManager) == null) {
            // No system widget picker on this device. An in-app provider list is the documented
            // fallback, but it is deliberately not built speculatively - if device testing shows
            // this branch is ever reached, that is the point to add it.
            Log.e(TAG, "No activity resolves ACTION_APPWIDGET_PICK")
            abort(showError = true)
            return
        }
        pickLauncher.launch(pickIntent)
    }

    /**
     * Binds [provider] to the allocated id, falling back to the system's ACTION_APPWIDGET_BIND
     * consent screen when this app isn't (yet) allowed to bind widgets on its own.
     */
    private fun bindProvider(provider: ComponentName, profile: UserHandle?) {
        val manager = AppWidgetManager.getInstance(this)
        val bound = runCatching {
            if (profile == null) manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
            else manager.bindAppWidgetIdIfAllowed(appWidgetId, profile, provider, null)
        }.getOrElse {
            Log.e(TAG, "bindAppWidgetIdIfAllowed failed for $provider", it)
            false
        }

        if (bound) {
            val info = WidgetHostManager.providerInfoFor(this, appWidgetId)
            if (info == null) {
                Log.e(TAG, "Bound $provider but its provider info is unreadable")
                abort(showError = true)
            } else {
                onProviderBound(info)
            }
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        if (profile != null) {
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
        }
        runCatching { bindLauncher.launch(bindIntent) }.onFailure {
            Log.e(TAG, "Could not launch ACTION_APPWIDGET_BIND", it)
            abort(showError = true)
        }
    }

    /**
     * The widget is bound. Most non-trivial widgets (calendar, weather, ...) render nothing until
     * their own configuration activity has run, so launch that first when the provider declares
     * one; otherwise the widget is ready to place as-is.
     */
    private fun onProviderBound(info: AppWidgetProviderInfo) {
        pendingProviderInfo = info
        val configure = info.configure
        if (configure == null) {
            placeWidget()
            return
        }
        val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        runCatching { configureLauncher.launch(configureIntent) }.onFailure {
            // Typically a non-exported configuration activity: the widget would stay unconfigured
            // (and so blank), so treat it as a failed add rather than placing a dead widget.
            Log.e(TAG, "Could not launch configuration activity $configure", it)
            abort(showError = true)
        }
    }

    /** Success: hand the bound id and the long-pressed cell to HomeFragment, then get out of the way. */
    private fun placeWidget() {
        val prefs = Prefs(this)
        prefs.pendingWidgetId = appWidgetId
        prefs.pendingWidgetCol = targetCol
        prefs.pendingWidgetRow = targetRow
        finish()
    }

    /**
     * Failure or cancellation at any stage: hand the allocated id straight back so it isn't leaked,
     * leave the pendingWidget* fields untouched (so nothing gets placed) and finish.
     */
    private fun abort(showError: Boolean) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetHostManager.deleteWidgetId(this, appWidgetId)
            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
        if (showError) showToast(R.string.widget_pin_failed)
        finish()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.parcelableExtra(name: String): T? =
        getParcelableExtra(name) as? T

    companion object {
        private const val TAG = "WidgetPickerActivity"

        /** Grid cell the user long-pressed, as put by HomeFragment when starting this activity. */
        const val EXTRA_COL = "col"
        const val EXTRA_ROW = "row"
    }
}
