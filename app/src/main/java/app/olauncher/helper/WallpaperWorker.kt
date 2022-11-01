package app.olauncher.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val wallType = checkWallpaperType()
        val wallpaperUrl = getTodaysWallpaper(wallType)
        if (Prefs(applicationContext).dailyWallpaperUrl == wallpaperUrl) {
            Result.success()
        }
        val success = setWallpaper(
            applicationContext,
            wallpaperUrl
        )
        if (success) {
            Prefs(applicationContext).dailyWallpaperUrl = wallpaperUrl
            Result.success()
        } else Result.retry()
    }

    private fun checkWallpaperType(): String {
        return when (Prefs(applicationContext).appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> Constants.WALL_TYPE_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Constants.WALL_TYPE_LIGHT
            else -> if (applicationContext.isDarkThemeOn())
                Constants.WALL_TYPE_DARK
            else
                Constants.WALL_TYPE_LIGHT
        }
    }
}
