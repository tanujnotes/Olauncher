package app.olauncher.helper

import android.content.Context
import android.content.res.Resources
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {

        val width = Resources.getSystem().displayMetrics.widthPixels
        val height = Resources.getSystem().displayMetrics.heightPixels

        val wallpaperUrl = getTodaysWallpaper()

        val success = setWallpaper(
            applicationContext,
            wallpaperUrl,
            width,
            height
        )

        if (success) Result.success()
        else Result.failure()
    }
}
