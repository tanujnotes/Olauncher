package app.olauncher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val selectedApp = MutableLiveData<AppModelWithFlag>()

    fun selectedApp(app: AppModel, flag: Int) {
        selectedApp.value = AppModelWithFlag(app, flag)
    }
}

data class AppModelWithFlag(
    val appModel: AppModel,
    val flag: Int
)