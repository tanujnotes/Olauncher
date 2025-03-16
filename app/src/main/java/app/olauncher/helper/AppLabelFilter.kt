package app.olauncher.helper

import android.util.Log
import app.olauncher.data.AppModel
import app.olauncher.helper.StringUtils.longestCommonSubsequence
import org.simmetrics.StringMetric
import java.util.TreeSet

private const val TAG = "AppFilter"

class AppLabelFilter(private val appsList: List<AppModel>, private val matcher: StringMetric) {

    private val filteredApps = TreeSet<AppScore>()

    fun filterApps(query: String): List<AppModel> {
        val searchString = query.normalizeNfd().lowercase()
        filteredApps.clear()

        appsList.mapNotNullTo(filteredApps) { app ->
            val normalized = app.appLabel.normalizeNfd().splitCamelCase().lowercase()
            if (appLabelMatches(searchString, normalized)) {
                val score = criteria(searchString, normalized)
                AppScore(app, score)
            } else null
        }

        return filteredApps.map(AppScore::app)
    }

    private fun appLabelMatches(charSearch: String, appLabel: String): Boolean =
        when (matchAcronym(charSearch, appLabel)) {
            AcronymMatch.FULL -> true
            AcronymMatch.PARTIAL -> longestCommonSubsequence(charSearch, appLabel) == charSearch.length
            else -> appLabel.contains(charSearch)
        }

    private fun criteria(searchString: String, label: String): Float {
        val rawScore = matcher.compare(searchString, label)
        val acronymBonus = computeAcronymBonus(searchString, label, rawScore)
        val subStringBonus = computeSubStringBonus(searchString, label)
        val score = rawScore + acronymBonus + subStringBonus
        Log.d(TAG, "Matching Score[$searchString, $label]: $score ($rawScore, Acronym Bonus: $acronymBonus, Substring Bonus: $subStringBonus)")
        return score
    }

    fun computeAcronymBonus(searchString: String, label: String, rawScore: Float): Float {
        val acronymMatch: AcronymMatch = if (searchString.length > 1) matchAcronym(searchString, label) else AcronymMatch.NONE
        return when (acronymMatch) {
            AcronymMatch.FULL -> 1f
            AcronymMatch.PARTIAL -> {
                val multiWordScore = matcher.compare(searchString, label.getAcronym())
                val missisng = 1 - rawScore
                missisng * multiWordScore
            }
            else -> 0f
        }
    }

    fun computeSubStringBonus(searchString: String, label: String): Float {
        val words = label.split(" ")
        var matchedWords = words.fold(0) { acc, word ->
            if (searchString.contains(word)) acc + 1 else acc
        }
        return if (matchedWords > 0)
            matchedWords.toFloat() / words.size.toFloat()
        else if (label.contains(searchString)) {
            (searchString.length.toFloat() / label.length.toFloat())
        } else 0f
    }

    private fun matchAcronym(searchString: String, label: String): AcronymMatch {
        if (label.contains(" ").not()) return AcronymMatch.NONE
        val acronym = label.getAcronym()
        val lcs = longestCommonSubsequence(searchString, acronym)
        return when (2*lcs) {
            0, 2 -> AcronymMatch.NONE
            searchString.length + acronym.length -> AcronymMatch.FULL
            else -> AcronymMatch.PARTIAL
        }
    }

    private data class AppScore(val app: AppModel, val score: Float) : Comparable<AppScore> {
        override fun compareTo(other: AppScore): Int =
            compareValuesBy(other, this, // swapping other and this makes it descending
                { it.score },
                { it.app.appLabel },
                { it.app }
            )
    }

    private enum class AcronymMatch {
        NONE, PARTIAL, FULL
    }
}