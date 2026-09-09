package app.elauncher.helper

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.elauncher.data.Prefs
import java.io.File

/**
 * Picking and persisting a single custom font file.
 *
 * Import model: the user re-picks a file each time they want to change the font (no persisted
 * "font library" list/management UI). The chosen font still needs to survive app restarts
 * without re-prompting, so [handlePickedFont] copies the picked file's bytes once into a
 * single fixed slot in app-private internal storage, and [loadCustomTypeface] loads a
 * [Typeface] from that internal copy at startup.
 */
object FontManager {
    private const val TAG = "FontManager"
    private const val CUSTOM_FONT_FILENAME = "custom_font.ttf"

    private val ACCEPTED_MIME_TYPES = arrayOf(
        "font/ttf",
        "font/otf",
        "application/x-font-ttf",
        "application/x-font-opentype",
        "application/octet-stream",
    )

    private fun customFontFile(context: Context) = File(context.filesDir, CUSTOM_FONT_FILENAME)

    /**
     * Builds an ACTION_OPEN_DOCUMENT intent to let the user pick a font file.
     *
     * Font MIME types are inconsistently registered across devices/file managers, so
     * `application/octet-stream` is included as a fallback and the picked file is also
     * validated by extension in [handlePickedFont] rather than relying on MIME filtering alone.
     */
    fun pickFontIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, ACCEPTED_MIME_TYPES)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

    /**
     * Validates the picked file's display name ends in .ttf/.otf, then copies its bytes into
     * the single fixed internal-storage slot (overwriting any previously picked font).
     *
     * Returns true on success, false if the file couldn't be validated/read (caller shows an
     * error toast).
     */
    fun handlePickedFont(context: Context, uri: Uri): Boolean {
        val displayName = queryDisplayName(context, uri)
        if (displayName == null || !hasFontExtension(displayName)) {
            return false
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                customFontFile(context).outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            invalidateTypefaceCache()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy picked font file", e)
            customFontFile(context).delete()
            invalidateTypefaceCache()
            false
        }
    }

    /**
     * Returns a [Typeface] loaded from the internal custom-font copy, or null if no font has
     * been picked yet or the stored file is corrupt/invalid (in which case it's deleted so a
     * future launch doesn't keep retrying it).
     */
    fun loadCustomTypeface(context: Context): Typeface? {
        val file = customFontFile(context)
        if (!file.exists()) return null

        return try {
            Typeface.createFromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom font, deleting invalid file", e)
            file.delete()
            null
        }
    }

    /** Deletes the stored custom font file, if present. */
    fun clearCustomFont(context: Context) {
        customFontFile(context).delete()
        invalidateTypefaceCache()
    }

    // --- Application layer -------------------------------------------------------------------
    //
    // The built-in light/bold font options are a theme attribute (?attr/mainFontFamily, applied
    // via BoldFontOverlay in MainActivity.onCreate), which can only name a *system* font family -
    // it cannot reference a file. So a user-picked font file is instead applied at runtime by
    // walking each inflated view tree and setting the Typeface on every TextView. The two
    // mechanisms coexist: when useCustomFont is off (or the stored file is gone/corrupt) nothing
    // is walked and the theme attribute keeps rendering the light/bold system font.

    @Volatile
    private var cachedTypeface: Typeface? = null

    @Volatile
    private var typefaceCacheLoaded = false

    private fun invalidateTypefaceCache() {
        typefaceCacheLoaded = false
        cachedTypeface = null
    }

    /**
     * The typeface that should currently be applied app-wide, or null when the user hasn't
     * enabled a custom font or the stored file is missing/corrupt (callers then simply leave the
     * theme-attribute font in place - no error is surfaced here; pick-time errors are reported by
     * [handlePickedFont]'s caller).
     *
     * The loaded [Typeface] is cached because this is called per RecyclerView row bind and per
     * home-grid cell; [Typeface.createFromFile] does disk I/O and must not run on every bind.
     */
    fun activeCustomTypeface(context: Context): Typeface? {
        if (!Prefs(context).useCustomFont) return null
        if (!typefaceCacheLoaded) {
            cachedTypeface = loadCustomTypeface(context)
            typefaceCacheLoaded = true
        }
        return cachedTypeface
    }

    /**
     * Recursively applies [typeface] to every [TextView] (and subclass - EditText, Button, ...)
     * in [root]'s view tree, preserving each view's existing bold/italic style.
     */
    fun applyTypeface(root: View, typeface: Typeface) {
        if (root is TextView) {
            root.setTypeface(typeface, root.typeface?.style ?: Typeface.NORMAL)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyTypeface(root.getChildAt(i), typeface)
            }
        }
    }

    /**
     * Convenience wrapper: applies the current custom typeface to [root]'s tree if there is one,
     * otherwise does nothing. Safe to call unconditionally right after a layout is inflated.
     *
     * Note this only reaches views that exist *now* - RecyclerView rows and code-built views
     * (e.g. HomeGridView's cells) are inflated later and apply the typeface themselves.
     */
    fun applyCustomTypeface(root: View) {
        val typeface = activeCustomTypeface(root.context) ?: return
        applyTypeface(root, typeface)
    }

    private fun hasFontExtension(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        // DocumentFile.fromSingleUri(context, uri)?.name would also work here, but it's a thin
        // wrapper around the same ContentResolver query below, and going straight to the
        // resolver avoids adding a dependency on androidx.documentfile for a single lookup.
        // Fall back to the URI's own last path segment (covers file:// URIs, which don't
        // support the DISPLAY_NAME projection).
        val fromResolver = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query display name for $uri", e)
            null
        }
        return fromResolver ?: uri.lastPathSegment
    }
}
