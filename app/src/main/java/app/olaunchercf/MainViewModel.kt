package app.olaunchercf

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.*
import app.olaunchercf.data.AppModel
import app.olaunchercf.data.Constants
import app.olaunchercf.data.Prefs
import app.olaunchercf.helper.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Boolean>()
    val updateSwipeApps = MutableLiveData<Any>()
    val updateClickApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>>()
    val hiddenApps = MutableLiveData<List<AppModel>>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val showMessageDialog = MutableLiveData<String>()
    val showSupportDialog = MutableLiveData<Boolean>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                launchApp(appModel)
            }
            Constants.FLAG_HIDDEN_APPS -> {
                launchApp(appModel)
            }
            Constants.FLAG_SET_HOME_APP_1 -> {
                prefs.appName1 = appModel.appLabel
                prefs.appPackage1 = appModel.appPackage
                prefs.appUser1 = appModel.user.toString()
                prefs.appActivity1 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_2 -> {
                prefs.appName2 = appModel.appLabel
                prefs.appPackage2 = appModel.appPackage
                prefs.appUser2 = appModel.user.toString()
                prefs.appActivity2 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_3 -> {
                prefs.appName3 = appModel.appLabel
                prefs.appPackage3 = appModel.appPackage
                prefs.appUser3 = appModel.user.toString()
                prefs.appActivity3 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_4 -> {
                prefs.appName4 = appModel.appLabel
                prefs.appPackage4 = appModel.appPackage
                prefs.appUser4 = appModel.user.toString()
                prefs.appActivity4 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_5 -> {
                prefs.appName5 = appModel.appLabel
                prefs.appPackage5 = appModel.appPackage
                prefs.appUser5 = appModel.user.toString()
                prefs.appActivity5 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_6 -> {
                prefs.appName6 = appModel.appLabel
                prefs.appPackage6 = appModel.appPackage
                prefs.appUser6 = appModel.user.toString()
                prefs.appActivity6 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_7 -> {
                prefs.appName7 = appModel.appLabel
                prefs.appPackage7 = appModel.appPackage
                prefs.appUser7 = appModel.user.toString()
                prefs.appActivity7 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_HOME_APP_8 -> {
                prefs.appName8 = appModel.appLabel
                prefs.appPackage8 = appModel.appPackage
                prefs.appUser8 = appModel.user.toString()
                prefs.appActivity8 = appModel.appActivityName
                refreshHome(false)
            }
            Constants.FLAG_SET_SWIPE_LEFT_APP -> {
                prefs.appNameSwipeLeft = appModel.appLabel
                prefs.appPackageSwipeLeft = appModel.appPackage
                prefs.appUserSwipeLeft = appModel.user.toString()
                prefs.appActivitySwipeLeft = appModel.appActivityName
                updateSwipeApps()
            }
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> {
                prefs.appNameSwipeRight = appModel.appLabel
                prefs.appPackageSwipeRight = appModel.appPackage
                prefs.appUserSwipeRight = appModel.user.toString()
                prefs.appActivitySwipeRight = appModel.appActivityName
                updateSwipeApps()
            }
            Constants.FLAG_SET_CLICK_CLOCK_APP -> {
                prefs.appNameClickClock = appModel.appLabel
                prefs.appPackageClickClock = appModel.appPackage
                prefs.appUserClickClock = appModel.user.toString()
                prefs.appActivityClickClock = appModel.appActivityName
                updateClickApps()
            }
            Constants.FLAG_SET_CLICK_DATE_APP -> {
                prefs.appNameClickDate = appModel.appLabel
                prefs.appPackageClickDate = appModel.appPackage
                prefs.appUserClickDate = appModel.user.toString()
                prefs.appActivityClickDate = appModel.appActivityName
                updateClickApps()
            }
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.value = appCountUpdated
    }

    fun toggleDateTime(visibility: Boolean) {
        toggleDateTime.value = visibility
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    private fun updateClickApps() {
        updateClickApps.postValue(Unit)
    }

    private fun launchApp(appModel: AppModel) {
        val packageName = appModel.appPackage
        val appActivityName = appModel.appActivityName
        val userHandle = appModel.user
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        // TODO: Handle multiple launch activities in an app. This is NOT the way.
        val component = when (activityInfo.size) {
            0 -> {
                showToastShort(appContext, "App not found")
                return
            }
            1 -> ComponentName(packageName, activityInfo[0].name)
            else -> if (appActivityName.isNotEmpty()) {
                ComponentName(packageName, appActivityName)
            } else {
                ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                showToastShort(appContext, "Unable to launch app")
            }
        } catch (e: Exception) {
            showToastShort(appContext, "Unable to launch app")
        }
    }

    fun getAppList(showHiddenApps: Boolean = false) {
        viewModelScope.launch {
            appList.value = getAppsList(appContext, showHiddenApps)
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value = getHiddenAppsList(appContext)
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.value = isOlauncherDefault(appContext)
    }

    fun resetDefaultLauncherApp(context: Context) {
        resetDefaultLauncher(context)
        launcherResetFailed.value = getDefaultLauncherPackage(
            appContext
        ).contains(".")
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    fun showMessageDialog(message: String) {
        showMessageDialog.postValue(message)
    }

    fun showSupportDialog(value: Boolean) {
        showSupportDialog.postValue(value)
    }
}