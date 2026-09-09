package app.elauncher.helper

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.elauncher.R
import app.elauncher.data.Prefs

/**
 * Launches the system font-file picker and persists the result, then finishes.
 *
 * This exists solely to work around a real, confirmed-on-device Android platform issue:
 * MainActivity is `android:launchMode="singleTask"` (required for a HOME/launcher app), and
 * registerForActivityResult()'s callback silently never fires when a singleTask Activity is the
 * one waiting on the result of an ACTION_OPEN_DOCUMENT pick - verified via direct on-device
 * logging (the launch log fires, the result callback never does, even though DocumentsUI closes
 * and control visibly returns to MainActivity). Routing the whole pick-and-persist round trip
 * through this separate, normal-launch-mode Activity (same pattern as PinItemActivity) avoids
 * MainActivity ever being the Activity waiting on a result, sidestepping the platform issue
 * entirely. MainActivity/SettingsFragment never see an activity result for this flow at all -
 * they just observe the Prefs change (and the PENDING_FONT_CHANGE one-shot flag, consumed by
 * SettingsFragment.onResume()) when the user naturally returns to Settings.
 *
 * Extends plain [ComponentActivity], not AppCompatActivity: unlike PinItemActivity (which
 * finishes synchronously inside onCreate(), before the framework ever reaches onPostCreate()),
 * this activity stays alive across the picker round trip, so AppCompatActivity's onPostCreate()
 * would reach AppCompatDelegate's subdecor setup and crash with "You need to use a
 * Theme.AppCompat theme" against this manifest's plain Theme.Translucent.NoTitleBar - confirmed
 * on-device. ComponentActivity still provides registerForActivityResult() (it's an
 * androidx.activity API, not an AppCompat one) without that requirement, and this activity has
 * no UI needing AppCompat's feature set anyway.
 */
class FontPickerActivity : ComponentActivity() {

    private val pickFontLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) handlePicked(uri) else onFailure()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        pickFontLauncher.launch(FontManager.pickFontIntent())
    }

    private fun handlePicked(uri: Uri) {
        val displayName = queryDisplayName(uri)
        if (displayName == null || !FontManager.handlePickedFont(this, uri)) {
            onFailure()
            return
        }
        val prefs = Prefs(this)
        prefs.useCustomFont = true
        prefs.customFontName = displayName
        prefs.pendingFontChange = true
        finish()
    }

    private fun onFailure() {
        showToast(R.string.invalid_font_file)
        finish()
    }

    // Same lookup FontManager.handlePickedFont() does internally for its own validation - kept
    // duplicated (not exposed from FontManager) rather than adding a public API surface for a
    // single caller; matches the pattern already used before this activity existed.
    private fun queryDisplayName(uri: Uri): String? {
        val fromResolver = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            Log.e("FontPickerActivity", "Failed to query display name for $uri", e)
            null
        }
        return fromResolver ?: uri.lastPathSegment
    }
}
