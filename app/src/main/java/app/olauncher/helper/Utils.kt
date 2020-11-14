package app.olauncher.helper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.*
import android.os.UserHandle
import android.os.UserManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import app.olauncher.BuildConfig
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


fun showToastLong(context: Context, message: String) {
    val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG)
    toast.setGravity(Gravity.CENTER, 0, 0)
    toast.show()
}

fun showToastShort(context: Context, message: String) {
    val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
    toast.setGravity(Gravity.CENTER, 0, 0)
    toast.show()
}

suspend fun getAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))
            val hiddenApps = Prefs(context).hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            for (profile in userManager.userProfiles) {
                for (app in launcherApps.getActivityList(null, profile)) {
                    if (!hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())
                        and (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID)
                    )
                        appList.add(AppModel(app.label.toString(), app.applicationInfo.packageName, profile))
                }
            }
            appList.sortBy { it.appLabel.toLowerCase(Locale.ROOT) }

        } catch (e: java.lang.Exception) {
        }
        appList
    }
}

suspend fun getHiddenAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val pm = context.packageManager
        if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))

        val hiddenAppsSet = Prefs(context).hiddenApps
        val appList: MutableList<AppModel> = mutableListOf()
        if (hiddenAppsSet.isEmpty()) return@withContext appList

        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        for (hiddenPackage in hiddenAppsSet) {
            val appPackage = hiddenPackage.split("|")[0]
            val userString = hiddenPackage.split("|")[1]
            var userHandle = android.os.Process.myUserHandle()
            for (user in userManager.userProfiles) {
                if (user.toString() == userString) userHandle = user
            }

            val appInfo = pm.getApplicationInfo(appPackage, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            appList.add(AppModel(appName, appPackage, userHandle))
        }
        appList.sortBy { it.appLabel.toLowerCase(Locale.ROOT) }
        appList
    }
}

// This is to ensure backward compatibility with older app versions
// which did not support multiple user profiles
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
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
    val launcherPackageName =
        getDefaultLauncherPackage(context!!)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else "android"
}

// Source: https://stackoverflow.com/a/13239706
fun resetDefaultLauncher(context: Context) {
    try {
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
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

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
    launcher.startAppDetailsActivity(intent?.component, userHandle, null, null)
//    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//    intent.addCategory(Intent.CATEGORY_DEFAULT)
//    intent.data = Uri.parse("package:$packageName")
//    context.startActivity(intent)
}

suspend fun getBitmapFromURL(src: String?): Bitmap? {
    return withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val url = URL(src)
            val connection: HttpURLConnection = url
                .openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            bitmap = BitmapFactory.decodeStream(input)
        } catch (e: java.lang.Exception) {
        }
        bitmap
    }
}

suspend fun getWallpaperBitmap(originalImage: Bitmap, width: Int, height: Int): Bitmap {
    return withContext(Dispatchers.IO) {

        val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val originalWidth: Float = originalImage.width.toFloat()
        val originalHeight: Float = originalImage.height.toFloat()

        val canvas = Canvas(background)
        val scale: Float = height / originalHeight

        val xTranslation: Float = (width - originalWidth * scale) / 2.0f
        val yTranslation = 0.0f

        val transformation = Matrix()
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(scale, scale)

        val paint = Paint()
        paint.isFilterBitmap = true
        canvas.drawBitmap(originalImage, transformation, paint)

        background
    }
}

suspend fun setWallpaper(appContext: Context, url: String): Boolean {
    return withContext(Dispatchers.IO) {
        val originalImageBitmap = getBitmapFromURL(url) ?: return@withContext false
        val wallpaperManager = WallpaperManager.getInstance(appContext)

        val (width, height) = getScreenDimensions(appContext)
        val scaledBitmap = getWallpaperBitmap(originalImageBitmap, width, height)

        try {
            wallpaperManager.setBitmap(scaledBitmap)
        } catch (e: Exception) {
            return@withContext false
        }

        try {
            originalImageBitmap.recycle()
            scaledBitmap.recycle()
        } catch (e: Exception) {
        }
        true
    }
}

fun getScreenDimensions(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val point = Point()
    windowManager.defaultDisplay.getRealSize(point)
    return Pair(point.x, point.y)
}

suspend fun getTodaysWallpaper(): String {
    return withContext(Dispatchers.IO) {
        var wallpaperUrl: String
        val hour = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        try {
            val url = URL(Constants.URL_DARK_WALLPAPERS)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            val inputStream = connection.inputStream
            val scanner = Scanner(inputStream)
            val stringBuffer = StringBuffer()
            while (scanner.hasNext()) {
                stringBuffer.append(scanner.nextLine())
            }

            val json = JSONObject(stringBuffer.toString())
            wallpaperUrl = json.getString(hour.toString())
            wallpaperUrl

        } catch (e: Exception) {
            wallpaperUrl = getBackupWallpaper(hour) ?: Constants.URL_DEFAULT_WALLPAPER
            wallpaperUrl
        }
    }
}

fun getBackupWallpaper(hour: Int): String? {
    val wallpapers = mapOf(
        1 to "https://images.unsplash.com/photo-1560713796-952b738c598b?w=2000&q=100",
        2 to "https://images.unsplash.com/photo-1502899576159-f224dc2349fa?w=2000&q=100",
        3 to "https://images.unsplash.com/photo-1555217851-6141535bd771?w=2000&q=100",
        4 to "https://images.unsplash.com/photo-1506296933720-1a0ce9bed41d?w=2000&q=100",
        5 to "https://images.unsplash.com/photo-1543651425-3260f9c9ecfb?w=2000&q=100",
        6 to "https://images.unsplash.com/photo-1542676032-e505f4fe02a9?w=2000&q=100",
        7 to "https://images.unsplash.com/photo-1578885136359-16c8bd4d3a8e?w=2000&q=100",
        8 to "https://images.unsplash.com/photo-1511362483461-8795ba551506?w=2000&q=100",
        9 to "https://images.unsplash.com/photo-1601969907230-092a6c065025?w=2000&q=100",
        10 to "https://images.unsplash.com/photo-1593714011419-91b10cd8a3a6?w=2000&q=100",
        11 to "https://images.unsplash.com/photo-1569817480240-41de5e7283c9?w=2000&q=100",
        12 to "https://images.unsplash.com/photo-1588363243917-97873eb4a8ce?w=2000&q=100",
        13 to "https://images.unsplash.com/photo-1576405515541-cb47b7da4fa7?w=2000&q=100",
        14 to "https://images.unsplash.com/photo-1597407068889-782ba11fb621?w=2000&q=100",
        15 to "https://images.unsplash.com/photo-1512850183-6d7990f42385?w=2000&q=100",
        16 to "https://images.unsplash.com/photo-1575387779103-48eb0dc78647?w=2000&q=100",
        17 to "https://images.unsplash.com/photo-1549948558-1c6406684186?w=2000&q=100",
        18 to "https://images.unsplash.com/photo-1570420118092-5b96e28ff4cb?w=2000&q=100",
        19 to "https://images.unsplash.com/photo-1550041771-aef92f14a6d5?w=2000&q=100",
        20 to "https://images.unsplash.com/photo-1600648170020-3182aba0a7e4?w=2000&q=100",
        21 to "https://images.unsplash.com/photo-1551382801-104df360840a?w=2000&q=100",
        22 to "https://images.unsplash.com/photo-1546507318-dc206ad061c9?w=2000&q=100",
        23 to "https://images.unsplash.com/photo-1574895862047-5a662529395e?w=2000&q=100",
        24 to "https://images.unsplash.com/photo-1548625149-720134d51a3a?w=2000&q=100",
        25 to "https://images.unsplash.com/photo-1518242340236-fd1dd715ba89?w=2000&q=100",
        26 to "https://images.unsplash.com/photo-1541617219835-3689726fa8e7?w=2000&q=100",
        27 to "https://images.unsplash.com/photo-1597659840241-37e2b9c2f55f?w=2000&q=100",
        28 to "https://images.unsplash.com/photo-1585135497273-1a86b09fe70e?w=2000&q=100",
        29 to "https://images.unsplash.com/photo-1536444640702-7d82b071cab8?w=2000&q=100",
        30 to "https://images.unsplash.com/photo-1544111795-fe8b9def73f6?w=2000&q=100",
        31 to "https://images.unsplash.com/photo-1600648170020-3182aba0a7e4?w=2000&q=100"
    )
    return wallpapers[hour]
}