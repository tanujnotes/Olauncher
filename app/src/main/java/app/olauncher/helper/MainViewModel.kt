package app.olauncher.helper

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val prefs = Prefs(appContext)

    private val selectedApp = MutableLiveData<AppModelWithFlag>()
    val refreshHome = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val isDarkModeOn = MutableLiveData<Boolean>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                launchApp(appModel.appPackage)
            }
            Constants.FLAG_SET_HOME_APP_1 -> {
                prefs.appName1 = appModel.appLabel
                prefs.appPackage1 = appModel.appPackage
                refreshHome(true)
            }
            Constants.FLAG_SET_HOME_APP_2 -> {
                prefs.appName2 = appModel.appLabel
                prefs.appPackage2 = appModel.appPackage
                refreshHome(true)
            }
            Constants.FLAG_SET_HOME_APP_3 -> {
                prefs.appName3 = appModel.appLabel
                prefs.appPackage3 = appModel.appPackage
                refreshHome(true)
            }
            Constants.FLAG_SET_HOME_APP_4 -> {
                prefs.appName4 = appModel.appLabel
                prefs.appPackage4 = appModel.appPackage
                refreshHome(true)
            }
        }
        selectedApp.value = AppModelWithFlag(appModel, flag)
    }

    private fun refreshHome(value: Any) {
        refreshHome.value = value
    }

    private fun launchApp(packageName: String) {
        try {
            val intent: Intent? = appContext.packageManager.getLaunchIntentForPackage(packageName)
            intent?.addCategory(Intent.CATEGORY_LAUNCHER)
            appContext.startActivity(intent)
        } catch (e: Exception) {
            refreshHome(true)
        }
    }

    fun getAppList() {
        viewModelScope.launch {
            appList.value = getAppsList(appContext)
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.value =
            isOlauncherDefault(appContext)
    }

    fun resetDefaultLauncherApp(context: Context) {
        resetDefaultLauncher(context)
        launcherResetFailed.value = getDefaultLauncherPackage(
            appContext
        ).contains(".")
    }

    fun switchTheme() {
        prefs.darkModeOn = !prefs.darkModeOn
        setTheme(prefs.darkModeOn)
    }

    fun setTheme(darkMode: Boolean) {
        prefs.darkModeOn = darkMode
        if (darkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        isDarkModeOn.value = prefs.darkModeOn
    }
}

data class AppModelWithFlag(
    val appModel: AppModel,
    val flag: Int
)