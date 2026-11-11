package app.olauncher.helper

import java.text.Normalizer

private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

/**
 * True if [appLabel] matches [query] for the app-drawer search: either a plain
 * case-insensitive substring match, or a match once both sides are normalised
 * (diacritics stripped, separator characters removed) — e.g. "cafe" matches
 * "Café", and "googlemaps" matches "Google Maps".
 */
fun appLabelMatches(appLabel: String, query: CharSequence): Boolean =
    appLabelMatcher(query)(appLabel)

internal fun appLabelMatcher(query: CharSequence): (String) -> Boolean {
    val trimmedQuery = query.trim().toString()
    val normalizedQuery = trimmedQuery.normalizeForSearch()
    return { label ->
        label.contains(trimmedQuery, ignoreCase = true) ||
            (normalizedQuery.isNotEmpty() &&
                label.normalizeForSearch().contains(normalizedQuery, ignoreCase = true))
    }
}

private fun CharSequence.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(diacriticsRegex, "")
        .replace(separatorsRegex, "")

/** Best match first; the drawer places adapter position zero at the bottom. */
internal fun appLabelRanking(query: CharSequence): Comparator<String> {
    val plainQuery = query.trim().toString()
    val normalizedQuery = plainQuery.normalizeForSearch()
    fun rank(label: String): Int {
        val normalizedLabel = label.normalizeForSearch()
        return when {
            label.equals(plainQuery, ignoreCase = true) ||
                (normalizedQuery.isNotEmpty() && normalizedLabel.equals(normalizedQuery, ignoreCase = true)) -> 0
            label.startsWith(plainQuery, ignoreCase = true) ||
                (normalizedQuery.isNotEmpty() && normalizedLabel.startsWith(normalizedQuery, ignoreCase = true)) -> 1
            label.split(separatorsRegex).any { word ->
                word.startsWith(plainQuery, ignoreCase = true) ||
                    (normalizedQuery.isNotEmpty() && word.normalizeForSearch().startsWith(normalizedQuery, ignoreCase = true))
            } -> 2
            else -> 3
        }
    }
    // Preserve the source's alphabetical ordering for equally strong matches.
    return compareBy(::rank)
}
