package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.*
import app.olauncher.listener.DeviceAdmin
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateLockSettings()
        populateWallpaperText()
        populateAppThemeText()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateActionHints()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        appsNumSelectLayout.visibility = View.GONE
        dateTimeSelectLayout.visibility = View.GONE
        appThemeSelectLayout.visibility = View.GONE
        if (view.id != R.id.alignmentBottom)
            alignmentSelectLayout.visibility = View.GONE
        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.appInfo -> openAppInfo(requireContext(), android.os.Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            R.id.publicRoadmap -> requireContext().openUrl(Constants.URL_PUBLIC_ROADMAP)
            R.id.toggleLock -> toggleLockMode()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignment -> alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.appThemeText -> appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)

            R.id.about -> {
                prefs.aboutClicked = true
                requireContext().openUrl(Constants.URL_ABOUT_OLAUNCHER)
            }
            R.id.share -> shareApp()
            R.id.rate -> {
                prefs.rateClicked = true
                rateApp()
            }
            R.id.affiliate -> {
                viewModel.showSupportDialog(true)
                findNavController().popBackStack()
            }
            R.id.twitter -> requireContext().openUrl(Constants.URL_TWITTER_TANUJ)
            R.id.instagram -> requireContext().openUrl(Constants.URL_INSTA_OLAUNCHER)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.moreApps -> requireContext().openUrl(Constants.URL_PLAY_STORE_DEV)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
            }
            R.id.dailyWallpaper -> removeWallpaper()
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            }
        }
        return true
    }

    private fun initClickListeners() {
        olauncherHiddenApps.setOnClickListener(this)
        scrollLayout.setOnClickListener(this)
        appInfo.setOnClickListener(this)
        setLauncher.setOnClickListener(this)
        publicRoadmap.setOnClickListener(this)
        autoShowKeyboard.setOnClickListener(this)
        toggleLock.setOnClickListener(this)
        homeAppsNum.setOnClickListener(this)
        dailyWallpaperUrl.setOnClickListener(this)
        dailyWallpaper.setOnClickListener(this)
        alignment.setOnClickListener(this)
        alignmentLeft.setOnClickListener(this)
        alignmentCenter.setOnClickListener(this)
        alignmentRight.setOnClickListener(this)
        alignmentBottom.setOnClickListener(this)
        statusBar.setOnClickListener(this)
        dateTime.setOnClickListener(this)
        dateTimeOn.setOnClickListener(this)
        dateTimeOff.setOnClickListener(this)
        dateOnly.setOnClickListener(this)
        swipeLeftApp.setOnClickListener(this)
        swipeRightApp.setOnClickListener(this)
        appThemeText.setOnClickListener(this)
        themeLight.setOnClickListener(this)
        themeDark.setOnClickListener(this)
        themeSystem.setOnClickListener(this)

        about.setOnClickListener(this)
        share.setOnClickListener(this)
        rate.setOnClickListener(this)
        twitter.setOnClickListener(this)
        instagram.setOnClickListener(this)
        privacy.setOnClickListener(this)
        github.setOnClickListener(this)
        affiliate.setOnClickListener(this)
        moreApps.setOnClickListener(this)

        maxApps0.setOnClickListener(this)
        maxApps1.setOnClickListener(this)
        maxApps2.setOnClickListener(this)
        maxApps3.setOnClickListener(this)
        maxApps4.setOnClickListener(this)
        maxApps5.setOnClickListener(this)
        maxApps6.setOnClickListener(this)
        maxApps7.setOnClickListener(this)
        maxApps8.setOnClickListener(this)

        dailyWallpaper.setOnLongClickListener(this)
        alignment.setOnLongClickListener(this)
        appThemeText.setOnLongClickListener(this)
        swipeLeftApp.setOnLongClickListener(this)
        swipeRightApp.setOnLongClickListener(this)
        toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            prefs.firstSettingsOpen = false
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter = prefs.toShowHintCounter + 1
                publicRoadmap.visibility = View.VISIBLE
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            showToastShort(requireContext(), "Swipe left app enabled")
        } else {
            swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            showToastShort(requireContext(), "Swipe left app disabled")
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            showToastShort(requireContext(), "Swipe right app enabled")
        } else {
            swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            showToastShort(requireContext(), "Swipe right app disabled")
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            statusBar.text = getString(R.string.on)
        } else {
            hideStatusBar()
            statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.showDateTime = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        dateTime.text = getString(when (prefs.showDateTime) {
            Constants.DateTime.DATE_ONLY -> R.string.date
            Constants.DateTime.ON -> R.string.on
            else -> R.string.off
        })
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

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            showToastShort(requireContext(), "No hidden apps")
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf("flag" to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when {
                prefs.lockModeOn -> {
                    prefs.lockModeOn = false
                    deviceManager.removeActiveAdmin(componentName) // for backward compatibility
                }
                isAccessServiceEnabled(requireContext()) -> prefs.lockModeOn = true
                else -> {
                    showToastLong(requireContext(), "Please turn on accessibility service for Olauncher")
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                deviceManager.removeActiveAdmin(componentName)
                prefs.lockModeOn = false
                showToastShort(requireContext(), "Admin permission removed.")
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                activity?.startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
            }
        }
        populateLockSettings()
    }

    private fun removeWallpaper() {
        setPlainWallpaper(requireContext(), android.R.color.black)
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        if (isOlauncherDefault(requireContext()))
            showToastShort(requireContext(), "Your wallpaper will update shortly")
        else
            showToastLong(requireContext(), "Olauncher is not default launcher.\nDaily wallpaper update may fail.")
    }

    private fun updateHomeAppsNum(num: Int) {
        homeAppsNum.text = num.toString()
        appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private fun toggleKeyboardText() {
        prefs.autoShowKeyboard = !prefs.autoShowKeyboard
        populateKeyboardText()
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> appThemeText.text = getString(R.string.light)
            else -> appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) autoShowKeyboard.text = getString(R.string.on)
        else autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) dailyWallpaper.text = getString(R.string.on)
        else dailyWallpaper.text = getString(R.string.off)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isOlauncherDefault.value != true) {
            showToastLong(requireContext(), getString(R.string.please_set_olauncher_as_default_first))
            findNavController().popBackStack()
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> alignment.text = getString(R.string.left)
            Gravity.CENTER -> alignment.text = getString(R.string.center)
            Gravity.END -> alignment.text = getString(R.string.right)
        }
        alignmentBottom.text = if (prefs.homeBottomAlignment)
            getString(R.string.bottom_on)
        else getString(R.string.bottom_off)
    }

    private fun populateLockSettings() {
        if (prefs.lockModeOn) toggleLock.text = getString(R.string.on)
        else toggleLock.text = getString(R.string.off)
    }

    private fun shareApp() {
        val message = "Are you using your phone or your phone is using you?\n" +
                Constants.URL_OLAUNCHER_PLAY_STORE
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun rateApp() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(Constants.URL_OLAUNCHER_PLAY_STORE)
        )
        var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        flags = flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.addFlags(flags)
        startActivity(intent)
    }

    private fun populateSwipeApps() {
        swipeLeftApp.text = prefs.appNameSwipeLeft
        swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            showToastShort(requireContext(), "Long press to enable")
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            showToastShort(requireContext(), "Long press to enable")
            return
        }

        viewModel.getAppList()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf("flag" to flag)
        )
    }

    private fun populateActionHints() {
        when (prefs.toShowHintCounter) {
            Constants.HINT_RATE_US -> {
                viewModel.showMessageDialog(getString(R.string.rate_us_message))
                rate.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
        if (prefs.aboutClicked.not())
            about.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
        if (prefs.rateClicked.not() && prefs.toShowHintCounter > Constants.HINT_RATE_US)
            rate.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
    }
}