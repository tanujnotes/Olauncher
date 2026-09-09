package app.elauncher.data

import org.json.JSONArray
import org.json.JSONObject

enum class GridItemType { APP, WIDGET }

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
    val appWidgetId: Int? = null
)

data class Page(
    var id: String, // random UUID string, stable across reorders
    var name: String,
    var items: MutableList<GridItem> = mutableListOf()
)

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
private const val KEY_ID = "id"
private const val KEY_NAME = "name"
private const val KEY_ITEMS = "items"

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
}

private fun JSONObject.toGridItemOrNull(): GridItem? {
    val type = try {
        GridItemType.valueOf(optString(KEY_TYPE))
    } catch (e: IllegalArgumentException) {
        return null
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
