package app.olaunchercf.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.ui.res.stringResource
import app.olaunchercf.R
import app.olaunchercf.data.Constants.toString
import java.util.*

interface EnumOption {
    @Composable
    fun string(): String
}

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

    enum class Language: EnumOption {
        System,
        Chinese,
        English,
        French,
        German,
        Greek,
        Italian,
        Korean,
        Persian,
        Portuguese,
        Spanish,
        Swedish,
        Turkish;

        @Composable
        override fun string(): String {
            return when(this) {
                System -> stringResource(R.string.lang_system)
                Chinese -> "中国人"
                English -> "English"
                French -> "Français"
                German -> "Deutsch"
                Greek -> "Ελληνική"
                Italian -> "Italiano"
                Korean -> "조선말"
                Persian -> "فارسی"
                Portuguese -> "Português"
                Spanish -> "Español"
                Swedish -> "Svenska"
                Turkish -> "Türkçe"
            }
        }
    }

    fun Language.value(): String {
        return when(this) {
            Language.System -> Locale(Locale.getDefault().language).toString()
            Language.English -> "en"
            Language.German -> "de"
            Language.Spanish -> "es"
            Language.French -> "fr"
            Language.Italian -> "it"
            Language.Swedish -> "se"
            Language.Turkish -> "tr"
            Language.Greek -> "gr"
            Language.Chinese -> "cn"
            Language.Persian -> "fa"
            Language.Portuguese -> "pt"
            Language.Korean -> "ko"
        }
    }

    enum class Gravity: EnumOption {
        Left,
        Center,
        Right;

        @Composable
        override fun string(): String {
            return when(this) {
                Left -> stringResource(R.string.left)
                Center -> stringResource(R.string.center)
                Right -> stringResource(R.string.right)
            }
        }
    }

    enum class Theme: EnumOption {
        System,
        Dark,
        Light;

        @Composable
        override fun string(): String {
            return when(this) {
                System -> stringResource(R.string.lang_system)
                Dark -> stringResource(R.string.dark)
                Light -> stringResource(R.string.light)
            }
        }
    }
}
