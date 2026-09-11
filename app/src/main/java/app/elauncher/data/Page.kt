package app.elauncher.data

import android.view.Gravity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class GridItemType { APP, WIDGET, APP_LIST, DATE_TIME }

/**
 * One entry of an APP_LIST item's row/column of shortcuts. [appPackage] == null means the slot is
 * unfilled and shows the "App" placeholder. [appName] stays the real app name; [customLabel] is an
 * independently-editable rename target the settings dialog (Step 11) writes to, mirroring how
 * GridItem itself keeps a real appName alongside per-app fields.
 */
data class AppSlot(
    val appName: String? = null,
    val customLabel: String? = null,
    val appPackage: String? = null,
    val appActivityClassName: String? = null,
    val appUser: String? = null,
    val isShortcut: Boolean = false,
    val shortcutId: String? = null,
)

data class GridItem(
    val type: GridItemType,
    var col: Int,
    var row: Int,
    var spanX: Int,
    var spanY: Int,
    // APP fields (null when type == WIDGET):
    val appName: String? = null,
    val appPackage: String? = null,
    val appActivityClassName: String? = null,
    val appUser: String? = null, // matches existing appUser1..8 storage shape in Prefs.kt
    val isShortcut: Boolean = false,
    val shortcutId: String? = null,
    // WIDGET fields (null when type == APP):
    val appWidgetId: Int? = null,
    // APP_LIST fields (empty list when type != APP_LIST):
    var appSlots: MutableList<AppSlot> = mutableListOf(),
    // APP_LIST/DATE_TIME field (unused otherwise); Gravity.START/CENTER_HORIZONTAL/END, same values
    // Prefs.homeAlignment/appLabelAlignment already use:
    var alignment: Int = android.view.Gravity.START,
    // DATE_TIME fields (unused otherwise):
    var showScreenTime: Boolean = false,
    // Constants.DateTime.ON/OFF/DATE_ONLY - per-item counterpart to the old global
    // Prefs.dateTimeVisibility, carried forward so a pre-existing OFF/DATE_ONLY choice survives the
    // migration off the fixed header instead of every item coming back fully visible regardless.
    var dateTimeVisibility: Int = Constants.DateTime.ON,
)

data class Page(
    var id: String, // random UUID string, stable across reorders
    var name: String,
    var items: MutableList<GridItem> = mutableListOf()
)

/**
 * Restates this item's position and span in a grid whose cell size changed from [oldCellSizeDp] to
 * [newCellSizeDp], so it keeps (as closely as whole cells allow) the same *physical* place on
 * screen.
 *
 * col/row/spanX/spanY are cell *counts*, and physical size is count x cell size - so a smaller cell
 * means the counts have to **grow** by `oldCellSizeDp / newCellSizeDp` (80/48 = 1.667) to cover the
 * same pixels. Getting this ratio the wrong way round silently shrinks every item instead of
 * failing, hence GridItemRescaleTest pinning the direction.
 *
 * Best effort: rounding to whole cells can put two previously adjacent items one cell into each
 * other. Nothing here tries to resolve that - HomeGridView.fits() simply refuses further moves of
 * an overlapping item until the user drags it somewhere legal.
 */
fun GridItem.rescaleForCellSize(
    oldCellSizeDp: Int,
    newCellSizeDp: Int,
    columnCount: Int,
    rowCount: Int,
): GridItem {
    if (oldCellSizeDp <= 0 || newCellSizeDp <= 0 || columnCount <= 0 || rowCount <= 0) return this
    val ratio = oldCellSizeDp.toDouble() / newCellSizeDp.toDouble()

    // Spans first: the column/row clamp below depends on them.
    val spanX = (spanX * ratio).roundToInt().coerceIn(1, columnCount)
    val spanY = (spanY * ratio).roundToInt().coerceIn(1, rowCount)
    val col = (col * ratio).roundToInt().coerceIn(0, columnCount - spanX)
    val row = (row * ratio).roundToInt().coerceIn(0, rowCount - spanY)
    return copy(col = col, row = row, spanX = spanX, spanY = spanY)
}

/**
 * [rescaleForCellSize] applied to every item of every page, then resolves any collision the
 * rounding introduced between items that didn't overlap before.
 *
 * Each item is rescaled independently (its own col/row/spanX/spanY each rounded to the nearest
 * cell), so two previously-adjacent items can land on the same cell - e.g. at the 80dp->48dp
 * ratio (1.667), an item at col 1 (spanX 1) becomes col 2 (spanX 2, covering cols 2-3) while its
 * neighbor at col 2 becomes col 3, landing inside that span. Left unresolved,
 * HomeGridView.rebuildChildren()'s occupied-cell skip treats the later item's origin cell as
 * "covered" by the earlier one and stops rendering it entirely, rather than the two visibly
 * overlapping - the same failure mode HomeFragment.clampSpanYToOverlap() guards against for the
 * settings-driven resize path. Resolved the same way a newly-added item is placed
 * (synthesizedDateTimeItem, HomeFragment.addAppList()): first-fit against everything already
 * placed on the page, in original order, so only the later, colliding item moves.
 */
fun List<Page>.rescaleForCellSize(
    oldCellSizeDp: Int,
    newCellSizeDp: Int,
    columnCount: Int,
    rowCount: Int,
): List<Page> = map { page ->
    val placed = mutableListOf<GridItem>()
    page.items.forEach { original ->
        val rescaled = original.rescaleForCellSize(oldCellSizeDp, newCellSizeDp, columnCount, rowCount)
        val resolved = if (placed.any { it.overlaps(rescaled.col, rescaled.row, rescaled.spanX, rescaled.spanY) }) {
            val (col, row) = firstFreePosition(placed, rescaled.spanX, rescaled.spanY, columnCount, rowCount)
                ?: (rescaled.col to rescaled.row)
            rescaled.copy(col = col, row = row)
        } else {
            rescaled
        }
        placed.add(resolved)
    }
    page.copy(items = placed)
}

/** How many (empty) slots a brand-new App List item is created with. */
const val DEFAULT_APP_LIST_SLOT_COUNT = 4

/**
 * Widest a *default-placed* App List / Date & Screen Time item is made, in cells.
 *
 * defaultColumnCount() is 8 on a Pixel-class portrait screen at 48dp cells, so this only bites on
 * unusually wide grids (tablets, landscape), where stretching a clock across the entire width
 * reads as broken rather than generous. The user can still resize past it by hand.
 */
const val MAX_DEFAULT_SPAN_X = 8

fun defaultDateTimeSpanX(columnCount: Int): Int = columnCount.coerceIn(1, MAX_DEFAULT_SPAN_X)

/**
 * Content-driven height for a DATE_TIME item, in cell rows (Constants.Grid.CELL_SIZE_DP == 48dp).
 *
 * Confirmed on-device (Pixel 9 Pro, Step 10): the large clock text style
 * (dimens time_size, 50-66sp depending on density bucket) alone needs close to two full 48dp rows
 * - its rendered line height comfortably exceeds one row at every density bucket, so budgeting
 * only one row for it (as an earlier version of this function did) clips the glyphs. Three rows
 * (clock ~2 + date ~1) is enough headroom without clipping; a fourth is added for the screen-time
 * line, sized like the date line. HomeGridView.createDateTimeView renders each line at
 * WRAP_CONTENT height rather than dividing this evenly, so a slightly generous estimate here just
 * leaves blank space below the block instead of squeezing text - erring high is the safe
 * direction.
 */
fun defaultDateTimeSpanY(showScreenTime: Boolean): Int = if (showScreenTime) 4 else 3

fun defaultAppListSpanX(columnCount: Int): Int = columnCount.coerceIn(1, MAX_DEFAULT_SPAN_X)

/** One slot per grid row, matching the single-vertical-column look the App List keeps. */
fun defaultAppListSpanY(slotCount: Int): Int = slotCount.coerceAtLeast(1)

/**
 * What a brand-new page starts with: a Date & Screen Time item at the top with an empty-slot App
 * List stacked directly underneath, so a new page is immediately usable instead of blank.
 */
fun newPageDefaultItems(columnCount: Int): MutableList<GridItem> {
    val dateTimeSpanY = defaultDateTimeSpanY(showScreenTime = false)
    return mutableListOf(
        GridItem(
            type = GridItemType.DATE_TIME,
            col = 0,
            row = 0,
            spanX = defaultDateTimeSpanX(columnCount),
            spanY = dateTimeSpanY,
            alignment = Gravity.START,
            showScreenTime = false,
            dateTimeVisibility = Constants.DateTime.ON,
        ),
        GridItem(
            type = GridItemType.APP_LIST,
            col = 0,
            row = dateTimeSpanY, // stacked below, so the two can never overlap
            spanX = defaultAppListSpanX(columnCount),
            spanY = defaultAppListSpanY(DEFAULT_APP_LIST_SLOT_COUNT),
            appSlots = MutableList(DEFAULT_APP_LIST_SLOT_COUNT) { AppSlot() },
            alignment = Gravity.START,
        ),
    )
}

/**
 * One-time conversion off the pre-App-List model: every standalone [GridItemType.APP] item becomes
 * its own one-slot [GridItemType.APP_LIST] item in exactly the same place, and any page left
 * without a [GridItemType.DATE_TIME] item gets one synthesized from the global settings passed in
 * ([dateTimeAlignment], [dateTimeShowScreenTime], [dateTimeVisibility]) so the migration doesn't
 * change what the user currently sees - including a pre-existing OFF/DATE_ONLY choice, which would
 * otherwise silently come back as a fully visible clock regardless of what the user had chosen.
 *
 * Deliberately 1:1 - two APP items on a page become two one-slot App Lists, not one two-slot list.
 * Consolidating afterwards is something the user can do by hand; un-consolidating is not.
 *
 * Pure (no Prefs, no Context) so it is directly unit-testable; [Prefs.pages] owns the run-once
 * guard that stops it re-wrapping already-migrated data or appending a second DATE_TIME item.
 */
fun List<Page>.migrateAppItemsToAppLists(
    columnCount: Int,
    rowCount: Int,
    dateTimeAlignment: Int,
    dateTimeShowScreenTime: Boolean,
    dateTimeVisibility: Int,
): List<Page> = map { page ->
    val items = page.items.map { item ->
        if (item.type != GridItemType.APP) {
            item
        } else {
            GridItem(
                type = GridItemType.APP_LIST,
                col = item.col,
                row = item.row,
                spanX = item.spanX,
                spanY = item.spanY,
                appSlots = mutableListOf(
                    AppSlot(
                        appName = item.appName,
                        appPackage = item.appPackage,
                        appActivityClassName = item.appActivityClassName,
                        appUser = item.appUser,
                        isShortcut = item.isShortcut,
                        shortcutId = item.shortcutId,
                    ),
                ),
                alignment = Gravity.START,
            )
        }
    }.toMutableList()

    if (items.none { it.type == GridItemType.DATE_TIME }) {
        items.add(
            synthesizedDateTimeItem(
                existing = items,
                columnCount = columnCount,
                rowCount = rowCount,
                alignment = dateTimeAlignment,
                showScreenTime = dateTimeShowScreenTime,
                dateTimeVisibility = dateTimeVisibility,
            ),
        )
    }
    page.copy(items = items)
}

private fun synthesizedDateTimeItem(
    existing: List<GridItem>,
    columnCount: Int,
    rowCount: Int,
    alignment: Int,
    showScreenTime: Boolean,
    dateTimeVisibility: Int,
): GridItem {
    val spanX = defaultDateTimeSpanX(columnCount)
    val spanY = defaultDateTimeSpanY(showScreenTime)
    // (0, 0) when it is free, otherwise the first row-major spot that clears everything already on
    // the page - the same placement rule plan 001's add-widget flow uses. A page too full for any
    // such spot still gets the item, overlapping at (0, 0): visible and movable in edit mode, which
    // silently dropping it would not be.
    val (col, row) = firstFreePosition(existing, spanX, spanY, columnCount, rowCount) ?: (0 to 0)
    return GridItem(
        type = GridItemType.DATE_TIME,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        alignment = alignment,
        showScreenTime = showScreenTime,
        dateTimeVisibility = dateTimeVisibility,
    )
}

private fun GridItem.overlaps(col: Int, row: Int, spanX: Int, spanY: Int): Boolean =
    col < this.col + this.spanX && this.col < col + spanX &&
        row < this.row + this.spanY && this.row < row + spanY

/** First row-major (col, row) where a [spanX] x [spanY] footprint hits nothing, or null if none. */
private fun firstFreePosition(
    items: List<GridItem>,
    spanX: Int,
    spanY: Int,
    columnCount: Int,
    rowCount: Int,
): Pair<Int, Int>? {
    for (row in 0..(rowCount - spanY)) {
        for (col in 0..(columnCount - spanX)) {
            if (items.none { it.overlaps(col, row, spanX, spanY) }) return col to row
        }
    }
    return null
}

private const val KEY_TYPE = "type"
private const val KEY_COL = "col"
private const val KEY_ROW = "row"
private const val KEY_SPAN_X = "spanX"
private const val KEY_SPAN_Y = "spanY"
private const val KEY_APP_NAME = "appName"
private const val KEY_APP_PACKAGE = "appPackage"
private const val KEY_APP_ACTIVITY_CLASS_NAME = "appActivityClassName"
private const val KEY_APP_USER = "appUser"
private const val KEY_IS_SHORTCUT = "isShortcut"
private const val KEY_SHORTCUT_ID = "shortcutId"
private const val KEY_APP_WIDGET_ID = "appWidgetId"
private const val KEY_APP_SLOTS = "appSlots"
private const val KEY_ALIGNMENT = "alignment"
private const val KEY_SHOW_SCREEN_TIME = "showScreenTime"
private const val KEY_DATE_TIME_VISIBILITY = "dateTimeVisibility"
private const val KEY_CUSTOM_LABEL = "customLabel"
private const val KEY_ID = "id"
private const val KEY_NAME = "name"
private const val KEY_ITEMS = "items"

private fun AppSlot.toJsonObject(): JSONObject = JSONObject().apply {
    put(KEY_APP_NAME, appName ?: JSONObject.NULL)
    put(KEY_CUSTOM_LABEL, customLabel ?: JSONObject.NULL)
    put(KEY_APP_PACKAGE, appPackage ?: JSONObject.NULL)
    put(KEY_APP_ACTIVITY_CLASS_NAME, appActivityClassName ?: JSONObject.NULL)
    put(KEY_APP_USER, appUser ?: JSONObject.NULL)
    put(KEY_IS_SHORTCUT, isShortcut)
    put(KEY_SHORTCUT_ID, shortcutId ?: JSONObject.NULL)
}

private fun JSONObject.toAppSlot(): AppSlot = AppSlot(
    appName = if (isNull(KEY_APP_NAME)) null else optString(KEY_APP_NAME),
    customLabel = if (isNull(KEY_CUSTOM_LABEL)) null else optString(KEY_CUSTOM_LABEL),
    appPackage = if (isNull(KEY_APP_PACKAGE)) null else optString(KEY_APP_PACKAGE),
    appActivityClassName = if (isNull(KEY_APP_ACTIVITY_CLASS_NAME)) null else optString(KEY_APP_ACTIVITY_CLASS_NAME),
    appUser = if (isNull(KEY_APP_USER)) null else optString(KEY_APP_USER),
    isShortcut = optBoolean(KEY_IS_SHORTCUT, false),
    shortcutId = if (isNull(KEY_SHORTCUT_ID)) null else optString(KEY_SHORTCUT_ID),
)

private fun GridItem.toJsonObject(): JSONObject = JSONObject().apply {
    put(KEY_TYPE, type.name)
    put(KEY_COL, col)
    put(KEY_ROW, row)
    put(KEY_SPAN_X, spanX)
    put(KEY_SPAN_Y, spanY)
    put(KEY_APP_NAME, appName ?: JSONObject.NULL)
    put(KEY_APP_PACKAGE, appPackage ?: JSONObject.NULL)
    put(KEY_APP_ACTIVITY_CLASS_NAME, appActivityClassName ?: JSONObject.NULL)
    put(KEY_APP_USER, appUser ?: JSONObject.NULL)
    put(KEY_IS_SHORTCUT, isShortcut)
    put(KEY_SHORTCUT_ID, shortcutId ?: JSONObject.NULL)
    put(KEY_APP_WIDGET_ID, appWidgetId ?: JSONObject.NULL)
    put(KEY_APP_SLOTS, JSONArray().apply { appSlots.forEach { put(it.toJsonObject()) } })
    put(KEY_ALIGNMENT, alignment)
    put(KEY_SHOW_SCREEN_TIME, showScreenTime)
    put(KEY_DATE_TIME_VISIBILITY, dateTimeVisibility)
}

private fun JSONObject.toGridItemOrNull(): GridItem? {
    val type = try {
        GridItemType.valueOf(optString(KEY_TYPE))
    } catch (e: IllegalArgumentException) {
        return null
    }
    val appSlotsArray = optJSONArray(KEY_APP_SLOTS)
    val appSlots = mutableListOf<AppSlot>()
    if (appSlotsArray != null) {
        for (i in 0 until appSlotsArray.length()) {
            val slotObject = appSlotsArray.optJSONObject(i) ?: continue
            appSlots.add(slotObject.toAppSlot())
        }
    }
    return GridItem(
        type = type,
        col = optInt(KEY_COL, 0),
        row = optInt(KEY_ROW, 0),
        spanX = optInt(KEY_SPAN_X, 1),
        spanY = optInt(KEY_SPAN_Y, 1),
        appName = if (isNull(KEY_APP_NAME)) null else optString(KEY_APP_NAME),
        appPackage = if (isNull(KEY_APP_PACKAGE)) null else optString(KEY_APP_PACKAGE),
        appActivityClassName = if (isNull(KEY_APP_ACTIVITY_CLASS_NAME)) null else optString(KEY_APP_ACTIVITY_CLASS_NAME),
        appUser = if (isNull(KEY_APP_USER)) null else optString(KEY_APP_USER),
        isShortcut = optBoolean(KEY_IS_SHORTCUT, false),
        shortcutId = if (isNull(KEY_SHORTCUT_ID)) null else optString(KEY_SHORTCUT_ID),
        appWidgetId = if (isNull(KEY_APP_WIDGET_ID)) null else optInt(KEY_APP_WIDGET_ID),
        appSlots = appSlots,
        alignment = optInt(KEY_ALIGNMENT, android.view.Gravity.START),
        showScreenTime = optBoolean(KEY_SHOW_SCREEN_TIME, false),
        dateTimeVisibility = optInt(KEY_DATE_TIME_VISIBILITY, Constants.DateTime.ON),
    )
}

private fun Page.toJsonObject(): JSONObject = JSONObject().apply {
    put(KEY_ID, id)
    put(KEY_NAME, name)
    put(KEY_ITEMS, JSONArray().apply { items.forEach { put(it.toJsonObject()) } })
}

private fun JSONObject.toPageOrNull(): Page? {
    val id = optString(KEY_ID, "")
    if (id.isBlank()) return null
    val name = optString(KEY_NAME, "")
    val itemsArray = optJSONArray(KEY_ITEMS) ?: JSONArray()
    val items = mutableListOf<GridItem>()
    for (i in 0 until itemsArray.length()) {
        val itemObject = itemsArray.optJSONObject(i) ?: continue
        itemObject.toGridItemOrNull()?.let { items.add(it) }
    }
    return Page(id = id, name = name, items = items)
}

fun List<Page>.toJson(): String {
    val array = JSONArray()
    forEach { array.put(it.toJsonObject()) }
    return array.toString()
}

fun String.toPages(): List<Page> {
    if (isBlank()) return emptyList()
    return try {
        val array = JSONArray(this)
        val pages = mutableListOf<Page>()
        for (i in 0 until array.length()) {
            val pageObject = array.optJSONObject(i) ?: continue
            pageObject.toPageOrNull()?.let { pages.add(it) }
        }
        pages
    } catch (e: Exception) {
        emptyList()
    }
}
