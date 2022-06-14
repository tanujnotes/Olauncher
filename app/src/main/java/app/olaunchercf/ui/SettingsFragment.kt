package app.olaunchercf.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olaunchercf.BuildConfig
import app.olaunchercf.MainActivity
import app.olaunchercf.MainViewModel
import app.olaunchercf.R
import app.olaunchercf.data.Constants
import app.olaunchercf.data.Prefs
import app.olaunchercf.helper.*
import app.olaunchercf.listener.DeviceAdmin
import kotlinx.android.synthetic.main.fragment_settings.*
import java.util.*

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

        populateAppsNum()
        initAppsNum()

        populateKeyboardText()
        populateLockSettings()
        populateAppThemeText()
        populateAlignment()

        populateLanguageText()
        initLanguageText()

        populateTextSizeText()

        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateClickApps()

        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        appsNumSelectLayout.visibility = View.GONE
        alignmentSelectLayout.visibility = View.GONE
        appThemeSelectLayout.visibility = View.GONE
        appLangSelectLayout.visibility = View.GONE
        textSizeLayout.visibility = View.GONE

        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.appInfo -> openAppInfo(requireContext(), android.os.Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            R.id.toggleLock -> toggleLockMode()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> appsNumSelectLayout.visibility = View.VISIBLE
            R.id.alignment -> alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> toggleDateTime()
            R.id.appThemeText -> appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.appLangText -> appLangSelectLayout.visibility = View.VISIBLE

            R.id.textSizeText -> textSizeLayout.visibility = View.VISIBLE
            R.id.textSizeHuge -> setTextSize(Constants.TEXT_SIZE_HUGE)
            R.id.textSizeNormal -> setTextSize(Constants.TEXT_SIZE_NORMAL)
            R.id.textSizeSmall -> setTextSize(Constants.TEXT_SIZE_SMALL)

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.clockClickApp -> showAppListIfEnabled(Constants.FLAG_SET_CLICK_CLOCK_APP)
            R.id.dateClickApp -> showAppListIfEnabled(Constants.FLAG_SET_CLICK_DATE_APP)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
            }
            R.id.appThemeText -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
        autoShowKeyboard.setOnClickListener(this)
        toggleLock.setOnClickListener(this)
        homeAppsNum.setOnClickListener(this)
        alignment.setOnClickListener(this)
        alignmentLeft.setOnClickListener(this)
        alignmentCenter.setOnClickListener(this)
        alignmentRight.setOnClickListener(this)
        statusBar.setOnClickListener(this)
        dateTime.setOnClickListener(this)
        swipeLeftApp.setOnClickListener(this)
        swipeRightApp.setOnClickListener(this)

        clockClickApp.setOnClickListener(this)
        dateClickApp.setOnClickListener(this)

        appThemeText.setOnClickListener(this)
        themeLight.setOnClickListener(this)
        themeDark.setOnClickListener(this)

        appLangText.setOnClickListener(this)
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
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
        viewModel.updateClickApps.observe(viewLifecycleOwner) {
            populateClickApps()
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

    private fun toggleDateTime() {
        prefs.showDateTime = !prefs.showDateTime
        populateDateTime()
        viewModel.toggleDateTime(prefs.showDateTime)
    }

    private fun populateDateTime() {
        if (prefs.showDateTime) dateTime.text = getString(R.string.on)
        else dateTime.text = getString(R.string.off)
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

    private fun setLang(lang: String) {
        prefs.language = lang
        populateLanguageText(lang)

        // restart activity
        activity?.let {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            it.startActivity(intent)
            it.finish()
        }
    }
    private fun setTextSize(size: Float) {
        if (size == Constants.TEXT_SIZE_HUGE || size == Constants.TEXT_SIZE_NORMAL || size == Constants.TEXT_SIZE_SMALL) {
            prefs.textSize = size

            populateTextSizeText(size)

            // restart activity
            activity?.let {
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                it.startActivity(intent)
                it.finish()
            }
        }
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return

        requireActivity().recreate()
    }

    private fun initAppsNum() {
        for (i in 0..Constants.MAX_HOME_APPS) {
            val view = layoutInflater.inflate(R.layout.settings_button, null) as TextView
            view.apply {
                text = i.toString()
                setPadding(30, 20, 30, 20)
                setOnClickListener{
                    updateHomeAppsNum(i)
                }
            }

            appsNum_layout.addView(view)
        }
    }

    private fun populateAppsNum() {
        homeAppsNum.text = prefs.homeAppsNum.toString()
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> appThemeText.text = getString(R.string.light)
            else -> appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun initLanguageText() {
        val languages = arrayOf(
            Pair(R.string.lang_system, Constants.LANG_SYSTEM),
            Pair(R.string.lang_en, Constants.LANG_EN),
            Pair(R.string.lang_de, Constants.LANG_DE),
            Pair(R.string.lang_es, Constants.LANG_ES),
            Pair(R.string.lang_fr, Constants.LANG_FR),
            Pair(R.string.lang_it, Constants.LANG_IT),
            Pair(R.string.lang_se, Constants.LANG_SE),
            Pair(R.string.lang_tr, Constants.LANG_TR),
            Pair(R.string.lang_gr, Constants.LANG_GR),
        )

        for ((button_text, lang) in languages) {
            val view = layoutInflater.inflate(R.layout.settings_button, null) as TextView
            view.apply {
                text = getString(button_text)
                setPadding(12)
                setOnClickListener{
                    setLang(lang)
                }
            }

            lang_layout.addView(view)
        }
    }

    private fun populateLanguageText(language: String = prefs.language) {
        when (language) {
            Constants.LANG_SYSTEM -> appLangText.text = getString(R.string.lang_system)
            Constants.LANG_DE -> appLangText.text = getString(R.string.lang_de)
            Constants.LANG_ES -> appLangText.text = getString(R.string.lang_es)
            Constants.LANG_FR -> appLangText.text = getString(R.string.lang_fr)
            Constants.LANG_IT -> appLangText.text = getString(R.string.lang_it)
            Constants.LANG_SE -> appLangText.text = getString(R.string.lang_se)
            Constants.LANG_TR -> appLangText.text = getString(R.string.lang_tr)
            Constants.LANG_GR -> appLangText.text = getString(R.string.lang_gr)
            else -> appLangText.text = getString(R.string.lang_en)
        }
    }

    private fun populateTextSizeText(size: Float = prefs.textSize) {
        when(size) {
            Constants.TEXT_SIZE_HUGE -> textSizeText.text = getString(R.string.text_size_huge)
            Constants.TEXT_SIZE_NORMAL -> textSizeText.text = getString(R.string.text_size_normal)
            Constants.TEXT_SIZE_SMALL -> textSizeText.text = getString(R.string.text_size_small)
        }
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) autoShowKeyboard.text = getString(R.string.on)
        else autoShowKeyboard.text = getString(R.string.off)
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

    private fun populateSwipeApps() {
        swipeLeftApp.text = prefs.appNameSwipeLeft
        swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }
    private fun populateClickApps() {
        clockClickApp.text = prefs.appNameClickClock
        dateClickApp.text = prefs.appNameClickDate
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
}
