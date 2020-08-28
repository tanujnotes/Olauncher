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
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

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
        1 to "https://images.unsplash.com/photo-1506147854445-5a3f534191f8",
        2 to "https://images.unsplash.com/photo-1502899576159-f224dc2349fa",
        3 to "https://images.unsplash.com/photo-1506475097213-5555f7fc9b66",
        4 to "https://images.unsplash.com/photo-1506296933720-1a0ce9bed41d",
        5 to "https://images.unsplash.com/photo-1505191419261-8ccbb5ac8f93",
        6 to "https://images.unsplash.com/photo-1498081959737-f3ba1af08103",
        7 to "https://images.unsplash.com/photo-1515825838458-f2a94b20105a",
        8 to "https://images.unsplash.com/photo-1504883303951-581cbf120aa4",
        9 to "https://images.unsplash.com/photo-1504972516657-526efa0347c9",
        10 to "https://images.unsplash.com/photo-1504198580308-d186fefc3fbb",
        11 to "https://images.unsplash.com/photo-1504466334719-af4ae9f12ad0",
        12 to "https://images.unsplash.com/photo-1495344517868-8ebaf0a2044a",
        13 to "https://images.unsplash.com/photo-1576405515541-cb47b7da4fa7",
        14 to "https://images.unsplash.com/photo-1565176083871-abc1508bd064",
        15 to "https://images.unsplash.com/photo-1518095695691-352141ca6c0f",
        16 to "https://images.unsplash.com/photo-1575387779103-48eb0dc78647",
        17 to "https://images.unsplash.com/photo-1500877015165-e1fb7f2db007",
        18 to "https://images.unsplash.com/photo-1570420118092-5b96e28ff4cb",
        19 to "https://images.unsplash.com/photo-1550041771-aef92f14a6d5",
        20 to "https://images.unsplash.com/photo-1518141532615-4305c9f914c9",
        21 to "https://images.unsplash.com/photo-1530600130-16d76247813a",
        22 to "https://images.unsplash.com/photo-1591333012556-473040f4e105",
        23 to "https://images.unsplash.com/photo-1541185933-c43f4922c6f7",
        24 to "https://images.unsplash.com/photo-1588765529230-0b9518c5af1d",
        25 to "https://images.unsplash.com/photo-1580523494734-852098607611",
        26 to "https://images.unsplash.com/photo-1504893524553-b855bce32c67",
        27 to "https://images.unsplash.com/photo-1536405416754-3bcd4fb38128",
        28 to "https://images.unsplash.com/photo-1577657655074-8bd55df63190",
        29 to "https://images.unsplash.com/photo-1584920718830-8d5d92a21fa8",
        30 to "https://images.unsplash.com/photo-1536444640702-7d82b071cab8",
        31 to "https://images.unsplash.com/photo-1590522665925-dd1abea17a1d"
    )
    return wallpapers[hour]
}