package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isPrivateSpaceProfile
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import app.olauncher.listener.OnSwipeTouchListener
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    // お気に入りアダプター
    private lateinit var niagaraAdapter: NiagaraHomeAdapter
    private var currentHomeItems: List<NiagaraHomeAdapter.HomeAppItem> = emptyList()

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var scrollToLetter = ""
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false
    private val indexLabels: MutableList<String> = mutableListOf()
    private val indexPositions: MutableMap<String, Int> = mutableMapOf()
    private var isIndexDragging = false
    private var lastWaveCenterIndex = -1

    private val viewModel2: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
            scrollToLetter = it.getString(Constants.Key.SCROLL_TO_LETTER, "") ?: ""
        }

        initViews()
        initFavorites()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
        initIndex()
        initSwipeTouchListener()
        applyIndexSide()
        populateDateTime()

        if (prefs.firstSettingsOpen) {
            // firstRunTips was removed; no tip shown
        }
    }

    override fun onResume() {
        super.onResume()
        populateDateTime()
        populateFavorites()
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    // ── 日時表示 ──

    private fun populateDateTime() {
        binding.dateTimeLayout?.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock?.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date?.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date?.text = dateText.replace(".,", ",")
    }

    // ── お気に入りアプリ ──

    private fun initFavorites() {
        niagaraAdapter = NiagaraHomeAdapter(
            context = requireContext(),
            onAppClick = { index ->
                val slot = index + 1
                favoriteAppClicked(slot)
            },
            onAppLongClick = { index ->
                val slot = index + 1
                showAppListForSlot(slot)
            }
        )
        binding.favoriteRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        binding.favoriteRecyclerView?.adapter = niagaraAdapter
        binding.favoriteRecyclerView?.setHasFixedSize(true)
    }

    private fun populateFavorites() {
        val homeAppsNum = prefs.homeAppsNum
        val items = mutableListOf<NiagaraHomeAdapter.HomeAppItem>()

        for (i in 1..homeAppsNum) {
            val appName = prefs.getAppName(i)
            val pkg = prefs.getAppPackage(i)
            if (appName.isBlank() || pkg.isBlank()) {
                items.add(
                    NiagaraHomeAdapter.HomeAppItem(
                        displayName = "",
                        packageName = "",
                        userString = "",
                        activityClassName = null,
                        slotIndex = i,
                    )
                )
            } else {
                items.add(
                    NiagaraHomeAdapter.HomeAppItem(
                        displayName = appName,
                        packageName = pkg,
                        userString = prefs.getAppUser(i),
                        activityClassName = prefs.getAppActivityClassName(i),
                        slotIndex = i,
                        isShortcut = prefs.getIsShortcut(i),
                        shortcutId = prefs.getShortcutId(i),
                    )
                )
            }
        }

        currentHomeItems = items
        niagaraAdapter.appItems = items
    }

    private fun favoriteAppClicked(slot: Int) {
        val appName = prefs.getAppName(slot)
        val packageName = prefs.getAppPackage(slot)
        if (appName.isEmpty() || packageName.isEmpty()) {
            requireContext().showToast(getString(R.string.long_press_to_select_app))
            return
        }

        val isShortcut = prefs.getIsShortcut(slot)
        val shortcutId = prefs.getShortcutId(slot)

        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            viewModel.selectedApp(
                app.olauncher.data.AppModel.PinnedShortcut(
                    shortcutId = shortcutId,
                    appLabel = appName,
                    user = getUserHandleFromString(requireContext(), prefs.getAppUser(slot)),
                    key = null,
                    appPackage = packageName,
                    isNew = false,
                ),
                Constants.FLAG_LAUNCH_APP
            )
        } else {
            viewModel.selectedApp(
                app.olauncher.data.AppModel.App(
                    appLabel = appName,
                    key = null,
                    appPackage = packageName,
                    activityClassName = prefs.getAppActivityClassName(slot),
                    isNew = false,
                    user = getUserHandleFromString(requireContext(), prefs.getAppUser(slot))
                ),
                Constants.FLAG_LAUNCH_APP
            )
        }
    }

    private fun showAppListForSlot(slot: Int) {
        val flag = when (slot) {
            1 -> Constants.FLAG_SET_HOME_APP_1
            2 -> Constants.FLAG_SET_HOME_APP_2
            3 -> Constants.FLAG_SET_HOME_APP_3
            4 -> Constants.FLAG_SET_HOME_APP_4
            5 -> Constants.FLAG_SET_HOME_APP_5
            6 -> Constants.FLAG_SET_HOME_APP_6
            7 -> Constants.FLAG_SET_HOME_APP_7
            8 -> Constants.FLAG_SET_HOME_APP_8
            else -> return
        }
        val hasExisting = prefs.getAppName(slot).isNotEmpty()
        showAppList(flag, hasExisting, true)
    }

    // ── ジェスチャー ──

    private fun initSwipeTouchListener() {
        binding.scrollRoot?.setOnTouchListener(getSwipeGestureListener(requireContext()))
    }

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeRight() {
                super.onSwipeRight()
                openSettings()
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSettings()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                // 一番下までスクロール
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                openSettings()
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    lockPhone()
                else
                    lockPhone()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    private fun openSettings() {
        try {
            findNavController().navigate(R.id.action_appListFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
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
        try {
            deviceManager.lockNow()
        } catch (e: SecurityException) {
            requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
            findNavController().navigate(R.id.action_appListFragment_to_settingsFragment)
        } catch (e: Exception) {
            requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
            prefs.lockModeOn = false
        }
    }

    // ── インデックス左右切替 ──

    private fun applyIndexSide() {
        val indexSide = prefs.indexSide
        val appIndex = binding.appIndex ?: return
        val recyclerView = binding.recyclerView

        val lp = appIndex.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = if (indexSide == "left") {
            android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        } else {
            android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }
        lp.marginStart = 0
        lp.marginEnd = 0
        appIndex.layoutParams = lp

        if (indexSide == "left") {
            recyclerView.setPaddingRelative(24, 0, 0, 0)
        } else {
            recyclerView.setPaddingRelative(0, 0, 24, 0)
        }
    }

    // ── 既存のドロワー機能 ──

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.filter.filter(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    updateIndexVisibility(newText)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { appModel ->
                viewModel.selectedApp(appModel, flag)
                if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                    findNavController().popBackStack(R.id.appListFragment, false)
                else
                    findNavController().popBackStack()
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.appListFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.PrivateSpaceHeader -> {}
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        if (isPrivateSpaceProfile(requireContext(), appModel.user)) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage)
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.appListFragment, false)
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.setHasFixedSize(true)
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateFavorites()
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                    updateAppIndex(it)
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
            }
        })
        viewModel.showRecentApps.observe(viewLifecycleOwner) {
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = apps.toMutableList()

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        adapter.setAppList(combined)
        updateAppIndex(combined)
        adapter.filter.filter(binding.search.query)

        if (scrollToLetter.isNotBlank()) {
            binding.recyclerView.post {
                scrollToPositionForLetter(scrollToLetter)
            }
        }
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            findNavController().popBackStack()
        }

        binding.date?.setOnClickListener {
            openCalendarApp()
        }
        binding.clock?.setOnClickListener {
            openClockApp()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                    }
                }
            }
        }
    }

    // ── アルファベットインデックス ──

    private fun initIndex() {
        val appIndex = binding.appIndex ?: return
        appIndex.setOnTouchListener { _, event ->
            handleIndexTouch(event)
        }
    }

    private fun handleIndexTouch(event: MotionEvent): Boolean {
        val appIndex = binding.appIndex ?: return false
        if (indexLabels.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isIndexDragging = true
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                scrollToIndexLabel(event, appIndex)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isIndexDragging) return false
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                scrollToIndexLabel(event, appIndex)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isIndexDragging) return false
                isIndexDragging = false
                hideLetterPopup()
                resetWaveEffect(appIndex)
                return true
            }
        }
        return false
    }

    private fun getIndexLabelAt(event: MotionEvent, appIndex: LinearLayout): String {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val y = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val index = ((y / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        return indexLabels[index]
    }

    private fun applyWaveEffect(event: MotionEvent, appIndex: LinearLayout) {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val touchY = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val centerIndex = ((touchY / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        if (centerIndex == lastWaveCenterIndex) return
        lastWaveCenterIndex = centerIndex

        val count = appIndex.childCount.coerceAtMost(indexLabels.size)
        for (i in 0 until count) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            val dist = Math.abs(i - centerIndex)
            val scale = when {
                dist == 0 -> 2.2f
                dist == 1 -> 1.6f
                dist == 2 -> 1.3f
                dist == 3 -> 1.1f
                else -> 1.0f
            }
            val alpha = when {
                dist == 0 -> 1.0f
                dist == 1 -> 0.85f
                dist == 2 -> 0.65f
                dist == 3 -> 0.50f
                else -> 0.40f
            }
            if (child.scaleX != scale) {
                child.scaleX = scale
                child.scaleY = scale
            }
            if (child.alpha != alpha) {
                child.alpha = alpha
            }
        }
    }

    private fun resetWaveEffect(appIndex: LinearLayout) {
        lastWaveCenterIndex = -1
        for (i in 0 until appIndex.childCount) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            child.animate().cancel()
            child.scaleX = 1f
            child.scaleY = 1f
            child.alpha = 0.6f
        }
    }

    private fun scrollToIndexLabel(event: MotionEvent, appIndex: LinearLayout) {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val y = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val index = ((y / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        val label = indexLabels[index]
        indexPositions[label]?.let { position ->
            binding.recyclerView.stopScroll()
            linearLayoutManager.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun scrollToPositionForLetter(letter: String) {
        val target = letter.uppercase()
        var bestPosition = -1
        for ((label, position) in indexPositions) {
            if (label.equals(target, ignoreCase = true)) {
                bestPosition = position
                break
            }
        }
        if (bestPosition < 0) {
            for ((label, position) in indexPositions) {
                if (label.startsWith(target.firstOrNull()?.toString() ?: "", ignoreCase = true)) {
                    bestPosition = position
                    break
                }
            }
        }
        if (bestPosition >= 0) {
            linearLayoutManager.scrollToPositionWithOffset(bestPosition, 0)
        }
    }

    // ── レターポップアップ ──

    private fun showLetterPopup(letter: String) {
        val popup = binding.letterPopup ?: return
        popup.text = letter
        popup.visibility = View.VISIBLE
        popup.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(80)
            .start()
    }

    private fun hideLetterPopup() {
        val popup = binding.letterPopup ?: return
        popup.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction { popup.visibility = View.GONE }
            .start()
    }

    // ── インデックス管理 ──

    private fun updateAppIndex(apps: List<AppModel>) {
        val labels = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app ->
            if (app is AppModel.PrivateSpaceHeader) return@forEachIndexed
            val label = app.appLabel.trim()
            if (label.isBlank()) return@forEachIndexed
            val key = getIndexKey(label)
            if (!labels.containsKey(key)) labels[key] = index
        }
        indexLabels.clear()
        indexLabels.addAll(labels.keys)
        indexPositions.clear()
        indexPositions.putAll(labels)
        renderIndexLabels()
        updateIndexVisibility(binding.search.query)
    }

    private fun renderIndexLabels() {
        val appIndex = binding.appIndex ?: return
        appIndex.removeAllViews()
        if (indexLabels.isEmpty()) return
        val context = requireContext()
        indexLabels.forEach { label ->
            val textView = TextView(context).apply {
                text = label
                TextViewCompat.setTextAppearance(this, R.style.TextSmall)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
                pivotX = 0f
                pivotY = 0f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                alpha = 0.6f
                tag = label
            }
            appIndex.addView(textView)
        }
    }

    private fun updateIndexVisibility(query: CharSequence?) {
        binding.appIndex?.isVisible = indexLabels.isNotEmpty()
    }

    private fun getIndexKey(label: String): String {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()
        val firstChar = normalized.firstOrNull()?.uppercaseChar() ?: return "#"
        return when {
            firstChar.isLetter() -> firstChar.toString()
            firstChar.isDigit() -> "0-9"
            else -> "#"
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    // ── 時計・カレンダー ──

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            viewModel.selectedApp(
                app.olauncher.data.AppModel.App(
                    appLabel = "Clock",
                    key = null,
                    appPackage = prefs.clockAppPackage,
                    activityClassName = prefs.clockAppClassName,
                    isNew = false,
                    user = getUserHandleFromString(requireContext(), prefs.clockAppUser)
                ),
                Constants.FLAG_LAUNCH_APP
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            viewModel.selectedApp(
                app.olauncher.data.AppModel.App(
                    appLabel = "Calendar",
                    key = null,
                    appPackage = prefs.calendarAppPackage,
                    activityClassName = prefs.calendarAppClassName,
                    isNew = false,
                    user = getUserHandleFromString(requireContext(), prefs.calendarAppUser)
                ),
                Constants.FLAG_LAUNCH_APP
            )
    }

    // ── ステータスバー ──

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
    }

    // ── その他 ──

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_appListFragment_to_settingsFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
