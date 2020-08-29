package app.olauncher.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
            Prefs(applicationContext).dailyWallpaperUrl = wallpaperUrl
            Result.success()
        } else Result.retry()
    }
}
