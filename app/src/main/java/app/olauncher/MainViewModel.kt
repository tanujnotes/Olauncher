package app.olauncher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val selectedApp = MutableLiveData<AppModelPosition>()

    fun selectedApp(app: AppModel, position: Int) {
        selectedApp.value = AppModelPosition(app, position)
    }
}

data class AppModelPosition(
    val appModel: AppModel,
    val position: Int
)