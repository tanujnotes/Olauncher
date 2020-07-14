package app.olauncher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


suspend fun getAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val installedApps = pm.queryIntentActivities(intent, 0)
        for (app in installedApps)
            appList.add(AppModel(app.loadLabel(pm).toString(), app.activityInfo.packageName))
        appList.sortBy { it.appLabel.toLowerCase(Locale.ROOT) }
        appList.remove(AppModel(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID))
        appList
    }
}

fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
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

fun setBlackWallpaper(context: Context) {
    try {
        val bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(context.getColor(android.R.color.black))
        val manager = WallpaperManager.getInstance(context)
        manager.setBitmap(bitmap)
        bitmap.recycle()
    } catch (e: Exception) {
    }
}

fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    intent.data = Uri.parse("package:$packageName")
    context.startActivity(intent)
}