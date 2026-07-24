package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.MediaPlaybackRepository
import app.olauncher.helper.MusicState
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showToast
import app.olauncher.helper.WidgetHostManager
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.SwipeDismissTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var widgetPagerAdapter: WidgetPagerAdapter
    private var widgetPageCallback: ViewPager2.OnPageChangeCallback? = null
    private var mediaRepository: MediaPlaybackRepository? = null

    private data class RenderedMusic(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val artworkId: Int?,
    )

    // Null whenever the music views are fresh and nothing has been drawn into them yet
    private var renderedMusic: RenderedMusic? = null
    private var musicLayoutKey: Int? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var homeAppViews: List<TextView> = emptyList()
    private var headerBottom = UNMEASURED

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
        widgetHostManager = WidgetHostManager.get(requireContext())
        mediaRepository = MediaPlaybackRepository(requireContext())
        homeAppViews = with(binding) {
            listOf(homeApp1, homeApp2, homeApp3, homeApp4, homeApp5, homeApp6, homeApp7, homeApp8)
        }
        initWidgetPager()
        initMusicWidget()
        initHomeAppsBounds()

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        mediaRepository?.refresh()
    }

    override fun onStart() {
        super.onStart()
        if (prefs.showMusicWidget) mediaRepository?.start()
    }

    override fun onStop() {
        mediaRepository?.stop()
        super.onStop()
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

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
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
                if (viewModel.isOlauncherDefault.value != true) {
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
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
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
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.refreshWidgets.observe(viewLifecycleOwner) {
            populateWidgets()
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        binding.homeApp1.setOnClickListener(this)
        binding.homeApp2.setOnClickListener(this)
        binding.homeApp3.setOnClickListener(this)
        binding.homeApp4.setOnClickListener(this)
        binding.homeApp5.setOnClickListener(this)
        binding.homeApp6.setOnClickListener(this)
        binding.homeApp7.setOnClickListener(this)
        binding.homeApp8.setOnClickListener(this)
        binding.homeApp1.setOnLongClickListener(this)
        binding.homeApp2.setOnLongClickListener(this)
        binding.homeApp3.setOnLongClickListener(this)
        binding.homeApp4.setOnLongClickListener(this)
        binding.homeApp5.setOnLongClickListener(this)
        binding.homeApp6.setOnLongClickListener(this)
        binding.homeApp7.setOnLongClickListener(this)
        binding.homeApp8.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.musicWidget.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
        applyMusicWidgetLayout()
    }

    private fun initHomeAppsBounds() {
        headerBottom = UNMEASURED
        // The header grows and shrinks with the date and music widget, so follow its size
        binding.headerLayout.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            if (bottom == headerBottom) return@addOnLayoutChangeListener
            headerBottom = bottom
            updateHomeAppsBounds()
        }
        binding.homeAppsLayout.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateHomeAppsBounds()
        }
    }

    /**
     * Keeps the home apps inside the space left between the header (clock, date and music widget)
     * and the widget area, spreading them evenly so the gaps shrink as apps are added.
     */
    private fun updateHomeAppsBounds() {
        val layout = binding.homeAppsLayout
        val available = layout.height
        if (available == 0 || headerBottom == UNMEASURED) return

        val apps = homeAppViews.filter { it.isVisible }
        val textHeight = apps.maxOfOrNull { app ->
            (app.height - app.paddingTop - app.paddingBottom).coerceAtLeast(app.lineHeight)
        } ?: 0

        val bottomInset = homeAppsBottomInset()
        val minAppsHeight = apps.size * (textHeight + 2 * MIN_APP_PADDING_DP.dpToPx())
        val topInset = (headerBottom + HOME_APPS_GAP_DP.dpToPx())
            .coerceAtMost((available - bottomInset - minAppsHeight).coerceAtLeast(0))

        if (layout.paddingTop != topInset || layout.paddingBottom != bottomInset) {
            layout.setPadding(layout.paddingLeft, topInset, layout.paddingRight, bottomInset)
        }
        if (apps.isEmpty()) return

        val slot = (available - topInset - bottomInset) / apps.size
        val padding = ((slot - textHeight) / 2).coerceIn(
            MIN_APP_PADDING_DP.dpToPx(),
            MAX_APP_PADDING_DP.dpToPx()
        )
        apps.forEach { app ->
            if (app.paddingTop != padding) app.updatePadding(top = padding, bottom = padding)
        }
    }

    private fun homeAppsBottomInset(): Int {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            resources.getDimensionPixelSize(R.dimen.home_app_padding_vertical)
        } else {
            28.dpToPx()
        }
        if (!binding.widgetArea.isVisible) return base
        return base + prefs.widgetAreaHeight.dpToPx() + HOME_APPS_GAP_DP.dpToPx()
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val dateOnly = prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY
        val marginTop = when {
            isLandscape && dateOnly -> prefs.headerTopMargin - 12
            isLandscape -> prefs.headerTopMargin + 8
            dateOnly -> prefs.headerTopMargin - 11
            else -> prefs.headerTopMargin + 16
        }.coerceAtLeast(0).dpToPx()
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        applyHeaderPosition()
        applyMusicWidgetLayout()
        populateDateTime()
        populateWidgets()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        populateHomeApps()
        updateHomeAppsBounds()
    }

    /**
     * Puts the music widget under the clock, or beside it when home alignment is left or right.
     * Centered homes always keep music below. Left alignment puts music to the right of the clock;
     * right alignment mirrors that (music on the left). Beside mode stacks the controls under the
     * title so the track text can use the full width; below mode keeps the classic single row.
     */
    private fun applyMusicWidgetLayout() {
        val alignment = prefs.homeAlignment
        val wantBeside = prefs.musicWidgetPosition == Constants.MusicPosition.BESIDE_CLOCK
        val sideBySide = wantBeside && alignment != Gravity.CENTER
        val musicOnStart = alignment == Gravity.END // mirrored: clock on the right → music on the left
        val layoutKey = when {
            !sideBySide -> LAYOUT_BELOW
            musicOnStart -> LAYOUT_BESIDE_START
            else -> LAYOUT_BESIDE_END
        }
        if (musicLayoutKey == layoutKey) return
        musicLayoutKey = layoutKey
        renderedMusic = null

        binding.headerLayout.orientation =
            if (sideBySide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        // Child order decides which side of the clock the music sits on
        orderHeaderChildren(musicFirst = sideBySide && musicOnStart)
        arrangeMusicContent(sideBySide)

        binding.dateTimeLayout.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (sideBySide) {
                LinearLayout.LayoutParams.WRAP_CONTENT
            } else {
                LinearLayout.LayoutParams.MATCH_PARENT
            }
            weight = 0f
            gravity = if (sideBySide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            marginStart = 0
            marginEnd = 0
            topMargin = 0
        }
        binding.musicWidget.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (sideBySide) 0 else LinearLayout.LayoutParams.MATCH_PARENT
            weight = if (sideBySide) 1f else 0f
            gravity = if (sideBySide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            val sideGap = if (sideBySide) MUSIC_SIDE_MARGIN_DP.dpToPx() else 0
            marginStart = if (sideBySide && !musicOnStart) sideGap else 0
            marginEnd = if (sideBySide && musicOnStart) sideGap else 0
            topMargin = if (sideBySide) 0 else MUSIC_BELOW_MARGIN_DP.dpToPx()
        }

        binding.musicArtwork.updateLayoutParams<LinearLayout.LayoutParams> {
            if (sideBySide) {
                // Tall strip matching the title+controls column; centerCrop keeps the middle of the art
                width = BESIDE_ARTWORK_WIDTH_DP.dpToPx()
                height = LinearLayout.LayoutParams.MATCH_PARENT
            } else {
                val size = FULL_ARTWORK_DP.dpToPx()
                width = size
                height = size
            }
        }
        binding.musicContent.gravity =
            if (sideBySide) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
        binding.musicWidget.gravity = if (sideBySide) Gravity.START else alignment
    }

    private fun arrangeMusicContent(sideBySide: Boolean) {
        val content = binding.musicContent
        val details = binding.musicDetails
        val info = binding.musicInfo
        val controls = binding.musicControls

        // Detach so we can rebuild either a single row or a stacked column
        (info.parent as? ViewGroup)?.removeView(info)
        (controls.parent as? ViewGroup)?.removeView(controls)
        (details.parent as? ViewGroup)?.removeView(details)

        if (sideBySide) {
            details.addView(
                info,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            details.addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dpToPx() }
            )
            content.addView(
                details,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dpToPx()
                }
            )
        } else {
            content.addView(
                info,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dpToPx()
                }
            )
            content.addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun orderHeaderChildren(musicFirst: Boolean) {
        val header = binding.headerLayout
        val first = if (musicFirst) binding.musicWidget else binding.dateTimeLayout
        val second = if (musicFirst) binding.dateTimeLayout else binding.musicWidget
        if (header.indexOfChild(first) == 0 && header.indexOfChild(second) == 1) return
        header.removeView(binding.dateTimeLayout)
        header.removeView(binding.musicWidget)
        header.addView(first)
        header.addView(second)
    }

    private fun applyHeaderPosition() {
        val topMargin = prefs.headerTopMargin.dpToPx()
        val params = binding.headerLayout.layoutParams as FrameLayout.LayoutParams
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin
            binding.headerLayout.layoutParams = params
        }
    }

    private fun populateHomeApps() {
        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

        binding.homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp6, prefs.appName6, prefs.appPackage6, prefs.appUser6, prefs.isShortcut6, prefs.shortcutId6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        binding.homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp7, prefs.appName7, prefs.appPackage7, prefs.appUser7, prefs.isShortcut7, prefs.shortcutId7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        binding.homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp8, prefs.appName8, prefs.appPackage8, prefs.appUser8, prefs.isShortcut8, prefs.shortcutId8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
    }

    private fun initWidgetPager() {
        widgetPagerAdapter = WidgetPagerAdapter(widgetHostManager) { hostView ->
            val width = binding.widgetPager.width
            val height = binding.widgetPager.height - 12.dpToPx()
            if (width > 0 && height > 0) widgetHostManager.updateSize(hostView, width, height)
        }
        binding.widgetPager.apply {
            adapter = widgetPagerAdapter
            offscreenPageLimit = 1
        }
        widgetPageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                prefs.widgetCurrentPage = position
                updateWidgetPageIndicator(position, widgetPagerAdapter.itemCount)
            }
        }.also(binding.widgetPager::registerOnPageChangeCallback)
    }

    private fun initMusicWidget() {
        // The views are recreated on every navigation, so the diff cache has to start empty too
        renderedMusic = null
        musicLayoutKey = null

        val artRadius = 8.dpToPx().toFloat()
        binding.musicArtwork.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, artRadius)
            }
        }
        binding.musicArtwork.clipToOutline = true

        binding.musicPermissionPrompt.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        binding.musicArtwork.setOnClickListener { mediaRepository?.openActiveMediaApp() }
        binding.musicInfo.setOnClickListener { mediaRepository?.openActiveMediaApp() }
        binding.musicPrevious.setOnClickListener { mediaRepository?.skipPrevious() }
        binding.musicPlayPause.setOnClickListener { mediaRepository?.togglePlayPause() }
        binding.musicNext.setOnClickListener { mediaRepository?.skipNext() }

        val swipeToDismiss = SwipeDismissTouchListener(binding.musicContent) {
            binding.musicWidget.isVisible = false
            mediaRepository?.dismissCurrent()
        }
        binding.musicContent.setOnTouchListener(swipeToDismiss)
        binding.musicArtwork.setOnTouchListener(swipeToDismiss)
        binding.musicInfo.setOnTouchListener(swipeToDismiss)
        binding.musicDetails.setOnTouchListener(swipeToDismiss)
        binding.musicControls.setOnTouchListener(swipeToDismiss)
        binding.musicPrevious.setOnTouchListener(swipeToDismiss)
        binding.musicPlayPause.setOnTouchListener(swipeToDismiss)
        binding.musicNext.setOnTouchListener(swipeToDismiss)

        val repository = mediaRepository ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect(::bindMusicState)
            }
        }
    }

    private fun bindMusicState(state: MusicState) {
        if (!prefs.showMusicWidget) {
            binding.musicWidget.isVisible = false
            return
        }

        if (!state.hasPermission) {
            binding.musicWidget.isVisible = true
            binding.musicPermissionPrompt.isVisible = true
            binding.musicContent.isVisible = false
            return
        }

        if (!state.hasSession) {
            binding.musicWidget.isVisible = false
            return
        }

        binding.musicWidget.isVisible = true
        binding.musicPermissionPrompt.isVisible = false
        binding.musicContent.isVisible = true

        val title = state.title.ifBlank { getString(R.string.music_unknown_title) }
        val artist = state.artist.ifBlank { getString(R.string.music_unknown_artist) }
        val artworkId = state.artwork?.let { System.identityHashCode(it) }
        val rendered = renderedMusic

        if (rendered == null || title != rendered.title) {
            binding.musicTitle.text = title
            binding.musicTitle.isSelected = true
        }

        if (rendered == null || artist != rendered.artist) {
            binding.musicArtist.text = artist
            binding.musicArtist.isSelected = true
        }

        if (rendered == null || state.isPlaying != rendered.isPlaying) {
            binding.musicPlayPause.setImageResource(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.musicPlayPause.contentDescription = getString(
                if (state.isPlaying) R.string.music_pause else R.string.music_play
            )
        }

        if (rendered == null || artworkId != rendered.artworkId) {
            if (state.artwork != null) {
                binding.musicArtwork.setImageBitmap(state.artwork)
                binding.musicArtwork.setPadding(0)
            } else {
                binding.musicArtwork.setImageResource(R.drawable.ic_music_note)
                binding.musicArtwork.setPadding(10.dpToPx())
            }
        }

        renderedMusic = RenderedMusic(title, artist, state.isPlaying, artworkId)
    }

    private fun populateWidgets() {
        if (!::widgetPagerAdapter.isInitialized) return
        val widgetIds = widgetHostManager.pruneStaleWidgets(prefs)
        binding.widgetArea.isVisible = widgetIds.isNotEmpty()
        if (widgetIds.isEmpty()) {
            widgetPagerAdapter.submitList(emptyList())
            binding.widgetPageIndicator.removeAllViews()
            updateHomeAppsBounds()
            return
        }

        val heightPx = prefs.widgetAreaHeight.dpToPx()
        binding.widgetArea.layoutParams = binding.widgetArea.layoutParams.apply { height = heightPx }
        updateHomeAppsBounds()
        widgetPagerAdapter.submitList(widgetIds)
        val page = prefs.widgetCurrentPage.coerceIn(0, widgetIds.lastIndex)
        binding.widgetPager.setCurrentItem(page, false)
        updateWidgetPageIndicator(page, widgetIds.size)
        binding.widgetPager.post {
            widgetPagerAdapter.updateAllSizes(
                binding.widgetPager.width,
                (binding.widgetPager.height - 12.dpToPx()).coerceAtLeast(1)
            )
        }
    }

    private fun updateWidgetPageIndicator(selected: Int, count: Int) {
        binding.widgetPageIndicator.removeAllViews()
        if (count <= 1) return
        repeat(count) { index ->
            val dot = View(requireContext()).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        requireContext().getColorFromAttr(
                            if (index == selected) R.attr.primaryColor else R.attr.primaryColorTrans50
                        )
                    )
                }
            }
            binding.widgetPageIndicator.addView(
                dot,
                android.widget.LinearLayout.LayoutParams(5.dpToPx(), 5.dpToPx()).apply {
                    marginStart = 3.dpToPx()
                    marginEnd = 3.dpToPx()
                }
            )
        }
    }

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    return true
                }
                textView.text = ""
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
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

    private fun homeAppClicked(location: Int) {
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
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

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
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

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
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

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
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

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        widgetPageCallback?.let(binding.widgetPager::unregisterOnPageChangeCallback)
        widgetPageCallback = null
        if (::widgetPagerAdapter.isInitialized) widgetPagerAdapter.clear()
        mediaRepository?.stop()
        mediaRepository = null
        homeAppViews = emptyList()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val UNMEASURED = -1
        const val HOME_APPS_GAP_DP = -5
        const val MIN_APP_PADDING_DP = 2
        const val MAX_APP_PADDING_DP = 24
        const val MUSIC_BELOW_MARGIN_DP = 12
        const val MUSIC_SIDE_MARGIN_DP = 16
        const val FULL_ARTWORK_DP = 48
        const val BESIDE_ARTWORK_WIDTH_DP = 40
        const val LAYOUT_BELOW = 0
        const val LAYOUT_BESIDE_END = 1   // music to the right of the clock
        const val LAYOUT_BESIDE_START = 2 // music to the left of the clock (mirrored)
    }
}