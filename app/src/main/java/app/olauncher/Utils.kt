package app.olauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.*


fun getAppsList(context: Context): MutableList<AppModel> {
    val appList: MutableList<AppModel> = mutableListOf()
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)

    val installedApps = pm.queryIntentActivities(intent, 0)
    for (app in installedApps)
        appList.add(AppModel(app.loadLabel(pm).toString(), app.activityInfo.packageName))
    appList.sortBy { it.appLabel.toLowerCase(Locale.ROOT) }
    return appList
}

fun isOlauncherDefault(context: Context?): Boolean {
    val launcherPackageName = getLauncherPackageName(context!!)
    return "app.olauncher" == launcherPackageName
}

fun getLauncherPackageName(context: Context): String? {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else null
}