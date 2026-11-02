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
fun appLabelMatches(appLabel: String, query: CharSequence): Boolean {
    if (appLabel.contains(query.trim(), true)) return true
    val normalizedQuery = query.normalizeForSearch()
    return normalizedQuery.isNotEmpty() && appLabel.normalizeForSearch().contains(normalizedQuery, true)
}

private fun CharSequence.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(diacriticsRegex, "")
        .replace(separatorsRegex, "")
