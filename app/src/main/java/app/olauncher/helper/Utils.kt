package app.olauncher.helper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


fun showToastLong(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

fun showToastShort(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

suspend fun getAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)

            val installedApps = pm.queryIntentActivities(intent, 0)
            for (app in installedApps)
                appList.add(
                    AppModel(
                        app.loadLabel(pm).toString(),
                        app.activityInfo.packageName
                    )
                )
            appList.sortBy { it.appLabel.toLowerCase(Locale.ROOT) }
            appList.remove(
                AppModel(
                    context.getString(R.string.app_name),
                    BuildConfig.APPLICATION_ID
                )
            )
        } catch (e: java.lang.Exception) {
        }
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
        1 to "https://images.unsplash.com/photo-1560713796-952b738c598b",
        2 to "https://images.unsplash.com/photo-1502899576159-f224dc2349fa",
        3 to "https://images.unsplash.com/photo-1555217851-6141535bd771",
        4 to "https://images.unsplash.com/photo-1506296933720-1a0ce9bed41d",
        5 to "https://images.unsplash.com/photo-1543651425-3260f9c9ecfb",
        6 to "https://images.unsplash.com/photo-1542676032-e505f4fe02a9",
        7 to "https://images.unsplash.com/photo-1515825838458-f2a94b20105a",
        8 to "https://images.unsplash.com/photo-1504883303951-581cbf120aa4",
        9 to "https://images.unsplash.com/photo-1575632000537-8bf1eb667089",
        10 to "https://images.unsplash.com/photo-1516410529446-2c777cb7366d",
        11 to "https://images.unsplash.com/photo-1578759029433-3fba392034d3",
        12 to "https://images.unsplash.com/photo-1584743241753-a727f5d13ff4",
        13 to "https://images.unsplash.com/photo-1576405515541-cb47b7da4fa7",
        14 to "https://images.unsplash.com/photo-1569817480240-41de5e7283c9",
        15 to "https://images.unsplash.com/photo-1512850183-6d7990f42385",
        16 to "https://images.unsplash.com/photo-1575387779103-48eb0dc78647",
        17 to "https://images.unsplash.com/photo-1549948558-1c6406684186",
        18 to "https://images.unsplash.com/photo-1570420118092-5b96e28ff4cb",
        19 to "https://images.unsplash.com/photo-1550041771-aef92f14a6d5",
        20 to "https://images.unsplash.com/photo-1569817480240-41de5e7283c9",
        21 to "https://images.unsplash.com/photo-1551382801-104df360840a",
        22 to "https://images.unsplash.com/photo-1546507318-dc206ad061c9",
        23 to "https://images.unsplash.com/photo-1574895862047-5a662529395e",
        24 to "https://images.unsplash.com/photo-1548625149-720134d51a3a",
        25 to "https://images.unsplash.com/photo-1580523494734-852098607611",
        26 to "https://images.unsplash.com/photo-1541617219835-3689726fa8e7",
        27 to "https://images.unsplash.com/photo-1536405416754-3bcd4fb38128",
        28 to "https://images.unsplash.com/photo-1505231509341-30534a9372ee",
        29 to "https://images.unsplash.com/photo-1584920718830-8d5d92a21fa8",
        30 to "https://images.unsplash.com/photo-1536444640702-7d82b071cab8",
        31 to "https://images.unsplash.com/photo-1514222134-b57cbb8ce073"
    )
    return wallpapers[hour]
}