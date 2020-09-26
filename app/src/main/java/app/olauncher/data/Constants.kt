package app.olauncher.data

object Constants {
    const val FLAG_LAUNCH_APP = 100

    const val FLAG_SET_HOME_APP_1 = 1
    const val FLAG_SET_HOME_APP_2 = 2
    const val FLAG_SET_HOME_APP_3 = 3
    const val FLAG_SET_HOME_APP_4 = 4
    const val FLAG_SET_HOME_APP_5 = 5
    const val FLAG_SET_HOME_APP_6 = 6
    const val FLAG_SET_HOME_APP_7 = 7
    const val FLAG_SET_HOME_APP_8 = 8

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12

    const val HOME_APPS_NUM_MAX = 8 // Max number of apps on home screen. Hopefully we'll never increase it ever again.

    const val REQUEST_CODE_ENABLE_ADMIN = 666

    const val ACTION_WALLPAPER_CHANGED = "app.olauncher.WALLPAPER_CHANGED"

    const val URL_OLAUNCHER_PRIVACY = "https://olauncher.flycricket.io/privacy.html"
    const val URL_OLAUNCHER_PLAY_STORE = "https://play.google.com/store/apps/details?id=app.olauncher"
    const val URL_TWITTER_TANUJNOTES = "https://twitter.com/tanujnotes/"
    const val URL_GITHUB_TANUJNOTES = "https://github.com/tanujnotes/"

    const val WALLPAPER_WORKER_NAME = "WALLPAPER_WORKER_NAME"
    const val URL_DARK_WALLPAPERS = "https://gist.githubusercontent.com/tanujnotes/481074b27ad4dc2607326c97439bb3ac/raw"
    const val URL_DEFAULT_WALLPAPER = "https://images.unsplash.com/photo-1512551980832-13df02babc9e"
}