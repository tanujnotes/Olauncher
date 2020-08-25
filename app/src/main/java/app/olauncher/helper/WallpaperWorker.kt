package app.olauncher.helper

import android.content.Context
import android.content.res.Resources
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {

        val width = Resources.getSystem().displayMetrics.widthPixels
        val height = Resources.getSystem().displayMetrics.heightPixels

        val wallpapers = listOf(
            "https://images.unsplash.com/photo-1506855147293-ca1ba11a1ac5",
            "https://images.unsplash.com/photo-1506732230727-85fbfb4999c3",
            "https://images.unsplash.com/photo-1506773090264-ac0b07293a64",
            "https://images.unsplash.com/photo-1506475097213-5555f7fc9b66",
            "https://images.unsplash.com/photo-1506296933720-1a0ce9bed41d",
            "https://images.unsplash.com/photo-1498081959737-f3ba1af08103",
            "https://images.unsplash.com/photo-1504964306813-50d4333f6968",
            "https://images.unsplash.com/photo-1505027593521-2436e2dbe299",
            "https://images.unsplash.com/photo-1515825838458-f2a94b20105a",
            "https://images.unsplash.com/photo-1504908276526-7fe3d7def9d7",
            "https://images.unsplash.com/photo-1504883303951-581cbf120aa4",
            "https://images.unsplash.com/photo-1504972516657-526efa0347c9",
            "https://images.unsplash.com/photo-1504198580308-d186fefc3fbb",
            "https://images.unsplash.com/photo-1504466334719-af4ae9f12ad0",
            "https://images.unsplash.com/photo-1495344517868-8ebaf0a2044a",
            "https://images.unsplash.com/photo-1576405515541-cb47b7da4fa7"
        )


        val success = setWallpaper(
            applicationContext,
            wallpapers[Random.nextInt(0, wallpapers.size)],
            width,
            height
        )

        if (success) Result.success()
        else Result.failure()
    }
}
