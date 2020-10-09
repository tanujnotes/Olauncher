package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showToastLong
import app.olauncher.helper.showToastShort
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

        homeAppsNum.text = prefs.homeAppsNum.toString()
        populateLockSettings()
        populateWallpaperText()
        populateSwipeLeftRight()
        populateAlignment()
        populateSwipeApps()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.settingsRootLayout -> appsNumSelectLayout.visibility = View.GONE
            R.id.appInfo -> openAppInfo(requireContext(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            R.id.toggleOnOff -> toggleLockMode()
            R.id.experimental -> openEditSettingsPermission()
            R.id.homeAppsNum -> appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignment -> viewModel.updateHomeAlignment()

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.swipeLeftRight -> {
                prefs.swipeLeftRight = !prefs.swipeLeftRight
                populateSwipeLeftRight()
            }
            R.id.swipeLeftApp -> showAppList(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppList(Constants.FLAG_SET_SWIPE_RIGHT_APP)

            R.id.privacy -> openUrl(Constants.URL_OLAUNCHER_PRIVACY)
            R.id.share -> shareApp()
            R.id.rate -> rateApp()
            R.id.email -> sendEmailIntent()
            R.id.github -> openUrl(Constants.URL_GITHUB_TANUJNOTES)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.swipeLeftApp -> showAppList(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppList(Constants.FLAG_SET_SWIPE_RIGHT_APP)
        }
        return true
    }

    private fun initClickListeners() {
        settingsRootLayout.setOnClickListener(this)
        appInfo.setOnClickListener(this)
        setLauncher.setOnClickListener(this)
        toggleOnOff.setOnClickListener(this)
        experimental.setOnClickListener(this)
        homeAppsNum.setOnClickListener(this)
        dailyWallpaperUrl.setOnClickListener(this)
        dailyWallpaper.setOnClickListener(this)
        alignment.setOnClickListener(this)
        swipeLeftRight.setOnClickListener(this)
        swipeLeftApp.setOnClickListener(this)
        swipeRightApp.setOnClickListener(this)
        privacy.setOnClickListener(this)
        share.setOnClickListener(this)
        rate.setOnClickListener(this)
        email.setOnClickListener(this)
        github.setOnClickListener(this)

        maxApps0.setOnClickListener(this)
        maxApps1.setOnClickListener(this)
        maxApps2.setOnClickListener(this)
        maxApps3.setOnClickListener(this)
        maxApps4.setOnClickListener(this)
        maxApps5.setOnClickListener(this)
        maxApps6.setOnClickListener(this)
        maxApps7.setOnClickListener(this)
        maxApps8.setOnClickListener(this)

        swipeLeftApp.setOnLongClickListener(this)
        swipeRightApp.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer<Boolean> {
            if (it) setLauncher.text = getString(R.string.change_default_launcher)
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner, Observer<Int> {
            populateAlignment()
        })
        viewModel.updateSwipeApps.observe(viewLifecycleOwner, Observer<Any> {
            populateSwipeApps()
        })
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
            openEditSettingsPermission()
            showToastLong(requireContext(), "Admin permission removed. You can remove settings permission too.")
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

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) dailyWallpaper.text = getString(R.string.on)
        else dailyWallpaper.text = getString(R.string.off)
    }

    private fun populateSwipeLeftRight() {
        if (prefs.swipeLeftRight) swipeLeftRight.text = getString(R.string.on)
        else swipeLeftRight.text = getString(R.string.off)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> alignment.text = getString(R.string.left)
            Gravity.CENTER -> alignment.text = getString(R.string.center)
            Gravity.END -> alignment.text = getString(R.string.right)
        }
    }

    private fun populateLockSettings() {
        if (prefs.lockModeOn) toggleOnOff.text = getString(R.string.on)
        else toggleOnOff.text = getString(R.string.off)
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun shareApp() {
        val message = "Give your phone a new ultra clean look - every day.\n" +
                "Download Olauncher! Free and open source, forever.\n" +
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
            showToastLong(requireContext(), "Sending email failed. Direct message instead.")
            openUrl(Constants.URL_TWITTER_TANUJNOTES)
        }
    }

    private fun populateSwipeApps() {
        swipeLeftApp.text = prefs.appNameSwipeLeft
        swipeRightApp.text = prefs.appNameSwipeRight
    }

    private fun showAppList(flag: Int) {
        viewModel.getAppList()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf("flag" to flag)
        )
    }

    private fun openEditSettingsPermission() {
        //        if (!Settings.System.canWrite(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
        startActivityForResult(intent, Constants.REQUEST_CODE_EDIT_SETTINGS)
//        }
    }
}