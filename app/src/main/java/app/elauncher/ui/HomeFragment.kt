package app.elauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.elauncher.MainViewModel
import app.elauncher.R
import app.elauncher.data.AppModel
import app.elauncher.data.AppSlot
import app.elauncher.data.Constants
import app.elauncher.data.DEFAULT_APP_LIST_SLOT_COUNT
import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import app.elauncher.data.Prefs
import app.elauncher.data.defaultAppListSpanX
import app.elauncher.data.defaultAppListSpanY
import app.elauncher.data.defaultClockSpanX
import app.elauncher.data.defaultClockSpanY
import app.elauncher.data.defaultDateTimeSpanX
import app.elauncher.data.defaultDateTimeSpanY
import app.elauncher.databinding.DialogAppListSettingsBinding
import app.elauncher.databinding.DialogClockSettingsBinding
import app.elauncher.databinding.DialogDateTimeSettingsBinding
import app.elauncher.databinding.FragmentHomeBinding
import app.elauncher.databinding.ItemHomePageBinding
import app.elauncher.helper.FontManager
import app.elauncher.helper.WidgetHostManager
import app.elauncher.helper.WidgetPickerActivity
import app.elauncher.helper.appUsagePermissionGranted
import app.elauncher.helper.dpToPx
import app.elauncher.helper.expandNotificationDrawer
import app.elauncher.helper.getUserHandleFromString
import app.elauncher.helper.isPackageInstalled
import app.elauncher.helper.openAlarmApp
import app.elauncher.helper.openCalendar
import app.elauncher.helper.openCameraApp
import app.elauncher.helper.openDialerApp
import app.elauncher.helper.openSearch
import app.elauncher.helper.showToast
import app.elauncher.listener.OnSwipeTouchListener
import app.elauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var pagerAdapter: HomePagerAdapter

    /**
     * The home alignment preference - used to apply to every page's dateTimeLayout on bind
     * (see setHomeAlignment()) back when the date/time header was fixed page chrome. That view
     * is gone as of Step 7; kept here for Step 10 to re-apply to the new DATE_TIME widget's own
     * views.
     */
    private var homeAlignment: Int = Gravity.START

    /**
     * Latest measured screen-time text - used to apply to every page's tvScreenTime on bind
     * (see currentScreenTimeText()) back when it was fixed page chrome. That view is gone as of
     * Step 7; kept here for Step 10 to re-apply to the new DATE_TIME widget's own views.
     */
    private var screenTimeText: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        homeAlignment = prefs.homeAlignment
        initPager()
        initObservers()
        initSwipeTouchListener()
        initClickListeners()
    }

    /**
     * Sets up the home screen's ViewPager2: one page per app.elauncher.data.Page, switched only
     * by tapping a dot in pageIndicator - isUserInputEnabled is false because single-finger
     * swipe-left/right is reserved for the existing quick-launch gesture (plan.md).
     */
    private fun initPager() {
        pagerAdapter = HomePagerAdapter(
            pageCount = { prefs.pages.size.coerceAtLeast(1) },
            bindPage = ::bindPage
        )
        binding.homePager.adapter = pagerAdapter
        binding.homePager.isUserInputEnabled = false
        binding.homePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                prefs.currentPageIndex = position
                refreshPageIndicator(position)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Runs before populateHomeScreen() on purpose: a widget just added through
        // WidgetPickerActivity has to be in prefs.pages before the pager re-renders, otherwise it
        // wouldn't appear until some later refresh.
        consumePendingWidgetPlacement()
        populateHomeScreen(false)
        viewModel.isElauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            // R.id.clock/date/tvScreenTime handled these until Step 7 removed the fixed header
            // views they belonged to - openClockApp()/openCalendarApp() are now wired directly as
            // onClickListeners on the DATE_TIME widget's own dynamically-built views instead of
            // through this id-keyed dispatch (see populateHomeGridFor()). tvScreenTime's
            // openScreenTimeDigitalWellbeing() is left unwired - see Step 10 deviation notes.
        }
    }

    // Wired (Step 10) as the DATE_TIME widget's clock line's onClickListener - see
    // populateHomeGridFor()'s setItems call.
    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    // Wired (Step 10) as the DATE_TIME widget's date line's onClickListener - see
    // populateHomeGridFor()'s setItems call.
    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    /**
     * Long-press-to-reassign for the DATE_TIME widget's clock line. This exact flow lived inline
     * in onLongClick's `when (view.id)` until Step 7 removed the fixed header view it dispatched
     * from; reconstructed here as its own function so it can be wired onto the dynamically-built
     * clock TextClock instead (no fixed R.id to switch on any more). Clearing the assignment
     * before opening the picker is what makes MainViewModel's save flow treat the next pick as a
     * fresh assignment rather than appending.
     */
    private fun reassignClockApp() {
        showAppList(Constants.FLAG_SET_CLOCK_APP)
        prefs.clockAppPackage = ""
        prefs.clockAppClassName = ""
        prefs.clockAppUser = ""
    }

    /** Same as [reassignClockApp], for the date line / calendar app. */
    private fun reassignCalendarApp() {
        showAppList(Constants.FLAG_SET_CALENDAR_APP)
        prefs.calendarAppPackage = ""
        prefs.calendarAppClassName = ""
        prefs.calendarAppUser = ""
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isElauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
            // R.id.clock/date/tvScreenTime handled long-press-to-reassign here until Step 7
            // removed the fixed header views they belonged to (the FLAG_SET_CLOCK_APP/
            // FLAG_SET_CALENDAR_APP/FLAG_SET_SCREEN_TIME_APP showAppList() flows themselves are
            // untouched, still wired up in AppDrawerFragment) - reassignClockApp()/
            // reassignCalendarApp() above are now wired directly as onLongClickListeners on the
            // DATE_TIME widget's own dynamically-built views instead (see populateHomeGridFor()).
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isElauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            pagerAdapter.notifyDataSetChanged()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let {
                screenTimeText = it
                pagerAdapter.notifyDataSetChanged()
            }
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        // Grid cells get their own per-cell touch listener from cellTouchListenerFor(), wired up
        // each time populateHomeGrid() calls homeGridView.setItems() - see populateHomeScreen().
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        // The old fixed clock/date/tvScreenTime views (removed in Step 7) used to be wired
        // per-instance in bindPage(), since pages are fully independent (plan.md). Their listeners
        // are now wired directly onto the DATE_TIME widget's own dynamically-built views instead,
        // via populateHomeGridFor()'s setItems call, not through this id-keyed dispatch.
    }

    /**
     * Binds one page's grid content - called by HomePagerAdapter for every page (occupied or the
     * "at least 1 blank page" fallback). Until Step 7 this also bound the page's fixed
     * clock/date/screen-time header; that's gone now (Date & Screen Time is a DATE_TIME GridItem
     * rendered inside HomeGridView, wired up by Step 10 instead).
     */
    private fun bindPage(pageBinding: ItemHomePageBinding, pageIndex: Int) {
        populateHomeGridFor(pageBinding, pageIndex)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        // homeBottomAlignment/vertical gravity and per-item horizontal gravity used to apply to
        // the homeApp1..8 labels in the old vertically-stacked layout; HomeGridView lays cells out
        // at absolute (col, row) positions and has no gravity concept. The date/time header's
        // alignment (this function's only remaining effect before Step 7) is gone too, now that
        // the header itself was removed - homeAlignment is kept for Step 10 to re-apply to the new
        // DATE_TIME widget's own views.
        homeAlignment = horizontalGravity
        if (::pagerAdapter.isInitialized) pagerAdapter.notifyDataSetChanged()
    }

    /**
     * The date/time header's date-line text (e.g. "Thu, 30 Dec" or, with the status bar hidden,
     * "Thu, 30 Dec - 82%"). Preserved from the pre-Step-7 populateDateTime(), which also toggled
     * view visibility for a since-removed fixed header - just the string-formatting logic
     * survives here. Wired (Step 10) as populateHomeGridFor()'s dateTextProvider, re-resolved on
     * every HomeGridView rebuild (same cadence the old fixed header refreshed it on - a bind, not
     * a timer).
     */
    private fun formatDateText(): String {
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        return dateText.replace(".,", ",")
    }

    /** Kicks off the (possibly expensive) screen-time measurement once per refresh, not per page. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun refreshScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return
        viewModel.getTodaysScreenTime()
    }

    /**
     * Whether the screen-time text should currently be shown at all, and what it should say if
     * so - null means "don't show". Preserved from the pre-Step-7 applyScreenTime(), minus the
     * margin/gravity computation that only made sense for that single fixed-position view (tied
     * to the old header's layout and to global homeAlignment). Wired (Step 10) as
     * populateHomeGridFor()'s screenTimeTextProvider.
     */
    private fun currentScreenTimeText(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (requireContext().appUsagePermissionGranted().not()) return null
        return screenTimeText
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) refreshScreenTime()
        refreshPager()
    }

    /**
     * Re-renders the pager from prefs.pages (e.g. a page was added/removed/reordered in Settings,
     * or on every resume) and keeps homePager.currentItem/prefs.currentPageIndex/the dot row in
     * sync.
     */
    private fun refreshPager() {
        val pageCount = prefs.pages.size.coerceAtLeast(1)
        pagerAdapter.notifyDataSetChanged()

        val targetIndex = prefs.currentPageIndex.coerceIn(0, pageCount - 1)
        prefs.currentPageIndex = targetIndex
        if (binding.homePager.currentItem != targetIndex) {
            binding.homePager.setCurrentItem(targetIndex, false)
        }
        refreshPageIndicator(targetIndex)
    }

    /** Rebuilds the dot row for the current page count and highlights [selectedIndex]. */
    private fun refreshPageIndicator(selectedIndex: Int) {
        // Deferred via post(): when this runs from onResume() right after HomeFragment's view is
        // freshly (re)created (e.g. returning from Settings), pageIndicator hasn't been through
        // its first layout pass yet - children added synchronously here would intermittently end
        // up unmeasured/invisible until some later, unrelated layout pass happened to fix them up
        // (same class of bug as HomeGridView.onSizeChanged in Step 7/9 - see history.md). post()
        // defers the rebuild to right after the current view tree settles instead.
        val binding = _binding ?: return
        binding.pageIndicator.post {
            // Fragment view may have been destroyed, or the fragment detached from its context
            // (e.g. a system dialog like the "set as default launcher" role request interrupting
            // the lifecycle), by the time this deferred block runs - requireContext() below needs
            // both checked, not just the binding.
            if (_binding == null || !isAdded) return@post
            val pageCount = prefs.pages.size.coerceAtLeast(1)
            binding.pageIndicator.removeAllViews()
            binding.pageIndicator.isVisible = pageCount > 1
            if (pageCount <= 1) return@post

            val dotSize = 8.dpToPx()
            val dotMargin = 4.dpToPx()
            for (i in 0 until pageCount) {
                val dot = View(requireContext()).apply {
                    setBackgroundResource(R.drawable.page_indicator_dot)
                    alpha = if (i == selectedIndex) 1f else 0.4f
                    setOnClickListener {
                        binding.homePager.setCurrentItem(i, true)
                        prefs.currentPageIndex = i
                        refreshPageIndicator(i)
                    }
                }
                val params = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                binding.pageIndicator.addView(dot, params)
            }
        }
    }

    /**
     * Reads [pageIndex]'s grid items from Prefs and renders them into [pageBinding]'s
     * HomeGridView. Unlike the old flat homeApp1..8 population, HomeGridView.setItems() always
     * tears down and rebuilds every cell view itself, so there's no separate "hide stale views
     * first" step.
     */
    private fun populateHomeGridFor(pageBinding: ItemHomePageBinding, pageIndex: Int) {
        val pages = prefs.pages
        val page = pages.getOrNull(pageIndex)
        if (page == null) {
            pageBinding.homeGridView.setItems(emptyList(), ::cellTouchListenerFor)
            return
        }

        // No item-level validity filter any more: apps only live inside an App List item's slots
        // now (Step 6/9), and one slot's app going missing must not take the whole item - and its
        // other slots - down with it. That case is resolved per slot instead, see
        // isAppSlotUnavailable()/onAppSlotClick().
        pageBinding.homeGridView.setItems(
            items = page.items,
            touchListenerFor = ::cellTouchListenerFor,
            slotTouchListenerFor = ::slotTouchListenerFor,
            isAppSlotUnavailable = ::isAppSlotUnavailable,
            // Edit mode (Step 19) resolves move/resize inside HomeGridView, which owns no Prefs
            // access, and hands the result back here to be written - deliberately without a
            // notifyItemChanged(), since the grid has already re-laid itself out and a rebind would
            // just drop the user out of edit mode after every single adjustment.
            onItemsChanged = { updatedItems -> persistPageItems(pageIndex, updatedItems) },
            onItemDeleted = ::removeGridItem,
            // Edit mode's gear badge (Step 11): HomeGridView only reports the tap, the per-item
            // settings themselves are edited here, where Prefs and the dialog theme live.
            onOpenSettings = ::showItemSettings,
            // DATE_TIME rendering/wiring (Step 10): HomeGridView touches no Prefs/Context itself,
            // so the formatted text and the clock/date tap/long-press flows are handed in the same
            // way the touch listeners above are.
            dateTextProvider = ::formatDateText,
            screenTimeTextProvider = ::currentScreenTimeText,
            onClockClick = ::openClockApp,
            onClockLongClick = ::reassignClockApp,
            onDateClick = ::openCalendarApp,
            onDateLongClick = ::reassignCalendarApp,
        )
    }

    /** Writes [items] back as [pageIndex]'s item list. See populateHomeGridFor()'s setItems call. */
    private fun persistPageItems(pageIndex: Int, items: List<GridItem>) {
        val pages = prefs.pages
        val page = pages.getOrNull(pageIndex) ?: return
        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = items.toMutableList())
        prefs.pages = updatedPages
    }

    /**
     * True if [slot] is filled but no longer points at something launchable - its app was
     * uninstalled or disabled, or its pinned shortcut was removed, while the slot kept pointing at
     * it. The per-slot counterpart of isUnavailableWidget(), and the direct successor of the old
     * isGridItemValid() (itself adapted from setHomeAppText()), which asked the same question about
     * a whole standalone APP item back when apps were their own grid items.
     *
     * An *empty* slot is not "unavailable" - it is simply unfilled, and taps on it open the picker.
     */
    private fun isAppSlotUnavailable(slot: AppSlot): Boolean {
        val packageName = slot.appPackage
        if (packageName.isNullOrEmpty()) return false
        if (slot.appName.isNullOrEmpty()) return true
        val userString = slot.appUser.orEmpty()

        if (slot.isShortcut) {
            val userHandle = getUserHandleFromString(requireContext(), userString)
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            return try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                shortcuts?.any { it.id == slot.shortcutId } != true
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }

        return !isPackageInstalled(requireContext(), packageName, userString)
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    /**
     * True if [item] is a widget that can no longer be shown - its provider app was uninstalled or
     * disabled, or the item lost its id somehow. Exactly the condition under which HomeGridView
     * renders the "unavailable" placeholder instead of a hosted widget view.
     */
    private fun isUnavailableWidget(item: GridItem): Boolean {
        if (item.type != GridItemType.WIDGET) return false
        val appWidgetId = item.appWidgetId ?: return true
        return WidgetHostManager.providerInfoFor(requireContext(), appWidgetId) == null
    }

    /**
     * Tap on one App List slot: pick an app for it if it is empty, clear it if what it points at is
     * gone (the per-slot counterpart of the "Widget unavailable - tap to remove" cell), otherwise
     * launch it.
     *
     * The picker is the same app drawer the whole app uses, in "set home app" mode, now targeted at
     * (the item's col/row, this slot index) rather than a bare cell - AppDrawerFragment threads
     * those args back into MainViewModel.saveAppInAppListSlot(), which writes the slot.
     */
    private fun onAppSlotClick(item: GridItem, slotIndex: Int) {
        val slot = item.appSlots.getOrNull(slotIndex) ?: return
        when {
            slot.appPackage.isNullOrEmpty() -> showAppList(
                Constants.FLAG_SET_HOME_APP_CELL,
                includeHiddenApps = true, // matches the old long-press-on-home-app behavior
                col = item.col,
                row = item.row,
                slotIndex = slotIndex,
            )

            isAppSlotUnavailable(slot) -> clearAppSlot(item, slotIndex)

            else -> launchAppOrShortcut(
                appName = slot.appName.orEmpty(),
                packageName = slot.appPackage.orEmpty(),
                activityClassName = slot.appActivityClassName,
                shortcutId = slot.shortcutId,
                isShortcut = slot.isShortcut,
                userString = slot.appUser.orEmpty(),
            )
        }
    }

    /**
     * Long press on one App List slot. Slots fill their item's whole cell, so this is also the only
     * route into edit mode for an App List item (plan.md, "Editing gesture model") - hence "Edit
     * widget" being offered on an empty slot too, where there is nothing slot-specific to do.
     * Same dialog shape as showEmptyCellOptions(), custom-font fix-up included.
     */
    private fun showAppSlotOptions(item: GridItem, slotIndex: Int) {
        val slot = item.appSlots.getOrNull(slotIndex) ?: return
        val filled = !slot.appPackage.isNullOrEmpty()
        val options = if (filled)
            arrayOf(
                getString(R.string.rename),
                getString(R.string.remove_this_app),
                getString(R.string.edit_widget),
            )
        else
            arrayOf(getString(R.string.edit_widget))

        val dialog = AlertDialog.Builder(requireContext())
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when {
                    !filled -> currentHomeGridView()?.enterEditMode(item)
                    which == 0 -> showAppSlotRenameDialog(item, slotIndex)
                    which == 1 -> clearAppSlot(item, slotIndex)
                    else -> currentHomeGridView()?.enterEditMode(item)
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Relabels one slot, writing [AppSlot.customLabel] and leaving [AppSlot.appName] (the real app
     * name) alone, so the rename is reversible by clearing the field. Mirrors PagesSettingsFragment's
     * rename-page dialog, the app's existing text-input dialog.
     */
    private fun showAppSlotRenameDialog(item: GridItem, slotIndex: Int) {
        val slot = item.appSlots.getOrNull(slotIndex) ?: return
        val input = AppCompatEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(slot.customLabel ?: slot.appName)
            setSelection(text?.length ?: 0)
            maxLines = 1
        }
        val paddingHorizontal = (RENAME_DIALOG_PADDING_DP * resources.displayMetrics.density).toInt()
        val inputContainer = FrameLayout(requireContext()).apply {
            setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_app)
            .setView(inputContainer)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newLabel = input.text?.toString()?.trim().orEmpty()
                if (newLabel.isNotEmpty()) updateAppSlot(item, slotIndex, slot.copy(customLabel = newLabel))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Empties one slot, deliberately without touching the item's slot *count* - how many slots an
     * App List has is a widget-level setting, not something removing one app should change. Like
     * removeGridItem(), immediate and unconfirmed.
     */
    private fun clearAppSlot(item: GridItem, slotIndex: Int) = updateAppSlot(item, slotIndex, AppSlot())

    /**
     * Writes [slot] into slot [slotIndex] of the App List item at [item]'s cell on the current page,
     * and re-renders that page.
     *
     * Looked up by (col, row) rather than by identity: prefs.pages re-parses its JSON on every read,
     * so the item this fragment hands out to HomeGridView is never the same object as the one a
     * fresh read returns.
     */
    private fun updateAppSlot(item: GridItem, slotIndex: Int, slot: AppSlot) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val target = pages[pageIndex].items.firstOrNull {
            it.type == GridItemType.APP_LIST && it.col == item.col && it.row == item.row
        } ?: return
        if (slotIndex !in target.appSlots.indices) return

        target.appSlots[slotIndex] = slot
        prefs.pages = pages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    /**
     * Opens the per-item settings dialog for [item] - the edit-mode gear badge's action
     * (HomeGridView.hasSettings decides which items get a badge at all, this decides what it opens).
     */
    private fun showItemSettings(item: GridItem) {
        when (item.type) {
            GridItemType.APP_LIST -> showAppListSettings(item)
            GridItemType.DATE_TIME -> showDateTimeSettings(item)
            GridItemType.CLOCK -> showClockSettings(item)
            // Never reached: no gear badge is drawn for these (HomeGridView.hasSettings), and a
            // widget's own settings belong to its provider. Spelled out rather than left to an else
            // so adding a fifth type is a compile error here, not a silently ignored tap.
            GridItemType.APP, GridItemType.WIDGET -> Unit
        }
    }

    /**
     * App List settings: text alignment, and how many app slots the item has (1-8, stepped rather
     * than typed - the range is small enough that +/- is fewer taps than any picker).
     *
     * Nothing is written until Save, so a stepper fiddled with and then cancelled leaves the item
     * exactly as it was. Same AlertDialog shape and custom-font fix-up as showAppSlotRenameDialog(),
     * with an inflated multi-field layout instead of a single text input.
     */
    private fun showAppListSettings(item: GridItem) {
        val content = DialogAppListSettingsBinding.inflate(layoutInflater)
        content.alignmentGroup.check(alignmentRadioId(item.alignment))

        var slotCount = item.appSlots.size.coerceIn(MIN_APP_LIST_SLOT_COUNT, MAX_APP_LIST_SLOT_COUNT)
        fun renderCount() {
            content.countValue.text = slotCount.toString()
            content.countDecrease.isEnabled = slotCount > MIN_APP_LIST_SLOT_COUNT
            content.countIncrease.isEnabled = slotCount < MAX_APP_LIST_SLOT_COUNT
        }
        renderCount()
        content.countDecrease.setOnClickListener {
            if (slotCount > MIN_APP_LIST_SLOT_COUNT) slotCount--
            renderCount()
        }
        content.countIncrease.setOnClickListener {
            if (slotCount < MAX_APP_LIST_SLOT_COUNT) slotCount++
            renderCount()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_list_settings)
            .setView(content.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                applyAppListSettings(
                    item = item,
                    alignment = alignmentFor(content.alignmentGroup.checkedRadioButtonId),
                    slotCount = slotCount,
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Writes an App List item's settings back.
     *
     * Growing the slot count appends empty slots, shrinking drops the trailing ones - filled or not,
     * immediately and unconfirmed, the same rule removeGridItem()/clearAppSlot() already follow for
     * comparably scoped actions (an app leaves the home screen; nothing is uninstalled). The item's
     * height follows the count, keeping the one-slot-per-row invariant HomeGridView's row rendering
     * assumes; clamped to the grid the same way addAppList() clamps a new item's, and to whatever
     * sits below it on the page (clampSpanYToOverlap) so growing this item can't silently occlude a
     * neighbor's origin cell the way HomeGridView.rebuildChildren()'s occupied-cell skip would.
     */
    private fun applyAppListSettings(item: GridItem, alignment: Int, slotCount: Int) {
        updateGridItem(item) { target, items ->
            target.alignment = alignment
            if (target.appSlots.size != slotCount) {
                while (target.appSlots.size < slotCount) target.appSlots.add(AppSlot())
                while (target.appSlots.size > slotCount) target.appSlots.removeAt(target.appSlots.size - 1)
                val desiredSpanY = defaultAppListSpanY(slotCount).coerceAtMost(gridGeometry().second.coerceAtLeast(1))
                target.spanY = clampSpanYToOverlap(target, items, desiredSpanY, gridGeometry().second)
            }
        }
    }

    /**
     * Clock settings: text alignment, and nothing else - a clock has no count, no extra lines and no
     * visibility state (hiding it means removing the widget), so this is showAppListSettings()
     * without the slot-count stepper.
     */
    private fun showClockSettings(item: GridItem) {
        val content = DialogClockSettingsBinding.inflate(layoutInflater)
        content.alignmentGroup.check(alignmentRadioId(item.alignment))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.clock_settings)
            .setView(content.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                applyClockSettings(
                    item = item,
                    alignment = alignmentFor(content.alignmentGroup.checkedRadioButtonId),
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Writes a Clock item's settings back. No span recompute, unlike its App List/Date counterparts:
     * alignment is the only thing this dialog can change and a clock's footprint never follows from
     * it, so there is nothing to clamp against the rest of the page.
     */
    private fun applyClockSettings(item: GridItem, alignment: Int) {
        updateGridItem(item) { target, _ ->
            target.alignment = alignment
        }
    }

    /**
     * Date & Screen Time settings: text alignment and whether the screen-time line is shown.
     *
     * No visibility control - date visibility is presence-only now, so an item that exists always
     * draws its date line and hiding it means removing the widget.
     *
     * Saving with screen time newly turned on and usage-access permission missing posts
     * Constants.Dialog.DIGITAL_WELLBEING, the same permission prompt Settings' own (now removed)
     * "Screen time" row used to open - this switch is the only entry point left for it. Posted after
     * the dialog is dismissed, not from the switch's own change listener: MainActivity renders that
     * prompt as a view inside its own layout (messageLayout), which an open AlertDialog's window
     * would sit on top of, leaving the prompt invisible and its button untappable until the settings
     * dialog was closed.
     *
     * "Ask, don't block": the setting is written either way, matching Prefs.screenTimeAvailable(),
     * which treats the permission as a separate check rather than a gate on the preference itself.
     */
    private fun showDateTimeSettings(item: GridItem) {
        val content = DialogDateTimeSettingsBinding.inflate(layoutInflater)
        content.alignmentGroup.check(alignmentRadioId(item.alignment))
        content.screenTimeSwitch.isChecked = item.showScreenTime

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.date_time_settings)
            .setView(content.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val showScreenTime = content.screenTimeSwitch.isChecked
                applyDateTimeSettings(
                    item = item,
                    alignment = alignmentFor(content.alignmentGroup.checkedRadioButtonId),
                    showScreenTime = showScreenTime,
                )
                dialog.dismiss()
                if (showScreenTime && requireContext().appUsagePermissionGranted().not()) {
                    viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Writes a Date & Screen Time item's settings back, re-deriving its height from the line count
     * (defaultDateTimeSpanY, the same function that sizes a freshly created one).
     *
     * dateTimeVisibility is deliberately left alone: the field is dead after the clock/date split's
     * migration (see GridItem's kdoc) and nothing writes it any more.
     *
     * Growing spanY (turning screen time on) is clamped to whatever sits below this item on the
     * page (clampSpanYToOverlap), for the same reason applyAppListSettings() clamps it.
     */
    private fun applyDateTimeSettings(item: GridItem, alignment: Int, showScreenTime: Boolean) {
        updateGridItem(item) { target, items ->
            target.alignment = alignment
            target.showScreenTime = showScreenTime
            val desiredSpanY = defaultDateTimeSpanY(showScreenTime).coerceAtMost(gridGeometry().second.coerceAtLeast(1))
            target.spanY = clampSpanYToOverlap(target, items, desiredSpanY, gridGeometry().second)
        }
    }

    /**
     * Applies [mutate] to the current page's copy of [item] and persists/re-renders the page.
     *
     * Looked up by type and (col, row) rather than by identity, for exactly the reason
     * updateAppSlot() does: prefs.pages re-parses its JSON on every read, so the GridItem a dialog
     * was opened with is never the same object a fresh read returns.
     *
     * [mutate] also receives the current page's item list (the same list [target] came from, by
     * reference) so a mutation that changes span can check it against the rest of the page - see
     * clampSpanYToOverlap().
     */
    private fun updateGridItem(item: GridItem, mutate: (GridItem, List<GridItem>) -> Unit) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val target = pages[pageIndex].items.firstOrNull {
            it.type == item.type && it.col == item.col && it.row == item.row
        } ?: return

        mutate(target, pages[pageIndex].items)
        prefs.pages = pages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    /**
     * The largest spanY, starting at [target]'s own row and capped at [desiredSpanY], that doesn't
     * overlap another item's cells in [items] or run past [rowCount].
     *
     * HomeGridView's drag-resize (moveTouchListener/resizeTouchListener) rejects any target that
     * overlaps another item outright - "no push/displace behavior". The settings dialogs' spanY
     * changes don't go through that touch path, so without this they could silently grow one item's
     * footprint over a neighbor's origin cell; rebuildChildren()'s occupied-cell skip then treats
     * that neighbor as covered and stops rendering it (and its own cell listener) entirely, rather
     * than the two items visibly overlapping. Clamping here keeps the same "reject growth past a
     * neighbor" rule the drag path already enforces, just applied to a height that changes via a
     * dialog instead of a finger.
     */
    private fun clampSpanYToOverlap(target: GridItem, items: List<GridItem>, desiredSpanY: Int, rowCount: Int): Int {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        items.forEach { other ->
            if (other === target) return@forEach
            for (dx in 0 until other.spanX) {
                for (dy in 0 until other.spanY) {
                    occupied.add((other.col + dx) to (other.row + dy))
                }
            }
        }
        var span = 1
        while (span < desiredSpanY && target.row + span < rowCount) {
            val blocked = (0 until target.spanX).any { dx -> (target.col + dx) to (target.row + span) in occupied }
            if (blocked) break
            span++
        }
        return span.coerceIn(1, desiredSpanY)
    }

    /** The alignment radio button matching [alignment], for pre-selecting a settings dialog. */
    private fun alignmentRadioId(alignment: Int): Int = when (alignment) {
        // CENTER is what Settings' own alignment picker stores (and therefore what the migration
        // copied into pre-existing items); CENTER_HORIZONTAL is accepted as the same choice so an
        // item written with it doesn't come back showing "Left".
        Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> R.id.alignCenter
        Gravity.END -> R.id.alignEnd
        else -> R.id.alignStart
    }

    /** The inverse of [alignmentRadioId] - Gravity.START/CENTER/END, as Prefs.homeAlignment uses. */
    private fun alignmentFor(checkedRadioButtonId: Int): Int = when (checkedRadioButtonId) {
        R.id.alignCenter -> Gravity.CENTER
        R.id.alignEnd -> Gravity.END
        else -> Gravity.START
    }

    /**
     * Removes [item] from the current page's grid, persists that, and refreshes the grid.
     * Reached from the delete badge shown on the selected item in edit mode (Step 19); before that
     * it was long-press-on-an-occupied-cell itself, which now opens edit mode instead.
     * Removal is immediate, with no confirmation dialog: nothing elsewhere in this app gates a
     * similarly-scoped action (removing one app from the home screen, not uninstalling it) behind
     * an in-app confirmation dialog, so inventing one here would be inconsistent with the rest of
     * the app rather than consistent with it.
     *
     * A widget also has to be released on the host side (Step 20): dropping only the GridItem would
     * leak its allocated appWidgetId - the host would go on treating that widget as bound forever,
     * with nothing left on any page that could ever release it. Still correct when the provider is
     * already gone (the id is allocated against this app's own host either way), and done only once
     * the item is known to actually be on the page, so a no-op removal can't unbind a live widget.
     */
    private fun removeGridItem(item: GridItem) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val page = pages[pageIndex]
        val updatedItems = page.items.toMutableList()
        if (!updatedItems.remove(item)) return

        val appWidgetId = item.appWidgetId
        if (item.type == GridItemType.WIDGET && appWidgetId != null) {
            WidgetHostManager.deleteWidgetId(requireContext(), appWidgetId)
        }

        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = updatedItems)
        prefs.pages = updatedPages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(
        flag: Int,
        includeHiddenApps: Boolean = false,
        col: Int = -1,
        row: Int = -1,
        slotIndex: Int = -1,
    ) {
        viewModel.getAppList(includeHiddenApps)
        val args = bundleOf(
            Constants.Key.FLAG to flag,
            Constants.Key.COL to col,
            Constants.Key.ROW to row,
            Constants.Key.SLOT_INDEX to slotIndex,
        )
        try {
            findNavController().navigate(R.id.action_mainFragment_to_appListFragment, args)
        } catch (e: Exception) {
            findNavController().navigate(R.id.appListFragment, args)
            e.printStackTrace()
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    /**
     * Swipe left/right switches pages first; the quick-launch app only fires as a fallback once
     * already at that edge (swipe left -> next/higher-index page, or quick-launch-left if already
     * on the last page; swipe right -> previous page, or quick-launch-right if already on the
     * first). Revised after Step 11 on-device testing: the page-indicator dots proved too small
     * to tap reliably, so swipe becomes the primary page-switch gesture (plan.md Context &
     * decisions) - dots remain a visual indicator/secondary tap target only. Returns true if a
     * page switch happened (caller should skip the quick-launch fallback).
     */
    private fun goToAdjacentPage(forward: Boolean): Boolean {
        val pageCount = prefs.pages.size.coerceAtLeast(1)
        val current = binding.homePager.currentItem
        val target = if (forward) current + 1 else current - 1
        if (target !in 0 until pageCount) return false
        binding.homePager.setCurrentItem(target, true)
        return true
    }

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (!goToAdjacentPage(forward = true)) openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (!goToAdjacentPage(forward = false)) openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else
                    lockPhone()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    /**
     * Builds the per-cell touch listener HomeGridView wires up for every cell (occupied or
     * empty) via setItems() - see HomeGridView's kdoc for why this is a full gesture-resolving
     * listener rather than a narrower callback set. This is the direct successor to the old
     * getViewSwipeTouchListener(context, view), generalized to key off the grid [item] (and
     * [col]/[row] for the empty-cell case) instead of a homeAppN view's id/tag.
     *
     * ViewSwipeTouchListener's constructor wants a View purely to hand back to onClick/onLongClick
     * below; since this factory keys off [item]/[col]/[row] instead of view identity, a fresh
     * placeholder View is enough - it is never attached to the hierarchy or read from.
     */
    private fun cellTouchListenerFor(col: Int, row: Int, item: GridItem?): View.OnTouchListener {
        val context = requireContext()
        return object : ViewSwipeTouchListener(context, View(context)) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (!goToAdjacentPage(forward = true)) openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (!goToAdjacentPage(forward = false)) openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onClick(view: View) {
                super.onClick(view)
                if (item != null) {
                    // A widget whose provider is gone renders as the "Widget unavailable - tap to
                    // remove" placeholder (HomeGridView.widgetPlaceholderView); this is the tap
                    // that label promises. Removal is immediate, matching removeGridItem()'s
                    // no-confirmation rule - and there is nothing to lose here anyway, since the
                    // widget it points at no longer exists. Nothing else here is launchable: apps
                    // live in App List slots now, which handle their own taps.
                    if (isUnavailableWidget(item)) removeGridItem(item)
                }
                // Tapping a genuinely empty cell does nothing: it used to open the app drawer to
                // place a single app there, but standalone app cells no longer exist (Step 9) - an
                // app is added by tapping an empty slot of an App List instead. Adding something to
                // the grid is a long-press action now, like every other one.
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                if (item != null) {
                    // Long-press on an occupied cell selects it for editing: move, resize and
                    // remove all live behind this one gesture (Step 19), the way mobile launchers
                    // usually combine them. Step 9 had this same gesture remove the item outright;
                    // that action is now the delete badge inside edit mode, still with no
                    // confirmation dialog, so nothing became harder to reach.
                    currentHomeGridView()?.enterEditMode(item)
                } else {
                    // Long-press on an empty cell used to go straight to Settings. HomeGridView
                    // tiles the whole screen with cells, so those cells always consume this
                    // gesture before mainLayout's whole-screen fallback listener sees it - making
                    // this the only way to reach Settings from home. Adding a widget therefore
                    // can't simply replace it; both are offered instead.
                    showEmptyCellOptions(col, row)
                }
            }
        }
    }

    /**
     * The per-slot counterpart of [cellTouchListenerFor], for the row views inside an App List item
     * (HomeGridView.createAppListView). Those rows fill the item's cell entirely, so this listener -
     * not the cell's - is what a touch on an App List reaches; it therefore has to resolve the same
     * swipe gestures as well, or page switching and the swipe-up drawer would stop working over
     * what is usually the largest item on the page.
     */
    private fun slotTouchListenerFor(item: GridItem, slotIndex: Int): View.OnTouchListener {
        val context = requireContext()
        return object : ViewSwipeTouchListener(context, View(context)) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (!goToAdjacentPage(forward = true)) openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (!goToAdjacentPage(forward = false)) openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onClick(view: View) {
                super.onClick(view)
                onAppSlotClick(item, slotIndex)
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                showAppSlotOptions(item, slotIndex)
            }
        }
    }

    /**
     * Long-press on an empty grid cell: add a widget, an App List, a clock or a date there, or open
     * Settings. Matches the AlertDialog style used by PagesSettingsFragment's rename/delete dialogs,
     * including its custom-font fix-up (dialog content lives in its own window, outside this
     * fragment's view tree, so BaseFragment's typeface walk never reaches it).
     */
    private fun showEmptyCellOptions(col: Int, row: Int) {
        val options = arrayOf(
            getString(R.string.add_widget),
            getString(R.string.add_app_list),
            getString(R.string.add_clock),
            getString(R.string.add_date_time),
            getString(R.string.settings),
        )
        val dialog = AlertDialog.Builder(requireContext())
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> startWidgetPicker(col, row)
                    1 -> addAppList(col, row)
                    2 -> addClock(col, row)
                    3 -> addDateTime(col, row)
                    else -> openSettings()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
        dialog.window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    /**
     * Hands the whole pick/bind/configure round trip to WidgetPickerActivity with a plain
     * startActivity() - deliberately not for-result: MainActivity is singleTask, where activity
     * results silently never arrive (see WidgetPickerActivity's kdoc). The result comes back
     * through Prefs instead, consumed by consumePendingWidgetPlacement() on the next resume.
     */
    private fun startWidgetPicker(col: Int, row: Int) {
        try {
            startActivity(
                Intent(requireContext(), WidgetPickerActivity::class.java)
                    .putExtra(WidgetPickerActivity.EXTRA_COL, col)
                    .putExtra(WidgetPickerActivity.EXTRA_ROW, row)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Adds a new, all-empty App List item at the long-pressed cell (or the first cell it fits, same
     * rule startWidgetPicker's placement uses). Sized from the same defaults a new page's App List
     * gets - one slot per grid row, so spanY == slot count, the invariant HomeGridView's row
     * rendering assumes.
     */
    private fun addAppList(col: Int, row: Int) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val page = pages[pageIndex]

        val (columnCount, rowCount) = gridGeometry()
        val spanX = defaultAppListSpanX(columnCount)
        val spanY = defaultAppListSpanY(DEFAULT_APP_LIST_SLOT_COUNT).coerceAtMost(rowCount.coerceAtLeast(1))
        val position = firstFreePosition(page.items, col, row, spanX, spanY, columnCount, rowCount)

        val updatedItems = page.items.toMutableList()
        updatedItems.add(
            GridItem(
                type = GridItemType.APP_LIST,
                col = position.first,
                row = position.second,
                spanX = spanX,
                spanY = spanY,
                appSlots = MutableList(DEFAULT_APP_LIST_SLOT_COUNT) { AppSlot() },
                alignment = Gravity.START,
            )
        )
        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = updatedItems)
        prefs.pages = updatedPages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    /**
     * Adds a new Clock item at the long-pressed cell (or the first cell it fits, same rule
     * addAppList()/startWidgetPicker()'s placement uses), sized from the same defaults a new page's
     * clock gets. Alignment is the one setting it has, and starts where a new page's clock starts.
     */
    private fun addClock(col: Int, row: Int) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val page = pages[pageIndex]

        val (columnCount, rowCount) = gridGeometry()
        val spanX = defaultClockSpanX(columnCount)
        val spanY = defaultClockSpanY().coerceAtMost(rowCount.coerceAtLeast(1))
        val position = firstFreePosition(page.items, col, row, spanX, spanY, columnCount, rowCount)

        val updatedItems = page.items.toMutableList()
        updatedItems.add(
            GridItem(
                type = GridItemType.CLOCK,
                col = position.first,
                row = position.second,
                spanX = spanX,
                spanY = spanY,
                alignment = Gravity.START,
            )
        )
        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = updatedItems)
        prefs.pages = updatedPages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    /**
     * Adds a new Date item at the long-pressed cell (or the first cell it fits), sized for the
     * date line alone - screen time starts off, so the item only claims the extra row once the user
     * turns it on in the item's own settings (applyDateTimeSettings re-derives spanY then).
     */
    private fun addDateTime(col: Int, row: Int) {
        val pages = prefs.pages
        if (pages.isEmpty()) return
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val page = pages[pageIndex]

        val (columnCount, rowCount) = gridGeometry()
        val spanX = defaultDateTimeSpanX(columnCount)
        val spanY = defaultDateTimeSpanY(showScreenTime = false).coerceAtMost(rowCount.coerceAtLeast(1))
        val position = firstFreePosition(page.items, col, row, spanX, spanY, columnCount, rowCount)

        val updatedItems = page.items.toMutableList()
        updatedItems.add(
            GridItem(
                type = GridItemType.DATE_TIME,
                col = position.first,
                row = position.second,
                spanX = spanX,
                spanY = spanY,
                alignment = Gravity.START,
                showScreenTime = false,
                // Dead field, kept only because it has to hold some value - a DATE_TIME item's date
                // line is shown whenever the item exists (see GridItem's kdoc). Never read again.
                dateTimeVisibility = Constants.DateTime.ON,
            )
        )
        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = updatedItems)
        prefs.pages = updatedPages
        pagerAdapter.notifyItemChanged(pageIndex)
    }

    private fun openSettings() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * One-shot handoff from WidgetPickerActivity: if it finished a successful add, place the bound
     * widget on the current page at the cell that was long-pressed (or the first cell it fits, if
     * that one is taken) and persist it. All three pending fields are cleared up front so a
     * placement can only ever happen once, even if this resume path runs again.
     */
    private fun consumePendingWidgetPlacement() {
        val appWidgetId = prefs.pendingWidgetId
        if (appWidgetId == -1) return

        val requestedCol = prefs.pendingWidgetCol
        val requestedRow = prefs.pendingWidgetRow
        prefs.pendingWidgetId = -1
        prefs.pendingWidgetCol = -1
        prefs.pendingWidgetRow = -1

        val context = requireContext()
        // Defensive: binding already succeeded in WidgetPickerActivity, so this shouldn't be null.
        val info = WidgetHostManager.providerInfoFor(context, appWidgetId)
        if (info == null) {
            WidgetHostManager.deleteWidgetId(context, appWidgetId)
            return
        }

        val pages = prefs.pages
        if (pages.isEmpty()) {
            WidgetHostManager.deleteWidgetId(context, appWidgetId)
            return
        }
        val pageIndex = prefs.currentPageIndex.coerceIn(0, pages.size - 1)
        val page = pages[pageIndex]

        val (columnCount, rowCount) = gridGeometry()
        val spanX = spanForMinDimension(info.minWidth).coerceAtMost(columnCount)
        val spanY = spanForMinDimension(info.minHeight).coerceAtMost(rowCount)

        val position = firstFreePosition(page.items, requestedCol, requestedRow, spanX, spanY, columnCount, rowCount)
        val updatedItems = page.items.toMutableList()
        updatedItems.add(
            GridItem(
                type = GridItemType.WIDGET,
                col = position.first,
                row = position.second,
                spanX = spanX,
                spanY = spanY,
                appWidgetId = appWidgetId,
            )
        )
        val updatedPages = pages.toMutableList()
        updatedPages[pageIndex] = page.copy(items = updatedItems)
        prefs.pages = updatedPages
    }

    /**
     * The requested cell if the widget fits there without overlapping anything, otherwise the
     * first row-major position it does fit - the simplest correct behavior, since the user can
     * move the widget afterwards. Falls back to the requested cell if the page is too full for
     * the widget's span anywhere (overlapping, but visible and removable).
     */
    private fun firstFreePosition(
        items: List<GridItem>,
        requestedCol: Int,
        requestedRow: Int,
        spanX: Int,
        spanY: Int,
        columnCount: Int,
        rowCount: Int,
    ): Pair<Int, Int> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        items.forEach { item ->
            for (dx in 0 until item.spanX) {
                for (dy in 0 until item.spanY) {
                    occupied.add((item.col + dx) to (item.row + dy))
                }
            }
        }

        fun fitsAt(col: Int, row: Int): Boolean {
            if (col < 0 || row < 0) return false
            if (col + spanX > columnCount || row + spanY > rowCount) return false
            for (dx in 0 until spanX) {
                for (dy in 0 until spanY) {
                    if ((col + dx) to (row + dy) in occupied) return false
                }
            }
            return true
        }

        if (fitsAt(requestedCol, requestedRow)) return requestedCol to requestedRow
        for (row in 0 until rowCount) {
            for (col in 0 until columnCount) {
                if (fitsAt(col, row)) return col to row
            }
        }
        return requestedCol.coerceAtLeast(0) to requestedRow.coerceAtLeast(0)
    }

    /**
     * Converts one of AppWidgetProviderInfo's minimum dimensions (px) to a whole number of
     * grid cells, rounding up so the widget is never given less room than it asked for - same
     * conversion PinItemActivity does for externally pinned widgets.
     */
    private fun spanForMinDimension(minDimensionPx: Int): Int {
        if (minDimensionPx <= 0) return 1
        val minDimensionDp = minDimensionPx / resources.displayMetrics.density
        return ceil(minDimensionDp / Constants.Grid.CELL_SIZE_DP).toInt().coerceAtLeast(1)
    }

    /**
     * Column/row capacity of the visible grid. Read straight off the current page's HomeGridView
     * when it has been measured (the authoritative numbers); before its first layout pass it
     * reports 0, so fall back to Prefs' screen-size prediction, which subtracts
     * item_home_page.xml's margins around the grid the same way.
     */
    private fun gridGeometry(): Pair<Int, Int> {
        val gridView = currentHomeGridView()
        val columnCount = gridView?.columnCount()?.takeIf { it > 0 } ?: prefs.defaultColumnCount()
        val rowCount = gridView?.rowCount()?.takeIf { it > 0 } ?: prefs.defaultRowCount()
        return columnCount to rowCount
    }

    /** The HomeGridView of the page currently shown by the pager, if it is attached and bound. */
    private fun currentHomeGridView(): HomeGridView? {
        val recyclerView = binding.homePager.getChildAt(0) as? RecyclerView ?: return null
        val holder = recyclerView
            .findViewHolderForAdapterPosition(binding.homePager.currentItem) as? HomePagerAdapter.PageViewHolder
        return holder?.binding?.homeGridView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Side padding around a dialog's text input, matching PagesSettingsFragment's. */
        private const val RENAME_DIALOG_PADDING_DP = 20

        // Range of the App List settings dialog's slot-count stepper. One slot is the smallest
        // thing still worth calling a list; eight matches the launcher's long-standing home-app
        // count (Prefs' appUser1..8 storage shape) and keeps a full-width list inside one screen.
        private const val MIN_APP_LIST_SLOT_COUNT = 1
        private const val MAX_APP_LIST_SLOT_COUNT = 8
    }
}