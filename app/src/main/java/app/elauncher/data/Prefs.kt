package app.elauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import app.elauncher.helper.appUsagePermissionGranted
import java.util.UUID

class Prefs(private val context: Context) {
    private val PREFS_FILENAME = "app.elauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val USER_STATE = "USER_STATE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val SHARE_SHOWN_TIME = "SHARE_SHOWN_TIME"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val BOLD_FONT = "BOLD_FONT"
    private val USE_CUSTOM_FONT = "USE_CUSTOM_FONT"
    private val CUSTOM_FONT_NAME = "CUSTOM_FONT_NAME"
    private val PENDING_FONT_CHANGE = "PENDING_FONT_CHANGE"
    private val PENDING_WIDGET_ID = "PENDING_WIDGET_ID"
    private val PENDING_WIDGET_COL = "PENDING_WIDGET_COL"
    private val PENDING_WIDGET_ROW = "PENDING_WIDGET_ROW"
    private val PAGES = "PAGES"
    private val CURRENT_PAGE = "CURRENT_PAGE"
    private val GRID_CELL_SIZE_DP = "GRID_CELL_SIZE_DP"
    private val APP_LIST_MIGRATION_DONE = "APP_LIST_MIGRATION_DONE"
    private val CLOCK_DATE_SPLIT_DONE = "CLOCK_DATE_SPLIT_DONE"
    private val PAGES_PRE_CLOCK_SPLIT_BACKUP = "PAGES_PRE_CLOCK_SPLIT_BACKUP"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"
    private val REDUCE_ANIMATIONS = "REDUCE_ANIMATIONS"
    private val BACKGROUND_OPACITY = "BACKGROUND_OPACITY"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"

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
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val IS_SHORTCUT_1 = "IS_SHORTCUT_1"
    private val SHORTCUT_ID_1 = "SHORTCUT_ID_1"
    private val IS_SHORTCUT_2 = "IS_SHORTCUT_2"
    private val SHORTCUT_ID_2 = "SHORTCUT_ID_2"
    private val IS_SHORTCUT_3 = "IS_SHORTCUT_3"
    private val SHORTCUT_ID_3 = "SHORTCUT_ID_3"
    private val IS_SHORTCUT_4 = "IS_SHORTCUT_4"
    private val SHORTCUT_ID_4 = "SHORTCUT_ID_4"
    private val IS_SHORTCUT_5 = "IS_SHORTCUT_5"
    private val SHORTCUT_ID_5 = "SHORTCUT_ID_5"
    private val IS_SHORTCUT_6 = "IS_SHORTCUT_6"
    private val SHORTCUT_ID_6 = "SHORTCUT_ID_6"
    private val IS_SHORTCUT_7 = "IS_SHORTCUT_7"
    private val SHORTCUT_ID_7 = "SHORTCUT_ID_7"
    private val IS_SHORTCUT_8 = "IS_SHORTCUT_8"
    private val SHORTCUT_ID_8 = "SHORTCUT_ID_8"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    // Opacity (0-100) of the Settings/App-Drawer background scrim. 100 = fully opaque.
    var backgroundOpacity: Int
        get() = prefs.getInt(BACKGROUND_OPACITY, 100)
        set(value) = prefs.edit { putInt(BACKGROUND_OPACITY, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    var boldFont: Boolean
        get() = prefs.getBoolean(BOLD_FONT, false)
        set(value) = prefs.edit { putBoolean(BOLD_FONT, value).apply() }

    // Manual override to skip fragment-transition/scroll animations, ORed into the existing
    // e-ink animation gates - for slow-refresh displays that isEinkDisplay() doesn't detect.
    var reduceAnimations: Boolean
        get() = prefs.getBoolean(REDUCE_ANIMATIONS, false)
        set(value) = prefs.edit { putBoolean(REDUCE_ANIMATIONS, value).apply() }

    // Separate on/off flag from "is a custom font file present": turning this off reverts to
    // the system font (light/bold) without deleting the stored file picked via FontManager, so
    // the user can toggle back on without re-picking.
    var useCustomFont: Boolean
        get() = prefs.getBoolean(USE_CUSTOM_FONT, false)
        set(value) = prefs.edit { putBoolean(USE_CUSTOM_FONT, value).apply() }

    // Display name of the picked font file (e.g. "MyFont.ttf"), shown in the settings row so the
    // user can see what's active without re-opening the file picker. Set alongside useCustomFont
    // when a pick succeeds, cleared when the user resets to the default font.
    var customFontName: String
        get() = prefs.getString(CUSTOM_FONT_NAME, "").toString()
        set(value) = prefs.edit { putString(CUSTOM_FONT_NAME, value).apply() }

    // One-shot flag: FontPickerActivity sets this after a successful pick/reset (it can't call
    // MainActivity.recreate() directly - different Activity instance). SettingsFragment.onResume()
    // checks and clears it, calling recreate() exactly once to propagate the new typeface
    // app-wide, matching the existing bold-font/text-size "recreate() on any visual change"
    // pattern. See FontPickerActivity's kdoc for why this indirection exists.
    var pendingFontChange: Boolean
        get() = prefs.getBoolean(PENDING_FONT_CHANGE, false)
        set(value) = prefs.edit { putBoolean(PENDING_FONT_CHANGE, value).apply() }

    // One-shot handoff from WidgetPickerActivity back to HomeFragment, same indirection as
    // pendingFontChange above (and for the same platform reason - see WidgetPickerActivity's
    // kdoc): the picker activity finishes the pick/bind/configure round trip, records the bound
    // widget id and the cell the user long-pressed here, and HomeFragment.onResume() consumes all
    // three exactly once (resetting them to -1) to append the widget to the current page.
    // -1 means "nothing pending" - AppWidgetManager.INVALID_APPWIDGET_ID is 0, so a real id is
    // never -1.
    var pendingWidgetId: Int
        get() = prefs.getInt(PENDING_WIDGET_ID, -1)
        set(value) = prefs.edit { putInt(PENDING_WIDGET_ID, value).apply() }

    var pendingWidgetCol: Int
        get() = prefs.getInt(PENDING_WIDGET_COL, -1)
        set(value) = prefs.edit { putInt(PENDING_WIDGET_COL, value).apply() }

    var pendingWidgetRow: Int
        get() = prefs.getInt(PENDING_WIDGET_ROW, -1)
        set(value) = prefs.edit { putInt(PENDING_WIDGET_ROW, value).apply() }

    // Backed by a JSON blob (org.json) rather than a primitive: pages hold a variable-length
    // list of grid items, which doesn't fit SharedPreferences' flat key/value shape.
    //
    // On first read (stored value blank), this is a one-time upgrade path: an in-place update
    // from a pre-page-model Elauncher build may have the old flat appName1..8 slots (still
    // functional today) already populated by the user. If so, convert the non-blank slots into
    // a single "Home" page instead of defaulting to blank. Once PAGES is non-empty on any later
    // read, the old flat slots are never consulted again.
    var pages: List<Page>
        get() {
            val stored = prefs.getString(PAGES, "").toString()
            if (stored.isBlank()) {
                val flatSlotPages = migratePagesFromFlatSlots()
                // A genuinely fresh install (no legacy appName1..8 data to carry over) gets the same
                // starter content newly-added pages get - which is nothing at all now that every
                // widget is long-press-added (see newPageDefaultItems), so this branch is a no-op
                // kept as the single seam a seeded default would come back through.
                // migratePagesFromFlatSlots() returns exactly one "Home" page with an empty item
                // list in that case, so an empty first page is the signal - and it stays the signal
                // end-to-end, because migrateAppItemsToAppLists synthesizes nothing for a page that
                // started empty either.
                val migrated = if (flatSlotPages.singleOrNull()?.items?.isEmpty() == true) {
                    listOf(flatSlotPages.single().copy(items = newPageDefaultItems(defaultColumnCount())))
                } else {
                    flatSlotPages
                }
                // Built from today's defaultColumnCount(), so it is already in current-cell-size
                // units - record that so the rescale below never touches it.
                gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
                pages = migrated
                // Not exempt from the App List migration the way it is from the rescale:
                // migratePagesFromFlatSlots() still emits GridItemType.APP items, which genuinely
                // need converting, so both branches go through the same pass. (For the blank
                // synthetic page above both migrations are no-ops - nothing to convert, nothing to
                // split - but they still run so their run-once markers get set.)
                return splitDateTimeItems(migrateToAppLists(migrated))
            }
            val storedPages = stored.toPages()
            // App Lists first, then the clock/date split: the split's fallback placement scans
            // every item on the page, so it has to see the converted (APP -> APP_LIST) set and any
            // item migrateToAppLists synthesized, not the pre-conversion one.
            return splitDateTimeItems(migrateToAppLists(rescaleForCurrentCellSize(storedPages)))
        }
        set(value) = prefs.edit { putString(PAGES, value.toJson()).apply() }

    /**
     * The cell size, in dp, that this install's stored [pages] were laid out against.
     *
     * Defaults to [Constants.Grid.LEGACY_CELL_SIZE_DP] so any install predating the 48dp grid is
     * recognised as needing the one-time rescale in [rescaleForCurrentCellSize]; that rescale
     * writes the current size here, which is what stops it from ever running twice.
     */
    var gridCellSizeDp: Int
        get() = prefs.getInt(GRID_CELL_SIZE_DP, Constants.Grid.LEGACY_CELL_SIZE_DP)
        set(value) = prefs.edit { putInt(GRID_CELL_SIZE_DP, value).apply() }

    /**
     * One-time, guarded migration: items are stored as cell *counts*, so a changed cell size moves
     * and resizes every one of them unless their counts are restated in the new unit. Runs at most
     * once per cell-size change (see [gridCellSizeDp]) and persists its result immediately.
     */
    private fun rescaleForCurrentCellSize(storedPages: List<Page>): List<Page> {
        val storedCellSizeDp = gridCellSizeDp
        if (storedCellSizeDp == Constants.Grid.CELL_SIZE_DP) return storedPages
        val rescaled = storedPages.rescaleForCellSize(
            oldCellSizeDp = storedCellSizeDp,
            newCellSizeDp = Constants.Grid.CELL_SIZE_DP,
            columnCount = defaultColumnCount(),
            rowCount = defaultRowCount(),
        )
        gridCellSizeDp = Constants.Grid.CELL_SIZE_DP
        pages = rescaled
        return rescaled
    }

    /**
     * Marker for the one-time [migrateToAppLists] pass, same run-once shape as [gridCellSizeDp]:
     * set as soon as the migration has run, and never cleared. Without it a second read would
     * re-wrap already-wrapped items and append a second DATE_TIME item to every page.
     */
    var appListMigrationDone: Boolean
        get() = prefs.getBoolean(APP_LIST_MIGRATION_DONE, false)
        set(value) = prefs.edit { putBoolean(APP_LIST_MIGRATION_DONE, value).apply() }

    /**
     * One-time, guarded migration off standalone APP grid items onto App List items, plus a
     * synthesized Date & Screen Time item for any page lacking one. Persists its result immediately
     * so the very next read is a plain load of already-migrated data.
     *
     * The global settings the synthesized item inherits are read here, before anything is written,
     * so it comes up looking like what the fixed header showed until now.
     */
    private fun migrateToAppLists(storedPages: List<Page>): List<Page> {
        if (appListMigrationDone) return storedPages
        val migrated = storedPages.migrateAppItemsToAppLists(
            columnCount = defaultColumnCount(),
            rowCount = defaultRowCount(),
            dateTimeAlignment = homeAlignment,
            dateTimeShowScreenTime = screenTimeAvailable(),
            dateTimeVisibility = dateTimeVisibility,
        )
        appListMigrationDone = true
        pages = migrated
        return migrated
    }

    /**
     * Marker for the one-time [splitDateTimeItems] pass, same run-once shape as
     * [appListMigrationDone]: set as soon as the split has run, and never cleared. Without it a
     * second read would split the already-split DATE_TIME items again, adding a second CLOCK item
     * per page every time the pages are loaded.
     */
    var clockDateSplitDone: Boolean
        get() = prefs.getBoolean(CLOCK_DATE_SPLIT_DONE, false)
        set(value) = prefs.edit { putBoolean(CLOCK_DATE_SPLIT_DONE, value).apply() }

    /**
     * The raw [PAGES] json exactly as it stood immediately before [splitDateTimeItems] ran, or ""
     * if that has not happened yet.
     *
     * The split is one-way, guarded, and runs against the only copy of a real page layout: this is
     * the escape hatch if it turns out to have placed something wrong. Written once and then left
     * alone forever - a few KB of json is a cheap price for being able to recover a layout by hand
     * (adb shell run-as app.elauncher cat shared_prefs/app.elauncher.xml).
     */
    var pagesPreClockSplitBackup: String
        get() = prefs.getString(PAGES_PRE_CLOCK_SPLIT_BACKUP, "").toString()
        set(value) = prefs.edit { putString(PAGES_PRE_CLOCK_SPLIT_BACKUP, value).apply() }

    /**
     * One-time, guarded split of every pre-split combined clock+date item into a CLOCK item plus a
     * simplified DATE_TIME one (see [splitDateTimeIntoClockAndDate] for what each old
     * dateTimeVisibility becomes and how the two are placed). Persists its result immediately so
     * the very next read is a plain load of already-split data.
     *
     * Backs the incoming json up first - see [pagesPreClockSplitBackup].
     */
    private fun splitDateTimeItems(storedPages: List<Page>): List<Page> {
        if (clockDateSplitDone) return storedPages
        val raw = prefs.getString(PAGES, "").toString()
        // Both callers have already persisted [storedPages], so raw is normally exactly it;
        // re-serializing covers the case where it somehow isn't, rather than storing a blank backup.
        pagesPreClockSplitBackup = raw.ifBlank { storedPages.toJson() }
        val split = storedPages.map { page ->
            page.copy(
                items = page.items
                    .splitDateTimeIntoClockAndDate(
                        columnCount = defaultColumnCount(),
                        rowCount = defaultRowCount(),
                    )
                    .toMutableList(),
            )
        }
        clockDateSplitDone = true
        pages = split
        return split
    }

    /**
     * The current source of truth for "is screen time actually being shown", matching what
     * SettingsFragment's screen-time row reports and what HomeFragment.applyScreenTime() gates on:
     * the usage-access permission on Q+. Deliberately not gated on [dateTimeVisibility] - that
     * controls the clock/date text, never the screen-time line.
     *
     * runCatching because this is reached from a plain-JUnit unit test's mocked Context, where the
     * AppOps lookup has no implementation to call.
     */
    private fun screenTimeAvailable(): Boolean = runCatching {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.appUsagePermissionGranted()
    }.getOrDefault(false)

    var currentPageIndex: Int
        get() {
            val pageCount = pages.size
            if (pageCount == 0) return 0
            val stored = prefs.getInt(CURRENT_PAGE, 0)
            return stored.coerceIn(0, pageCount - 1)
        }
        set(value) = prefs.edit { putInt(CURRENT_PAGE, value).apply() }

    private fun migratePagesFromFlatSlots(): List<Page> {
        val columnCount = defaultColumnCount()
        val items = mutableListOf<GridItem>()
        for (location in 1..8) {
            val name = getAppName(location)
            if (name.isBlank()) continue
            items.add(
                GridItem(
                    type = GridItemType.APP,
                    col = 0,
                    row = location - 1,
                    spanX = columnCount,
                    spanY = 1,
                    appName = name,
                    appPackage = getAppPackage(location),
                    appActivityClassName = getAppActivityClassName(location),
                    appUser = getAppUser(location),
                    isShortcut = getIsShortcut(location),
                    shortcutId = getShortcutId(location).ifBlank { null },
                ),
            )
        }
        return listOf(Page(id = UUID.randomUUID().toString(), name = "Home", items = items))
    }

    /**
     * How many whole cells the grid holds across, predicted from the screen size.
     *
     * The screen is not the grid: item_home_page.xml insets HomeGridView on every side, so the
     * margins have to come off before dividing, exactly as HomeFragment.gridGeometry()'s fallback
     * does. Without that subtraction this over-reported by a column, and the then-existing
     * MainViewModel.saveAppAtCell() - which sized a newly-placed app's spanX to a full row from
     * this - created items one column wider than the grid they had to fit inside.
     *
     * A prediction, not the truth: HomeGridView.columnCount()/rowCount() are authoritative once it
     * has been measured. Used where no measured grid is in reach (placement, the one-time rescale).
     */
    fun defaultColumnCount(): Int {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        return usableCellCount(screenWidthDp, Constants.Grid.HORIZONTAL_MARGIN_DP)
    }

    /** Vertical counterpart of [defaultColumnCount]. */
    fun defaultRowCount(): Int {
        val screenHeightDp = context.resources.configuration.screenHeightDp
        return usableCellCount(screenHeightDp, Constants.Grid.VERTICAL_MARGIN_DP)
    }

    private fun usableCellCount(screenSizeDp: Int, marginDp: Int): Int =
        maxOf(1, (screenSizeDp - marginDp) / Constants.Grid.CELL_SIZE_DP)

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }

    var shareShownTime: Long
        get() = prefs.getLong(SHARE_SHOWN_TIME, 0L)
        set(value) = prefs.edit { putLong(SHARE_SHOWN_TIME, value).apply() }

    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value).apply() }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value).apply() }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value).apply() }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value).apply() }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value).apply() }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value).apply() }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value).apply() }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value).apply() }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value).apply() }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value).apply() }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value).apply() }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value).apply() }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value).apply() }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value).apply() }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value).apply() }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value).apply() }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value).apply() }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value).apply() }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value).apply() }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value).apply() }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value).apply() }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value).apply() }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value).apply() }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value).apply() }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value).apply() }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value).apply() }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value).apply() }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value).apply() }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value).apply() }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value).apply() }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value).apply() }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value).apply() }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var isShortcut1: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_1, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_1, value) }

    var shortcutId1: String
        get() = prefs.getString(SHORTCUT_ID_1, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_1, value) }

    var isShortcut2: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_2, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_2, value) }

    var shortcutId2: String
        get() = prefs.getString(SHORTCUT_ID_2, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_2, value) }

    var isShortcut3: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_3, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_3, value) }

    var shortcutId3: String
        get() = prefs.getString(SHORTCUT_ID_3, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_3, value) }

    var isShortcut4: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_4, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_4, value) }

    var shortcutId4: String
        get() = prefs.getString(SHORTCUT_ID_4, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_4, value) }

    var isShortcut5: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_5, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_5, value) }

    var shortcutId5: String
        get() = prefs.getString(SHORTCUT_ID_5, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_5, value) }

    var isShortcut6: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_6, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_6, value) }

    var shortcutId6: String
        get() = prefs.getString(SHORTCUT_ID_6, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_6, value) }

    var isShortcut7: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_7, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_7, value) }

    var shortcutId7: String
        get() = prefs.getString(SHORTCUT_ID_7, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_7, value) }

    var isShortcut8: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_8, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_8, value) }

    var shortcutId8: String
        get() = prefs.getString(SHORTCUT_ID_8, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_8, value) }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    fun getAppName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_NAME_1, "").toString()
            2 -> prefs.getString(APP_NAME_2, "").toString()
            3 -> prefs.getString(APP_NAME_3, "").toString()
            4 -> prefs.getString(APP_NAME_4, "").toString()
            5 -> prefs.getString(APP_NAME_5, "").toString()
            6 -> prefs.getString(APP_NAME_6, "").toString()
            7 -> prefs.getString(APP_NAME_7, "").toString()
            8 -> prefs.getString(APP_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppPackage(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_PACKAGE_1, "").toString()
            2 -> prefs.getString(APP_PACKAGE_2, "").toString()
            3 -> prefs.getString(APP_PACKAGE_3, "").toString()
            4 -> prefs.getString(APP_PACKAGE_4, "").toString()
            5 -> prefs.getString(APP_PACKAGE_5, "").toString()
            6 -> prefs.getString(APP_PACKAGE_6, "").toString()
            7 -> prefs.getString(APP_PACKAGE_7, "").toString()
            8 -> prefs.getString(APP_PACKAGE_8, "").toString()
            else -> ""
        }
    }

    fun getAppActivityClassName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
            2 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
            3 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
            4 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
            5 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
            6 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
            7 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
            8 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppUser(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_USER_1, "").toString()
            2 -> prefs.getString(APP_USER_2, "").toString()
            3 -> prefs.getString(APP_USER_3, "").toString()
            4 -> prefs.getString(APP_USER_4, "").toString()
            5 -> prefs.getString(APP_USER_5, "").toString()
            6 -> prefs.getString(APP_USER_6, "").toString()
            7 -> prefs.getString(APP_USER_7, "").toString()
            8 -> prefs.getString(APP_USER_8, "").toString()
            else -> ""
        }
    }

    fun getShortcutId(location: Int): String {
        return when (location) {
            1 -> shortcutId1
            2 -> shortcutId2
            3 -> shortcutId3
            4 -> shortcutId4
            5 -> shortcutId5
            6 -> shortcutId6
            7 -> shortcutId7
            8 -> shortcutId8
            else -> ""
        }
    }

    fun getIsShortcut(location: Int): Boolean {
        return when (location) {
            1 -> isShortcut1
            2 -> isShortcut2
            3 -> isShortcut3
            4 -> isShortcut4
            5 -> isShortcut5
            6 -> isShortcut6
            7 -> isShortcut7
            8 -> isShortcut8
            else -> false
        }
    }

    fun setAppActivityClassName(location: Int, activityClassName: String) {
        when (location) {
            1 -> appActivityClassName1 = activityClassName
            2 -> appActivityClassName2 = activityClassName
            3 -> appActivityClassName3 = activityClassName
            4 -> appActivityClassName4 = activityClassName
            5 -> appActivityClassName5 = activityClassName
            6 -> appActivityClassName6 = activityClassName
            7 -> appActivityClassName7 = activityClassName
            8 -> appActivityClassName8 = activityClassName
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..8) {
            if (getAppPackage(i) == packageName) setAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }
}