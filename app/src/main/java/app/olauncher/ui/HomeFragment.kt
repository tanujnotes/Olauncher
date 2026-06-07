package app.olauncher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.applyFontFamily
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openSearch
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import eightbitlab.com.blurview.RenderScriptBlur
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var niagaraAdapter: NiagaraHomeAdapter

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var currentHomeItems: List<NiagaraHomeAdapter.HomeAppItem> = emptyList()

    // ── 波アニメーション用 ──
    private val indexLabels = listOf(
        "A","B","C","D","E","F","G","H","I","J","K","L","M",
        "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
        "#"
    )
    private var isIndexDragging = false

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

        initNiagaraRecyclerView()
        initObservers()
        initSwipeTouchListener()
        initClickListeners()
        initAlphabetIndex()
        setupBlurView()
        // 選択されたフォントを適用
        (binding.root as? ViewGroup)?.applyFontFamily(prefs.fontFamily)
    }

    override fun onResume() {
        super.onResume()
        populateNiagaraHome()
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    // ── Niagara 風 アプリリスト ──────────────────────────────

    private fun initNiagaraRecyclerView() {
        niagaraAdapter = NiagaraHomeAdapter(
            context = requireContext(),
            onAppClick = { index ->
                val slot = index + 1
                homeAppClicked(slot)
            },
            onAppLongClick = { index ->
                val slot = index + 1
                showAppListForSlot(slot)
            }
        )

        binding.niagaraRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        binding.niagaraRecyclerView?.adapter = niagaraAdapter
    }

    private fun populateNiagaraHome() {
        populateDateTime()

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

    // ── Niagara風 アルファベットインデックス（波アニメーション＋ドラッグスクロール） ──

    private fun initAlphabetIndex() {
        val appIndex = binding.appIndex ?: return
        appIndex.removeAllViews()
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

        // メインのタッチハンドリング
        appIndex.setOnTouchListener { _, event ->
            handleIndexTouch(event)
        }

        binding.appIndex?.visibility = View.VISIBLE
    }

    /**
     * Niagara式 アルファベットインデックスのタッチ処理
     * - ドラッグ中は波アニメーション＋レターポップアップ
     * - タップアップでアプリドロワーを開く（選択中の文字へスクロール）
     */
    private fun handleIndexTouch(event: MotionEvent): Boolean {
        val appIndex = binding.appIndex ?: return false
        if (indexLabels.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isIndexDragging = true
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isIndexDragging) return false
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isIndexDragging) return false
                isIndexDragging = false
                val label = getIndexLabelAt(event, appIndex)
                hideLetterPopup()
                resetWaveEffect(appIndex)
                // 選択された文字でアプリドロワーを開く
                openAppDrawerWithLetter(label)
                return true
            }
        }
        return false
    }

    /**
     * タッチ位置のアルファベットを取得
     */
    private fun getIndexLabelAt(event: MotionEvent, appIndex: LinearLayout): String {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val y = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val index = ((y / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        return indexLabels[index]
    }

    /**
     * Niagara式 波アニメーション
     * 指の近くの文字を拡大し、遠くの文字は小さく
     */
    private fun applyWaveEffect(event: MotionEvent, appIndex: LinearLayout) {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val touchY = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val touchProgress = touchY / viewHeight
        val centerIndex = (touchProgress * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)

        for (i in 0 until appIndex.childCount) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            val distance = Math.abs(i - centerIndex).toFloat()
            val scale = when {
                distance <= 1f -> 1.0f + (1f - distance / 1f) * 1.2f  // 最大2.2x
                distance <= 3f -> 1.0f + (1f - (distance - 1f) / 2f) * 0.4f  // 1.0〜1.4x
                else -> 1.0f
            }.coerceIn(0.8f, 2.5f)
            val alpha = when {
                distance <= 1f -> 1.0f
                distance <= 3f -> 1.0f - (distance - 1f) * 0.2f
                else -> 0.4f
            }.coerceIn(0.3f, 1.0f)

            child.scaleX = scale
            child.scaleY = scale
            child.alpha = alpha
        }
    }

    /**
     * 波アニメーションをリセット
     */
    private fun resetWaveEffect(appIndex: LinearLayout) {
        for (i in 0 until appIndex.childCount) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            child.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0.6f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    /**
     * レターポップアップを表示（Niagara: 中央に大きな文字）
     */
    private fun showLetterPopup(letter: String) {
        val popup = binding.letterPopup ?: return
        popup.text = letter
        popup.visibility = View.VISIBLE
        popup.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * レターポップアップを非表示（フェードアウト）
     */
    private fun hideLetterPopup() {
        val popup = binding.letterPopup ?: return
        popup.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { popup.visibility = View.GONE }
            .start()
    }

    /**
     * 選択された文字でアプリドロワーを開く
     */
    private fun openAppDrawerWithLetter(letter: String) {
        viewModel.getAppList()
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to Constants.FLAG_LAUNCH_APP,
                    Constants.Key.RENAME to false,
                    "scrollToLetter" to letter
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── アプリ起動 ──────────────────────────────────────────

    private fun homeAppClicked(slot: Int) {
        val appName = prefs.getAppName(slot)
        val packageName = prefs.getAppPackage(slot)
        if (appName.isEmpty() || packageName.isEmpty()) {
            showLongPressToast()
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

    // ── 日時表示 ────────────────────────────────────────────

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

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

    // ── オブザーバー ──────────────────────────────────────────

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateNiagaraHome()
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.showRecentApps.observe(viewLifecycleOwner) {
            binding.recents.performClick()
        }
    }

    // ── ジェスチャー ──────────────────────────────────────────

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
        }
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

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
    }

    // ── スワイプ ──────────────────────────────────────────────

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeRight() {
                super.onSwipeRight()
                showAllApps()
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                showAllApps()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAllApps()
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

    private fun showAllApps() {
        showAppList(Constants.FLAG_LAUNCH_APP)
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

    // ── 補助機能 ──────────────────────────────────────────────

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

    // ── ガラスエフェクト ────────────────────────────────

    private fun setupBlurView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val window = requireActivity().window
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attributes = window.attributes
            attributes.blurBehindRadius = 120
            window.attributes = attributes
            binding.blurView?.setBackgroundColor(0x99000000.toInt())
            binding.blurView?.visibility = View.VISIBLE
        } else {
            binding.blurView?.visibility = View.VISIBLE
            try {
                val decorView = requireActivity().window.decorView
                val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
                val windowBackground = decorView.background
                binding.blurView?.setupWith(rootView, RenderScriptBlur(requireContext()))
                    ?.setFrameClearDrawable(windowBackground)
                    ?.setBlurRadius(25f)
                binding.blurView?.setBackgroundColor(0x99000000.toInt())
            } catch (e: Exception) {
                binding.blurView?.setBackgroundColor(0x99000000.toInt())
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    override fun onDestroyView() {
        super.onDestroyView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val window = requireActivity().window
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            } catch (_: Exception) {}
        }
        _binding = null
    }
}
