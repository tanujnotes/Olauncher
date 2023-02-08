package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.app.WallpaperManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.*
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

fun Context.showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(stringResource: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(stringResource), duration).show()
}

suspend fun getAppsList(context: Context, includeHiddenApps: Boolean = false): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))
            val hiddenApps = Prefs(context).hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                for (app in launcherApps.getActivityList(null, profile)) {
                    if (includeHiddenApps && app.applicationInfo.packageName != BuildConfig.APPLICATION_ID)
                        appList.add(
                            AppModel(
                                app.label.toString(),
                                collator.getCollationKey(app.label.toString()),
                                app.applicationInfo.packageName,
                                app.componentName.className,
                                profile
                            )
                        )
                    else if (!hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())
                        && app.applicationInfo.packageName != BuildConfig.APPLICATION_ID
                    )
                        appList.add(
                            AppModel(
                                app.label.toString(),
                                collator.getCollationKey(app.label.toString()),
                                app.applicationInfo.packageName,
                                app.componentName.className,
                                profile
                            )
                        )
                }
            }
            appList.sort()

        } catch (e: Exception) {
            e.printStackTrace()
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
        val collator = Collator.getInstance()
        for (hiddenPackage in hiddenAppsSet) {
            try {
                val appPackage = hiddenPackage.split("|")[0]
                val userString = hiddenPackage.split("|")[1]
                var userHandle = android.os.Process.myUserHandle()
                for (user in userManager.userProfiles) {
                    if (user.toString() == userString) userHandle = user
                }

                val appInfo = pm.getApplicationInfo(appPackage, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val appKey = collator.getCollationKey(appName)
                appList.add(AppModel(appName, appKey, appPackage, null, userHandle))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        appList.sort()
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

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    if (activityInfo.size > 0) return true
    return false
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun isOlauncherDefault(context: Context): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(context)
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

fun setPlainWallpaperByTheme(context: Context, appTheme: Int) {
    when (appTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(context, android.R.color.black)
        AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(context, android.R.color.white)
        else -> {
            if (context.isDarkThemeOn())
                setPlainWallpaper(context, android.R.color.black)
            else setPlainWallpaper(context, android.R.color.white)
        }
    }
}

fun setPlainWallpaper(context: Context, color: Int) {
    try {
        val bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(context.getColor(color))
        val manager = WallpaperManager.getInstance(context)
        manager.setBitmap(bitmap)
        bitmap.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getChangedAppTheme(context: Context, currentAppTheme: Int): Int {
    return when (currentAppTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            if (context.isDarkThemeOn())
                AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        launcher.startAppDetailsActivity(intent.component, userHandle, null, null)
    } ?: context.showToast("Unable to to open app info")
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
        } catch (e: Exception) {
            e.printStackTrace()
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
            e.printStackTrace()
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

suspend fun getTodaysWallpaper(wallType: String): String {
    return withContext(Dispatchers.IO) {
        var wallpaperUrl: String
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val key = String.format("%d_%d", month + 1, day)

        try {
            val url = URL(Constants.URL_WALLPAPERS)
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
            val wallpapers = json.getString(key)
            val wallpapersJson = JSONObject(wallpapers)
            wallpaperUrl = wallpapersJson.getString(wallType)
            wallpaperUrl

        } catch (e: Exception) {
            wallpaperUrl = getBackupWallpaper(wallType)
            wallpaperUrl
        }
    }
}

fun getBackupWallpaper(wallType: String): String {
    return if (wallType == Constants.WALL_TYPE_LIGHT)
        Constants.URL_DEFAULT_LIGHT_WALLPAPER
    else Constants.URL_DEFAULT_DARK_WALLPAPER
}

fun openSearch(context: Context) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, "")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    context.startActivity(intent)
}

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    // Source: https://stackoverflow.com/a/51132142
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openDialerApp(context: Context) {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openCameraApp(context: Context) {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.d("TAG", e.toString())
    }
}

fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun isAccessServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (e: Exception) {
        0
    }
    if (enabled == 1) {
        val enabledServicesString: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServicesString?.contains(context.packageName + "/" + MyAccessibilityService::class.java.name) ?: false
    }
    return false
}

fun isTablet(context: Context): Boolean {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(metrics)
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    if (diagonalInches >= 7.0) return true
    return false
}

fun Context.isDarkThemeOn(): Boolean {
    return resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

fun Context.copyToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clipData)
    showToast("Copied")
}

fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    startActivity(intent)
}

fun Context.isSystemApp(packageName: String): Boolean {
    if (packageName.isBlank()) return true
    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
    return (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
}

fun Context.uninstall(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun View.animateAlpha(alpha: Float = 1.0f) {
    this.animate().apply {
        interpolator = LinearInterpolator()
        duration = 200
        alpha(alpha)
        start()
    }
}