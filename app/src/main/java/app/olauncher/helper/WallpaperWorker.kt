package app.olauncher.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.*

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (date == Prefs(applicationContext).wallpaperUpdatedDay)
            Result.success()
        val wallType = checkWallpaperType()
        val wallpaperUrl = getTodaysWallpaper(wallType)
        val success = setWallpaper(
            applicationContext,
            wallpaperUrl
        )
        if (success) {
            updateWallpaperPrefs(wallpaperUrl, date)
            Result.success()
        } else Result.retry()
    }

    private fun updateWallpaperPrefs(url: String, date: String) {
        Prefs(applicationContext).dailyWallpaperUrl = url
        Prefs(applicationContext).wallpaperUpdatedDay = date
    }

    private fun checkWallpaperType(): String {
        return when (Prefs(applicationContext).appTheme) {
            Constants.THEME_MODE_DARK -> Constants.WALL_TYPE_DARK
            Constants.THEME_MODE_LIGHT -> Constants.WALL_TYPE_LIGHT
            else -> if (applicationContext.isDarkThemeOn())
                Constants.WALL_TYPE_DARK
            else
                Constants.WALL_TYPE_LIGHT
        }
    }
}
