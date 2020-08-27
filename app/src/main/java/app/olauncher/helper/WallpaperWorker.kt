package app.olauncher.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {

        val wallpaperUrl = getTodaysWallpaper()

        val success = setWallpaper(
            applicationContext,
            wallpaperUrl
        )

        if (success) Result.success()
        else Result.failure()
    }
}
