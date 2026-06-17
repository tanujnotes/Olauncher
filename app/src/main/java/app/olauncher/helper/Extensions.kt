package app.olauncher.helper

import android.app.Activity
import android.app.AppOpsManager
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.navOptions
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.Constants
import java.util.Calendar
import java.util.Locale

fun View.hideKeyboard() {
    this.clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard(show: Boolean = true) {
    if (show.not()) return
    if (this.requestFocus())
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 100)
}


@RequiresApi(Build.VERSION_CODES.Q)
fun Activity.showLauncherSelector(requestCode: Int) {
    val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
    if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        startActivityForResult(intent, requestCode)
    } else
        resetDefaultLauncher()
}

fun Context.resetDefaultLauncher() {
    try {
        val componentName = ComponentName(this, FakeHomeActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        val selector = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        startActivity(selector)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.isDefaultLauncher(): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(this)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun Context.resetLauncherViaFakeActivity() {
    resetDefaultLauncher()
    if (getDefaultLauncherPackage(this).contains("."))
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

fun Context.openSearch(query: String? = null) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, query ?: "")
    startActivity(intent)
}

fun Context.isEinkDisplay(): Boolean {
//    return true
    return try {
        if (isEinkManufacturer()) true
        else currentRefreshRate() <= Constants.MIN_ANIM_REFRESH_RATE
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun Context.currentRefreshRate(): Float {
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
}

private fun isEinkManufacturer(): Boolean {
    val deviceInfo = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}".lowercase(Locale.ROOT)
    return Constants.EINK_DEVICE_KEYWORDS.any { deviceInfo.contains(it) }
}

fun View.applyEinkOptimizations() {
    if (this is TextView) {
        setShadowLayer(0f, 0f, 0f, 0)
        setTypeface(Typeface.create("sans-serif", typeface?.style ?: Typeface.NORMAL))
    }
    if (this is ViewGroup) {
        // FragmentContainerView throws UnsupportedOperationException if its
        // LayoutTransition is touched at all, so leave it alone.
        if (this !is FragmentContainerView) layoutTransition = null
        for (i in 0 until childCount) getChildAt(i).applyEinkOptimizations()
    }
}

fun NavController.navigateEink(
    context: Context,
    resId: Int,
    args: Bundle? = null,
    popUpToId: Int = 0,
    popUpToInclusive: Boolean = false,
) {
    if (context.isEinkDisplay()) {
        val options = navOptions {
            anim { } // leave all anims at -1 => no transition animation
            if (popUpToId != 0) popUpTo(popUpToId) { inclusive = popUpToInclusive }
        }
        navigate(resId, args, options)
    } else {
        navigate(resId, args)
    }
}

fun Context.searchOnPlayStore(query: String? = null): Boolean {
    return try {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$query&c=apps")
            ).addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        )
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.isPackageInstalled(packageName: String, userHandle: UserHandle = android.os.Process.myUserHandle()): Boolean {
    val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, userHandle)
    return activityInfo.isNotEmpty()
}

fun Context.isCountryIn(): Boolean {
    val country = runCatching {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephonyManager?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: telephonyManager?.networkCountryIso?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: Locale.getDefault().country
    return country.equals("IN", ignoreCase = true)
}

@RequiresApi(Build.VERSION_CODES.Q)
fun Context.appUsagePermissionGranted(): Boolean {
    val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOpsManager.unsafeCheckOpNoThrow(
        "android:get_usage_stats",
        android.os.Process.myUid(),
        packageName
    ) == AppOpsManager.MODE_ALLOWED
}

fun Context.formattedTimeSpent(timeSpent: Long): String {
    val seconds = timeSpent / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        timeSpent == 0L -> "0m"

        hours > 0 -> getString(
            R.string.time_spent_hour,
            hours.toString(),
            remainingMinutes.toString()
        )

        minutes > 0 -> {
            getString(R.string.time_spent_min, minutes.toString())
        }

        else -> "<1m"
    }
}

fun Long.convertEpochToMidnight(): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun Long.isDaySince(): Int = ((System.currentTimeMillis().convertEpochToMidnight() - this.convertEpochToMidnight())
        / Constants.ONE_DAY_IN_MILLIS).toInt()

fun Long.hasBeenDays(days: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_DAY_IN_MILLIS) >= days

fun Long.hasBeenHours(hours: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_HOUR_IN_MILLIS) >= hours

fun Long.hasBeenMinutes(minutes: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_MINUTE_IN_MILLIS) >= minutes

fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}
