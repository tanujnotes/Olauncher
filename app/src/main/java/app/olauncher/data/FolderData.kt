package app.olauncher.data

import java.text.Collator
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

fun FolderItem.toAppModel(collator: Collator): AppModel.Folder = AppModel.Folder(
    folderId = id,
    appLabel = name,
    key = collator.getCollationKey(name),
)

fun FolderApp.toAppModel(appList: List<AppModel>): AppModel? = when {
    isShortcut -> appList.firstOrNull {
        it is AppModel.PinnedShortcut &&
            it.appPackage == packageName &&
            it.user.toString() == user &&
            it.shortcutId == shortcutId
    }

    else -> appList.firstOrNull {
        it is AppModel.App &&
            it.appPackage == packageName &&
            it.user.toString() == user &&
            it.activityClassName == activityClassName
    }
}

fun AppModel.toFolderApp(): FolderApp = when (this) {
    is AppModel.PinnedShortcut -> FolderApp(appPackage, user.toString(), null, true, shortcutId)
    is AppModel.App -> FolderApp(appPackage, user.toString(), activityClassName, false, "")
    else -> FolderApp("", user.toString(), null, false, "")
}

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