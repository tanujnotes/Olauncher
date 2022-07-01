package app.olaunchercf.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val APP_LANGUAGE = "app_language"

    private val PREFS_FILENAME = "app.olauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val DRAWER_ALIGNMENT = "DRAWER_ALIGNMENT"
    private val TIME_ALIGNMENT = "TIME_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME = "DATE_TIME"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val SCREEN_TIMEOUT = "SCREEN_TIMEOUT"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"

    private val APP_NAME = "APP_NAME"
    private val APP_PACKAGE = "APP_PACKAGE"
    private val APP_ALIAS = "APP_USER"
    private val APP_ACTIVITY = "APP_ACTIVITY"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_NAME_CLICK_CLOCK = "APP_NAME_CLICK_CLOCK"
    private val APP_NAME_CLICK_DATE = "APP_NAME_CLICK_DATE"

    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_PACKAGE_CLICK_CLOCK = "APP_PACKAGE_CLICK_CLOCK"
    private val APP_PACKAGE_CLICK_DATE = "APP_PACKAGE_CLICK_DATE"

    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val APP_USER_CLICK_CLOCK = "APP_USER_CLICK_CLOCK"
    private val APP_USER_CLICK_DATE = "APP_USER_CLICK_DATE"

    private val APP_ACTIVITY_SWIPE_LEFT = "APP_ACTIVITY_SWIPE_LEFT"
    private val APP_ACTIVITY_SWIPE_RIGHT = "APP_ACTIVITY_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLICK_CLOCK = "APP_ACTIVITY_CLICK_CLOCK"
    private val APP_ACTIVITY_CLICK_DATE = "APP_ACTIVITY_CLICK_DATE"

    private val TEXT_SIZE = "text_size"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit().putBoolean(FIRST_OPEN, value).apply()

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit().putBoolean(FIRST_SETTINGS_OPEN, value).apply()

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit().putBoolean(LOCK_MODE, value).apply()

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit().putBoolean(AUTO_SHOW_KEYBOARD, value).apply()

    var homeAppsNum: Int
        get() {
            return try {
                prefs.getInt(HOME_APPS_NUM, 4)
            } catch (_: Exception) {
                4
            }
        }
        set(value) = prefs.edit().putInt(HOME_APPS_NUM, value).apply()

    var homeAlignment: Constants.Gravity
        get() {
            return try {
                val string = prefs.getString(
                    HOME_ALIGNMENT,
                    Constants.Gravity.Left.name
                ).toString()
                Constants.Gravity.valueOf(string)
            } catch (_: Exception) {
                Constants.Gravity.Left
            }
        }
        set(value) = prefs.edit().putString(HOME_ALIGNMENT, value.toString()).apply()

    var timeAlignment: Constants.Gravity
        get() {
            val string = prefs.getString(
                TIME_ALIGNMENT,
                Constants.Gravity.Left.name
            ).toString()
            return Constants.Gravity.valueOf(string)
        }
        set(value) = prefs.edit().putString(TIME_ALIGNMENT, value.toString()).apply()

    var drawerAlignment: Constants.Gravity
        get() {
            val string = prefs.getString(
                DRAWER_ALIGNMENT,
                Constants.Gravity.Right.name
            ).toString()
            return Constants.Gravity.valueOf(string)
        }
        set(value) = prefs.edit().putString(DRAWER_ALIGNMENT, value.name).apply()

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit().putBoolean(STATUS_BAR, value).apply()

    var showDateTime: Boolean
        get() = prefs.getBoolean(DATE_TIME, true)
        set(value) = prefs.edit().putBoolean(DATE_TIME, value).apply()

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(SWIPE_LEFT_ENABLED, value).apply()

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(SWIPE_RIGHT_ENABLED, value).apply()

    var appTheme: Constants.Theme
        get() {
            return try {
                Constants.Theme.valueOf(prefs.getString(APP_THEME, Constants.Theme.System.name).toString())
            } catch (_: Exception) {
                Constants.Theme.System
            }
        }
        set(value) = prefs.edit().putString(APP_THEME, value.name).apply()

    var language: Constants.Language
        get() {
            return try {
                Constants.Language.valueOf(prefs.getString(APP_LANGUAGE, Constants.Language.English.name).toString())
            } catch (_: Exception) {
                Constants.Language.English
            }
        }
        set(value) = prefs.edit().putString(APP_LANGUAGE, value.name).apply()

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit().putStringSet(HIDDEN_APPS, value).apply()

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit().putBoolean(HIDDEN_APPS_UPDATED, value).apply()

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit().putInt(SHOW_HINT_COUNTER, value).apply()

    fun getHomeAppValues(i: Int): Array<String> {
        val nameId = "${APP_NAME}_$i"
        val propId = "${APP_PACKAGE}_$i"
        val aliasId = "${APP_ALIAS}_$i"
        val activityId = "${APP_ACTIVITY}_$i"

        val name = prefs.getString(nameId, "").toString()
        val prop = prefs.getString(propId, "").toString()
        val alias = prefs.getString(aliasId, "").toString()
        val activity = prefs.getString(activityId, "").toString()

        return arrayOf(name, prop, alias, activity)
    }

    fun setHomeAppValues(i: Int, name: String, prop: String, alias: String, activity: String) {
        val nameId = "${APP_NAME}_$i"
        val propId = "${APP_PACKAGE}_$i"
        val aliasId = "${APP_ALIAS}_$i"

        prefs.edit().putString(nameId, name).apply()
        prefs.edit().putString(propId, prop).apply()
        prefs.edit().putString(aliasId, alias).apply()
        prefs.edit().putString(aliasId, activity).apply()
    }

    fun setHomeAppName(i: Int, name: String) {
        val nameId = "${APP_NAME}_$i"
        prefs.edit().putString(nameId, name).apply()
    }

    // this only resets name and alias because of how this was done before in HomeFragment
    fun resetHomeAppValues(i: Int) {
        val nameId = "${APP_NAME}_$i"
        val aliasId = "${APP_ALIAS}_$i"

        prefs.edit().putString(nameId, "").apply()
        prefs.edit().putString(aliasId, "").apply()
    }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit().putString(APP_NAME_SWIPE_LEFT, value).apply()

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit().putString(APP_NAME_SWIPE_RIGHT, value).apply()

    var appNameClickClock: String
        get() = prefs.getString(APP_NAME_CLICK_CLOCK, "Clock").toString()
        set(value) = prefs.edit().putString(APP_NAME_CLICK_CLOCK, value).apply()

    var appNameClickDate: String
        get() = prefs.getString(APP_NAME_CLICK_DATE, "Calendar").toString()
        set(value) = prefs.edit().putString(APP_NAME_CLICK_DATE, value).apply()

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_SWIPE_LEFT, value).apply()

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_SWIPE_RIGHT, value).apply()

    var appPackageClickClock: String
        get() = prefs.getString(APP_PACKAGE_CLICK_CLOCK, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_CLICK_CLOCK, value).apply()

    var appPackageClickDate: String
        get() = prefs.getString(APP_PACKAGE_CLICK_DATE, "").toString()
        set(value) = prefs.edit().putString(APP_PACKAGE_CLICK_DATE, value).apply()

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit().putString(APP_USER_SWIPE_LEFT, value).apply()

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit().putString(APP_USER_SWIPE_RIGHT, value).apply()

    var appUserClickClock: String
        get() = prefs.getString(APP_USER_CLICK_CLOCK, "").toString()
        set(value) = prefs.edit().putString(APP_USER_CLICK_CLOCK, value).apply()

    var appUserClickDate: String
        get() = prefs.getString(APP_USER_CLICK_DATE, "").toString()
        set(value) = prefs.edit().putString(APP_USER_CLICK_DATE, value).apply()

    var appActivitySwipeLeft: String
        get() = prefs.getString(APP_ACTIVITY_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit().putString(APP_ACTIVITY_SWIPE_LEFT, value).apply()

    var appActivitySwipeRight: String
        get() = prefs.getString(APP_ACTIVITY_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit().putString(APP_ACTIVITY_SWIPE_RIGHT, value).apply()

    var appActivityClickClock: String
        get() = prefs.getString(APP_ACTIVITY_CLICK_CLOCK, "").toString()
        set(value) = prefs.edit().putString(APP_ACTIVITY_CLICK_CLOCK, value).apply()

    var appActivityClickDate: String
        get() = prefs.getString(APP_ACTIVITY_CLICK_DATE, "").toString()
        set(value) = prefs.edit().putString(APP_ACTIVITY_CLICK_DATE, value).apply()

    var textSize: Int
        get() {
            return try {
                prefs.getInt(TEXT_SIZE, 18)
            } catch (_: Exception) {
                18
            }
        }
        set(value) = prefs.edit().putInt(TEXT_SIZE, value).apply()

    fun getAppName(location: Int): String {
        val (name, _, _, _) = this.getHomeAppValues(location)
        return name
    }

    fun getAppAlias(appName: String): String {
        return prefs.getString(appName, "").toString()
    }
    fun setAppAlias(appName: String, appAlias: String) {
        prefs.edit().putString(appName, appAlias).apply()
    }

    fun getAppPackage(location: Int): String {
        val (_, pack, _, _) = this.getHomeAppValues(location)
        return pack
    }

    fun getAppUser(location: Int): String {
        val (_, _, alias, _) = this.getHomeAppValues(location)
        return alias
    }

    fun getAppActivity(location: Int): String {
        val (_, _, _, activity) = this.getHomeAppValues(location)
        return activity
    }
}
