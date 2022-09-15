package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onBackPressed() {
        if (navController.currentDestination?.id != R.id.mainFragment)
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
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
        if (prefs.dailyWallpaper &&
            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
        }
        recreate()
    }

    private fun initClickListeners() {
        binding.okay.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
            viewModel.showMessageDialog("")
        }
        binding.closeOneLink.setOnClickListener {
            binding.supportOlauncherLayout.visibility = View.GONE
        }
        binding.copyOneLink.setOnClickListener {
            copyToClipboard(getAffiliateUrl())
            binding.supportOlauncherLayout.visibility = View.GONE
        }
        binding.openOneLink.setOnClickListener {
            openUrl(getAffiliateUrl())
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.showMessageDialog.observe(this) {
            showMessage(it)
        }
        viewModel.showSupportDialog.observe(this) {
            binding.supportOlauncherLayout.isVisible = it
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this)) return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        binding.messageLayout.visibility = View.GONE
        binding.supportOlauncherLayout.visibility = View.GONE
        // Whenever home button is pressed or user leaves the launcher,
        // pop all the fragments except main
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
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) else {
                showToastLong(
                    this,
                    "Search for launcher or home app"
                )
                Intent(Settings.ACTION_SETTINGS)
            }
            startActivity(intent)
        }
    }

    private fun showMessage(message: String) {
        if (message.isEmpty()) return
        binding.messageTextView.text = message
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun getAffiliateUrl(): String {
        return if (TimeZone.getDefault().displayName.equals(Constants.IST_NAME, true))
            Constants.URL_AFFILIATE_IN
        else
            Constants.URL_AFFILIATE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) {
                    prefs.lockModeOn = true
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                        showMessage(getString(R.string.double_tap_lock_is_enabled_message))
                    else
                        showMessage(getString(R.string.double_tap_lock_uninstall_message))
                }
            }
        }
    }
}