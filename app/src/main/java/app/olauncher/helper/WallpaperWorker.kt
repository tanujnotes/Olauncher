package app.olauncher.helper

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {

        val wallpaperUrl = getTodaysWallpaper()

        val success = setWallpaper(
            applicationContext,
            wallpaperUrl
        )

        if (success) {
            sendWallpaperBroadcast(wallpaperUrl)
            Result.success()
        } else Result.retry()
    }

    private fun sendWallpaperBroadcast(url: String) {
        Prefs(applicationContext).dailyWallpaperUrl = url
        Prefs(applicationContext).darkModeOn = true

        val intent = Intent()
        intent.action = Constants.ACTION_WALLPAPER_CHANGED
        applicationContext.sendBroadcast(intent)
    }
}
