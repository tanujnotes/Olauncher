package app.olauncher.helper

/** Ignore common phone formatting, but never turn a mixed name query into digits. */
fun contactPhoneQuery(query: String): String =
    if (query.all { it.isDigit() || it.isWhitespace() || it in "+()-./" }) {
        query.filter(Char::isDigit)
    } else ""

fun contactPhoneMatches(digits: String, display: String, normalized: String?): Boolean =
    digits.isNotEmpty() && (display.filter(Char::isDigit).contains(digits) ||
        normalized?.filter(Char::isDigit)?.contains(digits) == true)
