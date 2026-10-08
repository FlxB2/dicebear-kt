package xyz.felixb.dicebear.internal

private val AT_SUFFIX = Regex("@[\\s\\S]*")
private val APOSTROPHES = Regex("[`\u00B4'\u02BC]")
private val WORD = Regex("\\p{L}[\\p{L}\\p{M}]*")
private val ONE_OR_TWO_UNITS = Regex("^(?:\\p{L}\\p{M}*){1,2}")
private val ONE_UNIT = Regex("^(?:\\p{L}\\p{M}*)")

/**
 * Returns one or two uppercase initials for [seed]. By default strips `@…` so email addresses
 * yield a single initial instead of being treated as two words.
 */
internal fun initialsFromSeed(seed: String, discardAtSymbol: Boolean = true): String {
    var input = if (discardAtSymbol) AT_SUFFIX.replaceFirst(seed, "") else seed
    input = APOSTROPHES.replace(input, "")

    val matches = WORD.findAll(input).map { it.value }.toList()

    if (matches.isEmpty()) {
        return if (discardAtSymbol) initialsFromSeed(seed, false) else ""
    }

    if (matches.size == 1) {
        return ONE_OR_TWO_UNITS.find(matches[0])?.let { jsToUpperCase(it.value) } ?: ""
    }

    val first = ONE_UNIT.find(matches.first()) ?: return ""
    val last = ONE_UNIT.find(matches.last()) ?: return ""

    return jsToUpperCase(first.value + last.value)
}
