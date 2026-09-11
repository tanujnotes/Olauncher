package app.elauncher.data

object Constants {

    object Key {
        const val FLAG = "flag"
        const val COL = "col"
        const val ROW = "row"

        // Which slot of the App List item at (COL, ROW) a picked app should be written into - the
        // App List widget replaced standalone one-app cells, so (col, row) alone no longer
        // identifies a single app.
        const val SLOT_INDEX = "slotIndex"
    }

    object Dialog {
        const val ABOUT = "ABOUT"
        const val REVIEW = "REVIEW"
        const val RATE = "RATE"
        const val SHARE = "SHARE"
        const val HIDDEN = "HIDDEN"
        const val KEYBOARD = "KEYBOARD"
        const val DIGITAL_WELLBEING = "DIGITAL_WELLBEING"
    }

    object UserState {
        const val START = "START"
        const val REVIEW = "REVIEW"
        const val RATE = "RATE"
        const val SHARE = "SHARE"
    }

    object DateTime {
        const val OFF = 0
        const val ON = 1
        const val DATE_ONLY = 2

        fun isTimeVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON
        }

        fun isDateVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON || dateTimeVisibility == DATE_ONLY
        }
    }

    /**
     * The home grid's geometry, in dp.
     *
     * Single source of truth: before this existed the cell size was written out three times
     * (HomeGridView's own constant, HomeFragment's pre-measure fallback, Prefs.defaultColumnCount)
     * and the margin subtraction only in one of them, which is what let app items be created wider
     * than the grid they had to fit into.
     */
    object Grid {
        const val CELL_SIZE_DP = 48

        /**
         * What [CELL_SIZE_DP] was before it shrank to 48dp. Items' col/row/spanX/spanY are stored
         * as cell *counts*, so every pre-existing item has to be rescaled once when the cell size
         * changes - see Prefs.gridCellSizeDp and rescaleForCellSize().
         */
        const val LEGACY_CELL_SIZE_DP = 80

        // item_home_page.xml's margins around HomeGridView. Only used to predict the grid's size
        // before it has been measured (Prefs.defaultColumnCount/defaultRowCount and
        // HomeFragment.gridGeometry's fallback); once measured, HomeGridView's own numbers win.
        const val HORIZONTAL_MARGIN_DP = 16 // 8dp each side
        const val VERTICAL_MARGIN_DP = 56 // 8dp top (breathing room, Step 7) + 48dp bottom (page dots)
    }

    object SwipeDownAction {
        const val SEARCH = 1
        const val NOTIFICATIONS = 2
    }

    object CharacterIndicator {
        const val SHOW = 102
        const val HIDE = 101
    }

    val CLOCK_APP_PACKAGES = arrayOf(
        "com.google.android.deskclock", //Google Clock
        "com.sec.android.app.clockpackage", //Samsung Clock
        "com.oneplus.deskclock", //OnePlus Clock
        "com.miui.clock", //Xiaomi Clock
    )

//    const val THEME_MODE_DARK = 0
//    const val THEME_MODE_LIGHT = 1
//    const val THEME_MODE_SYSTEM = 2

    const val FLAG_LAUNCH_APP = 100
    const val FLAG_HIDDEN_APPS = 101

    // Superseded the old FLAG_SET_HOME_APP_1..8 (one flag per fixed flat Prefs slot). The target is
    // addressed by (col, row) plus a slot index - see Key.COL/Key.ROW/Key.SLOT_INDEX - so a single
    // flag now covers "pick an app for this App List slot" regardless of which slot it targets.
    const val FLAG_SET_HOME_APP_CELL = 1

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12
    const val FLAG_SET_CLOCK_APP = 13
    const val FLAG_SET_CALENDAR_APP = 14
    const val FLAG_SET_SCREEN_TIME_APP = 15

    const val REQUEST_CODE_ENABLE_ADMIN = 666
    const val REQUEST_CODE_LAUNCHER_SELECTOR = 678

    const val HINT_RATE_US = 15

    const val LONG_PRESS_DELAY_MS = 500L
    const val ONE_DAY_IN_MILLIS = 86400000L
    const val ONE_HOUR_IN_MILLIS = 3600000L
    const val ONE_MINUTE_IN_MILLIS = 60000L

    const val MIN_ANIM_REFRESH_RATE = 30f

    const val URL_ABOUT = "https://github.com/vinceumo/Elauncher"
    const val URL_PRIVACY = "https://github.com/vinceumo/Elauncher"
    const val URL_DOUBLE_TAP = "https://github.com/vinceumo/Elauncher"
    const val URL_GITHUB = "https://github.com/vinceumo/Elauncher"
    const val URL_DUCK_SEARCH = "https://duck.co/?q="
    const val URL_DIGITAL_WELLBEING_LEARN_MORE = "https://tanujnotes.substack.com/p/digital-wellbeing-app-on-android?utm_source=olauncher"

    const val DIGITAL_WELLBEING_PACKAGE_NAME = "com.google.android.apps.wellbeing"
    const val DIGITAL_WELLBEING_ACTIVITY = "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
    const val DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME = "com.samsung.android.forest"
    const val DIGITAL_WELLBEING_SAMSUNG_ACTIVITY = "com.samsung.android.forest.launcher.LauncherActivity"
}