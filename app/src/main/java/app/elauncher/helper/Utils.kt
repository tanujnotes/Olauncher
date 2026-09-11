package app.elauncher.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.os.Build
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
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import app.elauncher.BuildConfig
import app.elauncher.R
import app.elauncher.data.AppModel
import app.elauncher.data.Constants
import app.elauncher.data.Prefs
import app.elauncher.data.shortcutIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.pow
import kotlin.math.sqrt

fun Context.showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(stringResource: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(stringResource), duration).show()
}

suspend fun getAppsList(
    context: Context,
    prefs: Prefs,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))
            val hiddenApps = Prefs(context).hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                if (isPrivateSpaceProfile(context, profile)) continue
                for (app in launcherApps.getActivityList(null, profile)) {
                    val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                        .ifBlank { app.label.toString() }
                    val appModel = AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = (System.currentTimeMillis() - app.firstInstallTime) < Constants.ONE_HOUR_IN_MILLIS,
                        user = profile
                    )

                    // if the current app is not Elauncher
                    if (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID) {
                        // is this a hidden app?
                        if (hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())) {
                            if (includeHiddenApps) {
                                appList.add(appModel)
                            }
                        } else {
                            // this is a regular app
                            if (includeRegularApps) {
                                appList.add(appModel)
                            }
                        }
                    }
                }
            }

            // Add shortcuts if we're getting regular apps
            if (includeRegularApps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pinned = try {
                    getPinnedShortcuts(context, prefs, collator)
                } catch (e: Exception) {
                    emptyList()
                }
                appList.addAll(pinned)
            }

            appList.sortWith(compareBy(collator) { it.appLabel })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private suspend fun getPinnedShortcuts(
    context: Context,
    prefs: Prefs,
    collator: Collator,
): List<AppModel.PinnedShortcut> =
    withContext(Dispatchers.IO) {
        val pinnedShortcuts = mutableListOf<AppModel.PinnedShortcut>()
        val shortcuts = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (shortcuts?.hasShortcutHostPermission() == true) {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            shortcuts.profiles.forEach { profile ->
                if (isPrivateSpaceProfile(context, profile)) return@forEach
                try {
                    shortcuts.getShortcuts(query, profile)?.forEach { shortcut ->
                        val identity = shortcutIdentity(
                            shortcut.`package`,
                            shortcut.id,
                            profile.toString()
                        )
                        if (shortcut.isPinned && pinnedShortcuts.none { it.identity == identity }) {
                            val label = prefs.getAppRenameLabel(identity)
                                .ifBlank { prefs.getAppRenameLabel(shortcut.id) }
                                .takeIf { it.isNotBlank() }
                                ?: shortcut.shortLabel?.toString()
                                ?: shortcut.longLabel?.toString().orEmpty()
                            pinnedShortcuts.add(
                                AppModel.PinnedShortcut(
                                    appLabel = label,
                                    key = collator.getCollationKey(label),
                                    appPackage = shortcut.`package`,
                                    shortcutId = shortcut.id,
                                    isNew = false,
                                    user = profile
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        pinnedShortcuts
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
    if (activityInfo.isNotEmpty()) return true
    return false
}

fun isPrivateSpaceProfile(context: Context, userHandle: UserHandle): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
    return try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getLauncherUserInfo(userHandle)?.userType == "android.os.usertype.profile.PRIVATE"
    } catch (e: Exception) {
        false
    }
}

fun isPrivateSpaceLocked(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.isQuietModeEnabled(userHandle)
    } catch (e: Exception) {
        true
    }
}

fun getPrivateSpaceUserHandle(context: Context): UserHandle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (profile in userManager.userProfiles) {
        if (isPrivateSpaceProfile(context, profile)) return profile
    }
    return null
}

suspend fun getPrivateSpaceApps(
    context: Context,
    prefs: Prefs,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        try {
            val privateSpaceHandle = getPrivateSpaceUserHandle(context) ?: return@withContext appList
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (app in launcherApps.getActivityList(null, privateSpaceHandle)) {
                if (app.applicationInfo.packageName == BuildConfig.APPLICATION_ID) continue
                val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                    .ifBlank { app.label.toString() }
                appList.add(
                    AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = false,
                        user = privateSpaceHandle
                    )
                )
            }
            appList.sortWith(compareBy(collator) { it.appLabel })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
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

fun isElauncherDefault(context: Context): Boolean {
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

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null)
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    else
        context.showToast(context.getString(R.string.unable_to_open_app_info))
}

fun openSearch(context: Context) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, "")
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
    showToast("")
}

fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    startActivity(intent)
}

fun Context.isSystemApp(packageName: String, user: UserHandle? = null): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val targetUser = user ?: android.os.Process.myUserHandle()
        val activityList = launcherApps.getActivityList(packageName, targetUser)
        if (activityList.isNotEmpty()) {
            val applicationInfo = activityList.first().applicationInfo
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        } else {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
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

@ColorInt
fun Context.themedBackgroundColor(opacity: Int): Int {
    val baseColor = if (isDarkThemeOn()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val alpha = opacity * 255 / 100
    return ColorUtils.setAlphaComponent(baseColor, alpha)
}

fun View.animateAlpha(alpha: Float = 1.0f, reduceAnimations: Boolean = false) {
    if (context.isEinkDisplay() || reduceAnimations) {
        this.alpha = alpha
        return
    }
    this.animate().apply {
        interpolator = LinearInterpolator()
        duration = 200
        alpha(alpha)
        start()
    }
}

fun Context.shareApp() {
    val message = getString(R.string.are_you_using_your_phone_or_is_your_phone_using_you) +
            "\n" + "https://github.com/vinceumo/Elauncher/releases"
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null)
    startActivity(shareIntent)
}

fun Context.rateApp() {
    val intent = Intent(
        Intent.ACTION_VIEW,
        "https://github.com/vinceumo/Elauncher/releases".toUri()
    )
    var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    flags = flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
    intent.addFlags(flags)
    startActivity(intent)
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
fun Context.deletePinnedShortcut(packageName: String, shortcutIdToDelete: String, user: UserHandle) {
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // 1. Query for existing pinned shortcuts for the package
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(packageName)
        // Query only for pinned shortcuts
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }

    try {
        val pinnedShortcuts = launcherApps.getShortcuts(query, user)

        if (pinnedShortcuts != null) {
            // 2. Filter out the shortcut to be deleted
            val updatedPinnedIds = pinnedShortcuts
                .filter { it.id != shortcutIdToDelete }
                .map { it.id }

            // 3. Re-pin the remaining shortcuts
            // This replaces the existing set of pinned shortcuts for this package
            launcherApps.pinShortcuts(packageName, updatedPinnedIds, user)
        }
    } catch (e: SecurityException) {
        // Handle cases where the app doesn't have permission
        // (e.g., not the default launcher or active voice interaction service)
        Log.e("ShortcutHelper", "Permission denied to modify pinned shortcuts for $packageName", e)
    } catch (e: IllegalStateException) {
        // Handle cases where the user profile is locked or not running
        Log.e("ShortcutHelper", "User profile unavailable for modifying pinned shortcuts for $packageName", e)
    } catch (e: Exception) {
        // Handle other potential exceptions (like RemoteException wrapped)
        Log.e("ShortcutHelper", "Failed to modify pinned shortcuts for $packageName", e)
    }
}
