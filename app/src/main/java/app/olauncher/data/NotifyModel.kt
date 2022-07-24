package app.olauncher.data

data class NotifyModel(
    val id: Int,
    val dateShow: String, // yyyymmdd
    val dateExpiry: String, // yyyymmdd
    val minVersion: Int = 0,
    val maxVersion: Int = 0,
    val title: String,
    val message: String,
    val positiveButton: NotifyButton,
    val negativeButton: NotifyButton,
    val neutralButton: NotifyButton
)

data class NotifyButton(
    val text: String,
    val type: String,
    val url: String
)