package app.olauncher.helper

import java.text.Normalizer
import kotlin.math.max

object StringUtils {
    fun longestCommonSubsequence(a: CharSequence, b: CharSequence): Int {

        val n = a.length
        val m = b.length
        var v0 = MutableList<Int>(m + 1) { 0 };
        var v1 = MutableList<Int>(m + 1) { 0 };

        (1..n).forEach { i ->
            (1..m).forEach { j ->
                if (a[i - 1] == b[j - 1]) {
                    v1[j] = v0[j - 1] + 1;
                } else {
                    v1[j] = max(v1[j - 1], v0[j]);
                }
            }
            val swap = v0
            v0 = v1
            v1 = swap;
        }

        return v0[m];
    }

    fun normalizeStringNfd(s: String, trimWhitespace: Boolean): String {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-zA-Z0-9]"), " ").run {
                if (trimWhitespace) trimWhitespace()
                else this
            }
    }
}

fun String.normalizeNfd(trimWhitespace: Boolean = false) = StringUtils.normalizeStringNfd(this, trimWhitespace)
fun String.getAcronym() = this.split(" ").joinToString("") { it.firstOrNull()?.lowercase() ?: "" }
fun String.splitCamelCase(): String {
    return replace(Regex("([a-z])([A-Z])"), "$1 $2")
}
fun String.trimWhitespace() = replace(" ", "")