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
    appList.remove(AppModel(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID))
    return appList
}

fun isOlauncherDefault(context: Context?): Boolean {
    val launcherPackageName = getLauncherPackageName(context!!)
    return BuildConfig.APPLICATION_ID == launcherPackageName
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

// Source: https://stackoverflow.com/a/13239706
fun resetDefaultLauncher(context: Context) {
    val packageManager = context.packageManager
    val componentName = ComponentName(context, FakeHomeActivity::class.java)
    packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
    val selector = Intent(Intent.ACTION_MAIN)
    selector.addCategory(Intent.CATEGORY_HOME)
    context.startActivity(selector)
    packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
}