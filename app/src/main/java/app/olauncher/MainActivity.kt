package app.olauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.isTablet
import app.olauncher.helper.showToastLong
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onBackPressed() {
        if (navController.currentDestination?.id != R.id.mainFragment)
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        when (prefs.themeColor) {
            Constants.THEME_MODE_LIGHT -> setTheme(R.style.LightTheme)
            else -> setTheme(R.style.DarkTheme)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        val viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
        }

        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
        okay.setOnClickListener {
            messageLayout.visibility = View.GONE
            viewModel.showMessageDialog("")
        }
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

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this, {
            openLauncherChooser(it)
        })
        viewModel.showMessageDialog.observe(this, {
            showMessage(it)
        })
    }

    private fun setupOrientation() {
        if (isTablet(this)) return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        // Whenever home button is pressed or user leaves the launcher,
        // pop all the fragments except main
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
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
        messageTextView.text = message
        messageLayout.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) {
                    Prefs(this).lockModeOn = true
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                        showMessage(getString(R.string.double_tap_lock_is_enabled_message))
                    else
                        showMessage(getString(R.string.double_tap_lock_uninstall_message))
                }
            }
        }
    }
}