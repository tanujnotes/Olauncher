package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.hasBeenDays
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.shareApp
import app.olauncher.helper.showLauncherSelector
import app.olauncher.helper.showToast
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onBackPressed() {
        if (navController.currentDestination?.id != R.id.mainFragment)
            super.onBackPressed()
    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
        }

        initClickListeners()
        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onStop() {
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.ABOUT -> {
                    showMessageDialog(getString(R.string.app_name), getString(R.string.welcome_to_olauncher_settings), getString(R.string.okay)) {
                        binding.messageLayout.visibility = View.GONE
                    }
                }

                Constants.Dialog.REVIEW -> {
                    prefs.userState = Constants.UserState.RATE
                    showMessageDialog(getString(R.string.did_you_know), getString(R.string.review_message), getString(R.string.leave_a_review)) {
                        binding.messageLayout.visibility = View.GONE
                        prefs.rateClicked = true
                        showToast("😇❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.RATE -> {
                    prefs.userState = Constants.UserState.SHARE
                    showMessageDialog(getString(R.string.app_name), getString(R.string.rate_us_message), getString(R.string.rate_now)) {
                        binding.messageLayout.visibility = View.GONE
                        prefs.rateClicked = true
                        showToast("🤩❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.SHARE -> {
                    prefs.shareShownTime = System.currentTimeMillis()
                    showMessageDialog(getString(R.string.hey), getString(R.string.share_message), getString(R.string.share_now)) {
                        binding.messageLayout.visibility = View.GONE
                        showToast("😊❤️")
                        shareApp()
                    }
                }

                Constants.Dialog.HIDDEN -> {
                    showMessageDialog(getString(R.string.hidden_apps), getString(R.string.hidden_apps_message), getString(R.string.okay)) {
                        binding.messageLayout.visibility = View.GONE
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessageDialog(getString(R.string.app_name), getString(R.string.keyboard_message), getString(R.string.okay)) {
                        binding.messageLayout.visibility = View.GONE
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessageDialog(getString(R.string.screen_time), getString(R.string.app_usage_message), getString(R.string.permission)) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }

                Constants.Dialog.PRO_MESSAGE -> {
                    showMessageDialog(getString(R.string.hey), getString(R.string.pro_message), getString(R.string.olauncher_pro)) {
                        openUrl(Constants.URL_OLAUNCHER_PRO)
                    }
                }
            }
        }
    }

    private fun showMessageDialog(title: String, message: String, action: String, clickListener: () -> Unit) {
        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.tvAction.text = action
        binding.tvAction.setOnClickListener { clickListener() }
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()

        when (prefs.userState) {
            Constants.UserState.START -> {
                if (prefs.firstOpenTime.hasBeenHours(1))
                    prefs.userState = Constants.UserState.REVIEW
            }

            Constants.UserState.REVIEW -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isOlauncherDefault(this))
                    viewModel.showDialog.postValue(Constants.Dialog.REVIEW)
            }

            Constants.UserState.RATE -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isOlauncherDefault(this)
                    && prefs.firstOpenTime.hasBeenDays(7)
                    && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 18
                ) viewModel.showDialog.postValue(Constants.Dialog.RATE)
            }

            Constants.UserState.SHARE -> {
                if (isOlauncherDefault(this) && prefs.firstOpenTime.hasBeenDays(21)
                    && prefs.shareShownTime.hasBeenDays(42)
                    && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 18
                ) viewModel.showDialog.postValue(Constants.Dialog.SHARE)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        binding.messageLayout.visibility = View.GONE
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }
}