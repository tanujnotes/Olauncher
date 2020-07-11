package app.olauncher

import android.content.Context
import android.content.Intent
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