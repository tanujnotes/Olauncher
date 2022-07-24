package app.olaunchercf.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olaunchercf.MainViewModel
import app.olaunchercf.R
import app.olaunchercf.data.AppModel
import app.olaunchercf.data.Constants
import app.olaunchercf.data.Prefs
import app.olaunchercf.databinding.FragmentHomeBinding
import app.olaunchercf.helper.*
import app.olaunchercf.listener.OnSwipeTouchListener
import app.olaunchercf.listener.ViewSwipeTouchListener

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var vibrator: Vibrator

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())

        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        initObservers()
        initHomeApps() // must be before alignments

        setHomeAlignment(prefs.homeAlignment)
        Log.d("time", "1")
        setTimeAlignment(prefs.timeAlignment)
        Log.d("time", "1")

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
            R.id.lock -> { }
            R.id.clock -> openClickClockApp()
            R.id.date -> openClickDateApp()
            R.id.setDefaultLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            else -> {
                try { // Launch app
                    val appLocation = view.id.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val n = view.id
        val (name, _, _, _) = prefs.getHomeAppValues(n)
        showAppList(Constants.FLAG_SET_HOME_APP, name.isNotEmpty(), true, n)
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeApps(it)
        }
        with(viewModel) {
            isOlauncherDefault.observe(viewLifecycleOwner, Observer {
                if (binding.firstRunTips.visibility == View.VISIBLE) return@Observer
                if (it) binding.setDefaultLauncher.visibility = View.GONE
                else binding.setDefaultLauncher.visibility = View.VISIBLE
            })
            homeAppAlignment.observe(viewLifecycleOwner) {
                setHomeAlignment(it)
            }
            timeAlignment.observe(viewLifecycleOwner) {
                setTimeAlignment(it)
            }
            timeVisible.observe(viewLifecycleOwner) {
                if (it) {
                    binding.clock.visibility = View.VISIBLE
                } else {
                    binding.clock.visibility = View.GONE
                }
            }
            dateVisible.observe(viewLifecycleOwner) {
                if (it) {
                    binding.date.visibility = View.VISIBLE
                } else {
                    binding.date.visibility = View.GONE
                }
            }
            /*toggleDateTime.observe(viewLifecycleOwner) {
                if (it) binding.dateTimeLayout.visibility = View.VISIBLE
                else binding.dateTimeLayout.visibility = View.GONE
            }*/
        }
    }

    private fun initHomeApps() {
        binding.homeAppsLayout.removeAllViews()

        for (i in 0 until prefs.homeAppsNum) {
            val view = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
            view.apply {
                textSize = prefs.textSize.toFloat()
                id = i
                setOnTouchListener(getViewSwipeTouchListener(context, this))
            }
            // swipe

            binding.homeAppsLayout.addView(view)
        }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
    }
    @SuppressLint("RtlHardcoded")
    private fun setTimeAlignment(gravity_const: Constants.Gravity) {
        val gravity = when(gravity_const) {
            Constants.Gravity.Left -> Gravity.LEFT
            Constants.Gravity.Center -> Gravity.CENTER
            Constants.Gravity.Right -> Gravity.RIGHT
        }
        binding.dateTimeLayout.gravity = gravity
    }

    private fun setHomeAlignment(gravity_const: Constants.Gravity) {
        val gravity = when(gravity_const) {
            Constants.Gravity.Left -> Gravity.LEFT
            Constants.Gravity.Center -> Gravity.CENTER
            Constants.Gravity.Right -> Gravity.RIGHT
        }
        binding.homeAppsLayout.gravity = gravity
        binding.homeAppsLayout.children.forEach {
            (it as TextView).gravity = gravity
        }
    }

    private fun populateHomeApps(appCountUpdated: Boolean) {
        if (appCountUpdated) initHomeApps()

        if (prefs.showTime) binding.clock.visibility = View.VISIBLE
        else binding.clock.visibility = View.GONE
        if (prefs.showDate) binding.date.visibility = View.VISIBLE
        else binding.date.visibility = View.GONE

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return // TODO: place clock in center when no apps are shown

        binding.homeAppsLayout.children.forEachIndexed { i, app ->
            val (name, pack, alias) = prefs.getHomeAppValues(i)
            if (!setHomeAppText(app as TextView, name, pack, alias)) {
                prefs.resetHomeAppValues(i)
            }
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

    private fun homeAppClicked(location: Int) {
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else launchApp(
            prefs.getAppName(location),
            prefs.getAppPackage(location),
            prefs.getAppActivity(location),
            prefs.getAppUser(location)
        )
    }

    private fun launchApp(appName: String, packageName: String, appActivity: String,
                          userString: String) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                appActivity,
                getUserHandleFromString(requireContext(), userString),
                Prefs(requireContext()).getAppAlias(appName)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, showHiddenApps: Boolean = false, n: Int = 0) {
        viewModel.getAppList(showHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf("flag" to flag, "rename" to rename, "n" to n)
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
            launchApp(
                prefs.appNameSwipeRight,
                prefs.appPackageSwipeRight,
                prefs.appActivitySwipeRight,
                android.os.Process.myUserHandle().toString()
            )
        else openDialerApp(requireContext())
    }

    private fun openClickClockApp() {
        if (prefs.appPackageClickClock.isNotEmpty())
            launchApp(
                prefs.appNameClickClock,
                prefs.appPackageClickClock,
                prefs.appActivityClickClock,
                android.os.Process.myUserHandle().toString()
            )
        else openAlarmApp(requireContext())
    }

    private fun openClickDateApp() {
        if (prefs.appPackageClickClock.isNotEmpty())
            launchApp(
                prefs.appNameClickDate,
                prefs.appPackageClickDate,
                prefs.appActivityClickDate,
                android.os.Process.myUserHandle().toString()
            )
        else openCalendar(requireContext())
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        if (prefs.appPackageSwipeLeft.isNotEmpty())
            launchApp(
                prefs.appNameSwipeLeft,
                prefs.appPackageSwipeLeft,
                prefs.appActivitySwipeLeft,
                android.os.Process.myUserHandle().toString()
            )
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
                if (prefs.lockModeOn) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        requireActivity().runOnUiThread {
                            if (isAccessServiceEnabled(requireContext())) {
                                binding.lock.performClick()
                            } else {
                                // prefs.lockModeOn = false
                                showToastLong(
                                    requireContext(),
                                    "Please turn on accessibility service for Olauncher"
                                )
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                    } else {
                        lockPhone()
                    }
                }
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
