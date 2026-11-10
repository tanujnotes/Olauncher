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
