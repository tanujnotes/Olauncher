package app.olaunchercf

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.olaunchercf.data.AppModel
import app.olaunchercf.data.Constants
import app.olaunchercf.data.Prefs
import app.olaunchercf.helper.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val timeVisible = MutableLiveData<Boolean>()
    val dateVisible = MutableLiveData<Boolean>()
    val updateSwipeApps = MutableLiveData<Any>()
    val updateClickApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Constants.Gravity>()
    val timeAlignment = MutableLiveData<Constants.Gravity>()
    val showMessageDialog = MutableLiveData<String>()
    val showSupportDialog = MutableLiveData<Boolean>()

    fun selectedApp(appModel: AppModel, flag: Int, n: Int = 0) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                launchApp(appModel)
            }
            Constants.FLAG_HIDDEN_APPS -> {
                launchApp(appModel)
            }
            Constants.FLAG_SET_HOME_APP -> {
                Log.d("homeapps", "$n")
                appModel.let {
                    prefs.setHomeAppValues(n, it.appLabel, it.appPackage, it.user.toString(), it.appActivityName)
                }
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

    fun setShowDate(visibility: Boolean) {
        dateVisible.value = visibility
    }

    fun setShowTime(visibility: Boolean) {
        timeVisible.value = visibility
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

    fun updateHomeAlignment(gravity: Constants.Gravity) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = gravity
    }

    fun updateDrawerAlignment(gravity: Constants.Gravity) {
        prefs.drawerAlignment = gravity
        // drawerAppAlignment.value = gravity
    }

    fun updateTimeAlignment(gravity: Constants.Gravity) {
        prefs.timeAlignment = gravity
        timeAlignment.value = gravity
    }

    fun showMessageDialog(message: String) {
        showMessageDialog.postValue(message)
    }

    fun showSupportDialog(value: Boolean) {
        showSupportDialog.postValue(value)
    }
}
