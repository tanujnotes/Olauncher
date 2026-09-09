package app.elauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.setPadding
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.elauncher.MainViewModel
import app.elauncher.R
import app.elauncher.data.AppModel
import app.elauncher.data.Constants
import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import app.elauncher.data.Prefs
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

    /** Applied to every page's dateTimeLayout on bind - see setHomeAlignment(). */
    private var homeAlignment: Int = Gravity.START

    /** Latest measured screen-time text, applied to every page's tvScreenTime on bind. */
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
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()
        }
    }

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

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isElauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
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
        // clock/date/tvScreenTime now live inside each page's item_home_page.xml (pages are fully
        // independent per plan.md) - their listeners are wired per-instance in bindPage().
    }

    /**
     * Binds one page's clock/date/screen-time/grid content - called by HomePagerAdapter for every
     * page (occupied or the "at least 1 blank page" fallback). Multiple instances of this exist
     * at once (ViewPager2 pre-renders adjacent pages), all sharing the same view ids, so
     * HomeFragment.onClick/onLongClick's `when (view.id)` dispatch still works unchanged - it
     * doesn't care which page instance triggered it.
     */
    private fun bindPage(pageBinding: ItemHomePageBinding, pageIndex: Int) {
        pageBinding.clock.setOnClickListener(this)
        pageBinding.date.setOnClickListener(this)
        pageBinding.clock.setOnLongClickListener(this)
        pageBinding.date.setOnLongClickListener(this)
        pageBinding.tvScreenTime.setOnClickListener(this)
        pageBinding.tvScreenTime.setOnLongClickListener(this)

        pageBinding.dateTimeLayout.gravity = homeAlignment
        populateDateTime(pageBinding)
        applyScreenTime(pageBinding)
        populateHomeGridFor(pageBinding, pageIndex)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        // homeBottomAlignment/vertical gravity and per-item horizontal gravity used to apply to
        // the homeApp1..8 labels in the old vertically-stacked layout; HomeGridView lays cells out
        // at absolute (col, row) positions and has no gravity concept, so only the date/time
        // header's alignment is still meaningful here. See deviation notes for this step.
        homeAlignment = horizontalGravity
        if (::pagerAdapter.isInitialized) pagerAdapter.notifyDataSetChanged()
    }

    private fun populateDateTime(pageBinding: ItemHomePageBinding) {
        pageBinding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        pageBinding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        pageBinding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        pageBinding.date.text = dateText.replace(".,", ",")
    }

    /** Kicks off the (possibly expensive) screen-time measurement once per refresh, not per page. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun refreshScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return
        viewModel.getTodaysScreenTime()
    }

    /** Applies the latest measured [screenTimeText] to one page - cheap, safe to call per bind. */
    private fun applyScreenTime(pageBinding: ItemHomePageBinding) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (requireContext().appUsagePermissionGranted().not()) return

        pageBinding.tvScreenTime.visibility = View.VISIBLE
        pageBinding.tvScreenTime.text = screenTimeText

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        pageBinding.tvScreenTime.layoutParams = params
        pageBinding.tvScreenTime.setPadding(10.dpToPx())
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
            if (_binding == null) return@post // fragment view may have been destroyed by then
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

        val validItems = page.items.filter(::isGridItemValid)
        if (validItems.size != page.items.size) {
            // An app was uninstalled or a pinned shortcut was removed since the page was last
            // populated - drop the stale item and persist that, mirroring the old behavior of
            // clearing prefs.appNameN/appPackageN when setHomeAppText() found nothing installed.
            val updatedPages = pages.toMutableList()
            updatedPages[pageIndex] = page.copy(items = validItems.toMutableList())
            prefs.pages = updatedPages
        }

        pageBinding.homeGridView.setItems(
            items = validItems,
            touchListenerFor = ::cellTouchListenerFor,
            // Edit mode (Step 19) resolves move/resize inside HomeGridView, which owns no Prefs
            // access, and hands the result back here to be written - deliberately without a
            // notifyItemChanged(), since the grid has already re-laid itself out and a rebind would
            // just drop the user out of edit mode after every single adjustment.
            onItemsChanged = { updatedItems -> persistPageItems(pageIndex, updatedItems) },
            onItemDeleted = ::removeGridItem,
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

    /** Adapted from the old setHomeAppText(): true if [item] still points at something launchable. */
    private fun isGridItemValid(item: GridItem): Boolean {
        if (item.type != GridItemType.APP) return true // widget validity isn't this step's concern

        val appName = item.appName
        if (appName.isNullOrEmpty()) return false
        val packageName = item.appPackage.orEmpty()
        val userString = item.appUser.orEmpty()

        if (item.isShortcut) {
            val userHandle = getUserHandleFromString(requireContext(), userString)
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            return try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                shortcuts?.any { it.id == item.shortcutId } == true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        return isPackageInstalled(requireContext(), packageName, userString)
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

    private fun launchGridItem(item: GridItem) {
        if (item.type != GridItemType.APP) return // widgets aren't launchable
        launchAppOrShortcut(
            appName = item.appName.orEmpty(),
            packageName = item.appPackage.orEmpty(),
            activityClassName = item.appActivityClassName,
            shortcutId = item.shortcutId,
            isShortcut = item.isShortcut,
            userString = item.appUser.orEmpty()
        )
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
        rename: Boolean = false,
        includeHiddenApps: Boolean = false,
        col: Int = -1,
        row: Int = -1,
    ) {
        viewModel.getAppList(includeHiddenApps)
        val args = bundleOf(
            Constants.Key.FLAG to flag,
            Constants.Key.RENAME to rename,
            Constants.Key.COL to col,
            Constants.Key.ROW to row,
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
                    // widget it points at no longer exists.
                    if (isUnavailableWidget(item)) removeGridItem(item)
                    else launchGridItem(item)
                } else {
                    // Tap on an empty cell opens the app drawer in "pick an app for this cell"
                    // mode, keyed off (col, row) instead of the old fixed FLAG_SET_HOME_APP_N
                    // slot flags. includeHiddenApps = true mirrors the old long-press-on-home-app
                    // behavior, which also allowed picking a hidden app for a slot.
                    showAppList(Constants.FLAG_SET_HOME_APP_CELL, includeHiddenApps = true, col = col, row = row)
                }
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
     * Long-press on an empty grid cell: add a widget there, or open Settings. Matches the
     * AlertDialog style used by PagesSettingsFragment's rename/delete dialogs, including its
     * custom-font fix-up (dialog content lives in its own window, outside this fragment's view
     * tree, so BaseFragment's typeface walk never reaches it).
     */
    private fun showEmptyCellOptions(col: Int, row: Int) {
        val options = arrayOf(getString(R.string.add_widget), getString(R.string.settings))
        val dialog = AlertDialog.Builder(requireContext())
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> startWidgetPicker(col, row)
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
     * Converts one of AppWidgetProviderInfo's minimum dimensions (px) to a whole number of 80dp
     * grid cells, rounding up so the widget is never given less room than it asked for - same
     * conversion PinItemActivity does for externally pinned widgets.
     */
    private fun spanForMinDimension(minDimensionPx: Int): Int {
        if (minDimensionPx <= 0) return 1
        val minDimensionDp = minDimensionPx / resources.displayMetrics.density
        return ceil(minDimensionDp / GRID_CELL_SIZE_DP).toInt().coerceAtLeast(1)
    }

    /**
     * Column/row capacity of the visible grid. Read straight off the current page's HomeGridView
     * when it has been measured (the authoritative numbers); before its first layout pass it
     * reports 0, so fall back to the same screen-size arithmetic Prefs.defaultColumnCount() uses,
     * minus item_home_page.xml's margins around the grid.
     */
    private fun gridGeometry(): Pair<Int, Int> {
        val gridView = currentHomeGridView()
        val configuration = resources.configuration
        val columnCount = gridView?.columnCount()?.takeIf { it > 0 }
            ?: maxOf(1, (configuration.screenWidthDp - GRID_HORIZONTAL_MARGIN_DP) / GRID_CELL_SIZE_DP.toInt())
        val rowCount = gridView?.rowCount()?.takeIf { it > 0 }
            ?: maxOf(1, (configuration.screenHeightDp - GRID_VERTICAL_MARGIN_DP) / GRID_CELL_SIZE_DP.toInt())
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
        // Matches HomeGridView's own (private) CELL_SIZE_DP and item_home_page.xml's margins
        // around the grid - only used as a fallback when the grid hasn't been measured yet.
        private const val GRID_CELL_SIZE_DP = 80f
        private const val GRID_HORIZONTAL_MARGIN_DP = 48 // 24dp each side
        private const val GRID_VERTICAL_MARGIN_DP = 160 // 112dp top + 48dp bottom
    }
}