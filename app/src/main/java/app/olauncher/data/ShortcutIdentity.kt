package app.olauncher.data

fun shortcutIdentity(packageName: String, shortcutId: String, user: String): String =
    "shortcut:${packageName.length}:$packageName${shortcutId.length}:$shortcutId${user.length}:$user"
