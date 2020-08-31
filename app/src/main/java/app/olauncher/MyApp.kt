package app.olauncher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.data.Prefs

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Prefs(this).darkModeOn)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}