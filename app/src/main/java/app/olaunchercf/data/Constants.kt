package app.olaunchercf.data

import java.util.*

object Constants {

    const val FLAG_LAUNCH_APP = 100
    const val FLAG_HIDDEN_APPS = 101

    const val FLAG_SET_HOME_APP = 1

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12

    const val FLAG_SET_CLICK_CLOCK_APP = 13
    const val FLAG_SET_CLICK_DATE_APP = 14

    const val REQUEST_CODE_ENABLE_ADMIN = 666

    const val TRIPLE_TAP_DELAY_MS = 300
    const val LONG_PRESS_DELAY_MS = 500

    const val MAX_HOME_APPS = 15
    const val TEXT_SIZE_MIN = 16
    const val TEXT_SIZE_MAX = 30

    enum class Language {
        System,
        English,
        Deutsch,
        Spanish,
        French,
        Italian,
        Swedish,
        Turkish,
        Greek,
        Chinese
    }

    fun Language.value(): String {
        return when(this) {
            Language.System -> Locale(Locale.getDefault().language).toString()
            Language.English -> "en"
            Language.Deutsch -> "de"
            Language.Spanish -> "es"
            Language.French -> "fr"
            Language.Italian -> "it"
            Language.Swedish -> "se"
            Language.Turkish -> "tr"
            Language.Greek -> "gr"
            Language.Chinese -> "cn"
        }
    }

    enum class Gravity {
        Left,
        Center,
        Right
    }

    enum class Theme {
        System,
        Dark,
        Light
    }
}
