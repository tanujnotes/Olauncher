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
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
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
        savedInstanceState: Bundle?
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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            experimental.visibility = View.GONE

        homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateLockSettings()
        populateWallpaperText()
        populateAlignment()
        populateStatusBar()
        populateSwipeApps()
        populateActionHints()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        appsNumSelectLayout.visibility = View.GONE
        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.appInfo -> openAppInfo(requireContext(), android.os.Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            R.id.toggleLock -> toggleLockMode()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.doubleTapText -> openEditSettingsPermission()
            R.id.experimental -> openEditSettingsPermission()
            R.id.homeAppsNum -> appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignment -> viewModel.updateHomeAlignment()
            R.id.statusBar -> toggleStatusBar()

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

            R.id.about -> openUrl(Constants.URL_ABOUT_OLAUNCHER)
            R.id.share -> shareApp()
            R.id.rate -> rateApp()
            R.id.tweet -> openUrl(Constants.URL_TWITTER_TANUJ)
            R.id.privacy -> openUrl(Constants.URL_OLAUNCHER_PRIVACY)
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
        }
        return true
    }

    private fun initClickListeners() {
        olauncherHiddenApps.setOnClickListener(this)
        settingsRootLayout.setOnClickListener(this)
        appInfo.setOnClickListener(this)
        setLauncher.setOnClickListener(this)
        autoShowKeyboard.setOnClickListener(this)
        toggleLock.setOnClickListener(this)
        doubleTapText.setOnClickListener(this)
        experimental.setOnClickListener(this)
        homeAppsNum.setOnClickListener(this)
        dailyWallpaperUrl.setOnClickListener(this)
        dailyWallpaper.setOnClickListener(this)
        alignment.setOnClickListener(this)
        statusBar.setOnClickListener(this)
        swipeLeftApp.setOnClickListener(this)
        swipeRightApp.setOnClickListener(this)

        about.setOnClickListener(this)
        share.setOnClickListener(this)
        rate.setOnClickListener(this)
        tweet.setOnClickListener(this)
        privacy.setOnClickListener(this)

        maxApps0.setOnClickListener(this)
        maxApps1.setOnClickListener(this)
        maxApps2.setOnClickListener(this)
        maxApps3.setOnClickListener(this)
        maxApps4.setOnClickListener(this)
        maxApps5.setOnClickListener(this)
        maxApps6.setOnClickListener(this)
        maxApps7.setOnClickListener(this)
        maxApps8.setOnClickListener(this)

        // Long click listeners
        dailyWallpaper.setOnLongClickListener(this)
        alignment.setOnLongClickListener(this)
        swipeLeftApp.setOnLongClickListener(this)
        swipeRightApp.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer<Boolean> {
            if (it) {
                setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter = prefs.toShowHintCounter + 1
            }
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner, Observer<Int> {
            populateAlignment()
        })
        viewModel.updateSwipeApps.observe(viewLifecycleOwner, Observer<Any> {
            populateSwipeApps()
        })
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            swipeLeftApp.setTextColor(requireContext().getColor(R.color.colorPrimary))
            showToastShort(requireContext(), "Swipe left app enabled")
        } else {
            swipeLeftApp.setTextColor(requireContext().getColor(R.color.colorPrimaryTrans50))
            showToastShort(requireContext(), "Swipe left app disabled")
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            swipeRightApp.setTextColor(requireContext().getColor(R.color.colorPrimary))
            showToastShort(requireContext(), "Swipe right app enabled")
        } else {
            swipeRightApp.setTextColor(requireContext().getColor(R.color.colorPrimaryTrans50))
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

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION")
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
        prefs.lockModeOn = isAdmin
    }

    private fun toggleLockMode() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (isAdmin) {
            deviceManager.removeActiveAdmin(componentName)
            prefs.lockModeOn = false
            populateLockSettings()
            showToastShort(requireContext(), "Admin permission removed.")
            if (Settings.System.canWrite(requireContext())) {
                openEditSettingsPermission()
                showToastLong(requireContext(), "You can remove settings permission too.")
            }
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

    private fun removeWallpaper() {
        prefs.dailyWallpaper = false
        populateWallpaperText()
        setBlackWallpaper(requireContext())
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
        if (!isOlauncherDefault(requireContext()))
            showToastLong(requireContext(), "Olauncher is not default launcher.\nDaily wallpaper update may fail.")
        else
            showToastShort(requireContext(), "Your wallpaper will update shortly")
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

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) autoShowKeyboard.text = getString(R.string.on)
        else autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) dailyWallpaper.text = getString(R.string.on)
        else dailyWallpaper.text = getString(R.string.off)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> alignment.text = getString(R.string.left)
            Gravity.CENTER -> alignment.text = getString(R.string.center)
            Gravity.END -> alignment.text = getString(R.string.right)
        }
    }

    private fun populateLockSettings() {
        if (prefs.lockModeOn) toggleLock.text = getString(R.string.on)
        else toggleLock.text = getString(R.string.off)
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun shareApp() {
        val message = "You should use your phone, not the other way round. -Olauncher\n" +
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

    private fun sendEmailIntent() {
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.data = Uri.parse(
            "mailto:thetanuj1@gmail.com?" +
                    "subject=Hello%20Team%20Olauncher!"
        )
        try {
            startActivity(emailIntent)
        } catch (e: Exception) {
            showToastLong(requireContext(), "Failed! Send email to thetanuj1@gmail.com")
        }
    }

    private fun populateSwipeApps() {
        swipeLeftApp.text = prefs.appNameSwipeLeft
        swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            swipeLeftApp.setTextColor(requireContext().getColor(R.color.colorPrimaryTrans50))
        if (!prefs.swipeRightEnabled)
            swipeRightApp.setTextColor(requireContext().getColor(R.color.colorPrimaryTrans50))
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
            Constants.HINT_ABOUT_US -> {
                Toast.makeText(context, getString(R.string.about_hint), Toast.LENGTH_SHORT).show()
                Toast.makeText(context, getString(R.string.about_hint), Toast.LENGTH_LONG).show()
                about.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
            }
            Constants.HINT_RATE_US -> {
                Toast.makeText(context, getString(R.string.rate_us_hint), Toast.LENGTH_SHORT).show()
                Toast.makeText(context, getString(R.string.rate_us_hint), Toast.LENGTH_LONG).show()
                rate.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
            }
        }
    }

    private fun openEditSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
        activity?.startActivityForResult(intent, Constants.REQUEST_CODE_EDIT_SETTINGS)
    }
}