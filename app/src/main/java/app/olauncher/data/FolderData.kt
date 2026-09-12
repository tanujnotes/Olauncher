package app.olauncher.data

import org.json.JSONArray
import org.json.JSONObject

data class FolderItem(
    val id: String,
    val name: String,
    val apps: List<FolderApp> = emptyList(),
)

data class FolderApp(
    val packageName: String,
    val user: String,
    val activityClassName: String?,
    val isShortcut: Boolean,
    val shortcutId: String,
)

fun FolderApp.memberEquals(other: FolderApp): Boolean = when {
    isShortcut || other.isShortcut ->
        isShortcut == other.isShortcut && shortcutId == other.shortcutId &&
            packageName == other.packageName && user == other.user

    else -> packageName == other.packageName && user == other.user &&
        activityClassName == other.activityClassName
}

internal fun FolderApp.toJson(): JSONObject = JSONObject().apply {
    put("package", packageName)
    put("user", user)
    put("activity", activityClassName ?: "")
    put("isShortcut", isShortcut)
    put("shortcutId", shortcutId)
}

internal fun FolderItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("apps", JSONArray(apps.map { it.toJson() }))
}

internal fun JSONObject.toFolderApp(): FolderApp = FolderApp(
    packageName = getString("package"),
    user = getString("user"),
    activityClassName = optString("activity").ifBlank { null },
    isShortcut = optBoolean("isShortcut"),
    shortcutId = optString("shortcutId"),
)

internal fun JSONObject.toFolderItem(): FolderItem {
    val appsArray = optJSONArray("apps") ?: JSONArray()
    return FolderItem(
        id = getString("id"),
        name = getString("name"),
        apps = (0 until appsArray.length()).map { appsArray.getJSONObject(it).toFolderApp() },
    )
}