package app.olauncher.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.*
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var vibrator: Vibrator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        populateHomeApps(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {
            }
            R.id.clock -> openAlarmApp(requireContext())
            R.id.date -> openCalendar(requireContext())
            R.id.setDefaultLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
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

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty())
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty())
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty())
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty())
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty())
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty())
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty())
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty())
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            firstRunTips.visibility = View.VISIBLE
            setDefaultLauncher.visibility = View.GONE
        } else firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner, {
            populateHomeApps(it)
        })
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (firstRunTips.visibility == View.VISIBLE) return@Observer
            if (it) setDefaultLauncher.visibility = View.GONE
            else setDefaultLauncher.visibility = View.VISIBLE
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner, {
            setHomeAlignment(it)
        })
        viewModel.toggleDateTime.observe(viewLifecycleOwner, {
            if (it) dateTimeLayout.visibility = View.VISIBLE
            else dateTimeLayout.visibility = View.GONE
        })
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, homeApp1))
        homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, homeApp2))
        homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, homeApp3))
        homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, homeApp4))
        homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, homeApp5))
        homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, homeApp6))
        homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, homeApp7))
        homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, homeApp8))
    }

    private fun initClickListeners() {
        lock.setOnClickListener(this)
        clock.setOnClickListener(this)
        date.setOnClickListener(this)
        setDefaultLauncher.setOnClickListener(this)
    }

    private fun setHomeAlignment(gravity: Int) {
        dateTimeLayout.gravity = gravity
        homeAppsLayout.gravity = gravity
        homeApp1.gravity = gravity
        homeApp2.gravity = gravity
        homeApp3.gravity = gravity
        homeApp4.gravity = gravity
        homeApp5.gravity = gravity
        homeApp6.gravity = gravity
        homeApp7.gravity = gravity
        homeApp8.gravity = gravity
    }

    private fun populateHomeApps(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        if (prefs.showDateTime) dateTimeLayout.visibility = View.VISIBLE
        else dateTimeLayout.visibility = View.GONE

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp1, prefs.appName1, prefs.appPackage1, prefs.appUser1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp2, prefs.appName2, prefs.appPackage2, prefs.appUser2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp3, prefs.appName3, prefs.appPackage3, prefs.appUser3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp4, prefs.appName4, prefs.appPackage4, prefs.appUser4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp5, prefs.appName5, prefs.appPackage5, prefs.appUser5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

        homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp6, prefs.appName6, prefs.appPackage6, prefs.appUser6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp7, prefs.appName7, prefs.appPackage7, prefs.appUser7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(homeApp8, prefs.appName8, prefs.appPackage8, prefs.appUser8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
    }

    private fun setHomeAppText(textView: TextView, appName: String, packageName: String, userString: String): Boolean {
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        homeApp1.visibility = View.GONE
        homeApp2.visibility = View.GONE
        homeApp3.visibility = View.GONE
        homeApp4.visibility = View.GONE
        homeApp5.visibility = View.GONE
        homeApp6.visibility = View.GONE
        homeApp7.visibility = View.GONE
        homeApp8.visibility = View.GONE

        // Added as a potential fix to clock freeze issue
        dateTimeLayout.visibility = View.GONE
    }

    private fun homeAppClicked(location: Int) {
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else launchApp(
            prefs.getAppName(location),
            prefs.getAppPackage(location),
            prefs.getAppUser(location)
        )
    }

    private fun launchApp(appName: String, packageName: String, userString: String) {
        viewModel.selectedApp(
            AppModel(appName, null, packageName, getUserHandleFromString(requireContext(), userString)),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false) {
        viewModel.getAppList()
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf("flag" to flag, "rename" to rename)
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf("flag" to flag, "rename" to rename)
            )
            e.printStackTrace()
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotificationDrawer(context: Context) {
        // Source: https://stackoverflow.com/a/51132142
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        if (prefs.appPackageSwipeRight.isNotEmpty())
            launchApp(prefs.appNameSwipeRight, prefs.appPackageSwipeRight, android.os.Process.myUserHandle().toString())
        else openDialerApp(requireContext())
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        if (prefs.appPackageSwipeLeft.isNotEmpty())
            launchApp(prefs.appNameSwipeLeft, prefs.appPackageSwipeLeft, android.os.Process.myUserHandle().toString())
        else openCameraApp(requireContext())
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                showToastLong(requireContext(), "Please turn on double tap to lock")
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                showToastLong(requireContext(), "Olauncher failed to lock device.\nPlease check your app settings.")
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

    private fun showLongPressToast() = showToastShort(requireContext(), "Long press to select app")

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
                expandNotificationDrawer(context)
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: java.lang.Exception) {
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (prefs.lockModeOn)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        requireActivity().runOnUiThread {
                            if (isAccessServiceEnabled(requireContext()))
                                lock.performClick()
                            else {
                                prefs.lockModeOn = false
                                showToastLong(requireContext(), "Please turn on accessibility service for Olauncher")
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                    } else
                        lockPhone()
            }

            override fun onTripleClick() {
                super.onTripleClick()
                changeAppTheme()
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
                expandNotificationDrawer(context)
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
}