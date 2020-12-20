package app.olauncher.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
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
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.showToastLong
import app.olauncher.helper.showToastShort
import app.olauncher.listener.LockTouchListener
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import kotlinx.android.synthetic.main.fragment_home.*


class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        blackOverlay.visibility = View.GONE
        populateHomeApps(false)
        viewModel.isOlauncherDefault()
        showNavBarAndResetScreenTimeout()
    }

    override fun onClick(view: View) {
        when (view.id) {

            R.id.homeApp1 -> if (prefs.appPackage1.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName1, prefs.appPackage1, prefs.appUser1)

            R.id.homeApp2 -> if (prefs.appPackage2.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName2, prefs.appPackage2, prefs.appUser2)

            R.id.homeApp3 -> if (prefs.appPackage3.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName3, prefs.appPackage3, prefs.appUser3)

            R.id.homeApp4 -> if (prefs.appPackage4.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName4, prefs.appPackage4, prefs.appUser4)

            R.id.homeApp5 -> if (prefs.appPackage5.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName5, prefs.appPackage5, prefs.appUser5)

            R.id.homeApp6 -> if (prefs.appPackage6.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName6, prefs.appPackage6, prefs.appUser6)

            R.id.homeApp7 -> if (prefs.appPackage7.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName7, prefs.appPackage7, prefs.appUser7)

            R.id.homeApp8 -> if (prefs.appPackage8.isEmpty()) showLongPressToast()
            else launchApp(prefs.appName8, prefs.appPackage8, prefs.appUser8)

            R.id.clock -> openAlarmApp()
            R.id.date -> openCalendar()
            R.id.setDefaultLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
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
        viewModel.refreshHome.observe(viewLifecycleOwner, Observer<Boolean> {
            populateHomeApps(it)
        })
        viewModel.firstOpen.observe(viewLifecycleOwner, Observer<Boolean> { isFirstOpen ->
            if (isFirstOpen) {
                firstRunTips.visibility = View.VISIBLE
                setDefaultLauncher.visibility = View.GONE
            } else firstRunTips.visibility = View.GONE
        })
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer<Boolean> {
            if (firstRunTips.visibility == View.VISIBLE) return@Observer
            if (it) setDefaultLauncher.visibility = View.GONE
            else setDefaultLauncher.visibility = View.VISIBLE
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner, Observer<Int> {
            setHomeAlignment(it)
        })
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        blackOverlay.setOnTouchListener(getLockScreenGestureListener(context))
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
        clock.setOnClickListener(this)
        date.setOnClickListener(this)
        setDefaultLauncher.setOnClickListener(this)
    }

    private fun setHomeAlignment(gravity: Int) {
        dateTimeLayout.gravity = gravity
        homeAppsLayout.gravity = gravity
        setDefaultLauncher.gravity = gravity
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
        dateTimeLayout.visibility = View.VISIBLE

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        val pm = requireContext().packageManager

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

    private fun launchApp(appName: String, packageName: String, userString: String) {
        viewModel.selectedApp(
            AppModel(appName, packageName, getUserHandleFromString(requireContext(), userString)),
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
        else openDialerApp()
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        if (prefs.appPackageSwipeLeft.isNotEmpty())
            launchApp(prefs.appNameSwipeLeft, prefs.appPackageSwipeLeft, android.os.Process.myUserHandle().toString())
        else openCameraApp()
    }

    private fun openDialerApp() {
        try {
            val sendIntent = Intent(Intent.ACTION_DIAL)
            startActivity(sendIntent)
        } catch (e: java.lang.Exception) {

        }
    }

    private fun openCameraApp() {
        try {
            val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            startActivity(sendIntent)
        } catch (e: java.lang.Exception) {

        }
    }

    private fun openAlarmApp() {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            Log.d("TAG", e.toString())
        }
    }

    private fun openCalendar() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            startActivity(intent)
        } catch (e: java.lang.Exception) {

        }
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

    private fun showNavBarAndResetScreenTimeout() {
        if (Settings.System.canWrite(requireContext()))
            Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, prefs.screenTimeout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.show(WindowInsets.Type.navigationBars())
        } else
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        // populate status bar
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    private fun hideNavBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
            requireActivity().window.insetsController?.hide(WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
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

    private fun setScreenTimeout() {
        // Save the existing screen off timeout
        val screenTimeoutInSettings =
            Settings.System.getInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        if (screenTimeoutInSettings >= 5000) prefs.screenTimeout = screenTimeoutInSettings

        // Set timeout to 5 seconds
        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 5000)
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
                if (prefs.lockModeOn) {
                    if (Settings.System.canWrite(requireContext())) {
                        requireActivity().runOnUiThread {
                            blackOverlay.visibility = View.VISIBLE
                            setScreenTimeout()
                            hideNavBar()
                        }
                    } else {
                        lockPhone()
                    }
                }
                super.onDoubleClick()
            }

            override fun onTripleClick() {
                if (prefs.lockModeOn) lockPhone()
                super.onTripleClick()
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

    private fun getLockScreenGestureListener(context: Context): View.OnTouchListener {
        return object : LockTouchListener(context) {
            override fun onDoubleClick() {
                requireActivity().runOnUiThread {
                    blackOverlay.visibility = View.GONE
                    showNavBarAndResetScreenTimeout()
                }
                super.onDoubleClick()
            }

            override fun onTripleClick() {
                if (prefs.lockModeOn) lockPhone()
                super.onTripleClick()
            }
        }
    }
}