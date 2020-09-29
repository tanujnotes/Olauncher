package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity

class Prefs(context: Context) {
    private val PREFS_FILENAME = "app.olauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val SWIPE_LEFT_RIGHT = "SWIPE_LEFT_RIGHT"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0);

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit().putBoolean(FIRST_OPEN, value).apply()

    var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit().putBoolean(DAILY_WALLPAPER, value).apply()

    var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit().putString(DAILY_WALLPAPER_URL, value).apply()

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit().putInt(HOME_APPS_NUM, value).apply()

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit().putInt(HOME_ALIGNMENT, value).apply()

    var swipeLeftRight: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_RIGHT, true)
        set(value) = prefs.edit().putBoolean(SWIPE_LEFT_RIGHT, value).apply()

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_1, value).apply()

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_2, value).apply()

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_3, value).apply()

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_4, value).apply()

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_5, value).apply()

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_6, value).apply()

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_7, value).apply()

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit().putString(APP_NAME_8, value).apply()

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_1, value).apply()

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_2, value).apply()

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_3, value).apply()

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_4, value).apply()

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_5, value).apply()

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_6, value).apply()

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_7, value).apply()

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_8, value).apply()

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "CAMERA").toString()
        set(value) = prefs.edit().putString(APP_NAME_SWIPE_LEFT, value).apply()

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "PHONE").toString()
        set(value) = prefs.edit().putString(APP_NAME_SWIPE_RIGHT, value).apply()

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_SWIPE_LEFT, value).apply()

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_SWIPE_RIGHT, value).apply()
}