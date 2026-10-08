package xyz.felixb.dicebear.internal

import kotlin.math.floor

/**
 * Rounds half toward +∞, matching JavaScript's `Math.round`. Comparing the fractional part against
 * 0.5 (instead of `floor(x + 0.5)`) keeps the largest double below 0.5 from over-rounding.
 */
internal fun roundHalfUp(value: Double): Double {
    val f = floor(value)
    return if (value - f < 0.5) f else f + 1.0
}

/**
 * Formats a number for SVG output, rounded to at most 5 decimal places. Built from integer
 * arithmetic, so the result is identical on every platform (no float stringification involved).
 */
internal fun formatNumber(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value == Double.POSITIVE_INFINITY) return "Infinity"
    if (value == Double.NEGATIVE_INFINITY) return "-Infinity"

    var scaled = roundHalfUp(value * 100000.0).toLong()
    val sign = if (scaled < 0) "-" else ""
    if (scaled < 0) scaled = -scaled

    val integerPart = scaled / 100000
    val fraction = (scaled % 100000).toString().padStart(5, '0').trimEnd('0')

    return if (fraction.isEmpty()) "$sign$integerPart" else "$sign$integerPart.$fraction"
}

/**
 * Converts a number to the string JavaScript's `String(value)` produces (e.g. `400`, not `400.0`;
 * `1e-7`, not `1.0E-7`).
 *
 * The shortest round-trip digits come from the platform's `Double.toString()`; only the layout of
 * those digits is rebuilt here, following ECMAScript's Number::toString algorithm.
 */
internal fun jsNumberToString(value: Double): String {
    if (value == 0.0) return "0"
    if (value.isNaN()) return "NaN"
    if (value == Double.POSITIVE_INFINITY) return "Infinity"
    if (value == Double.NEGATIVE_INFINITY) return "-Infinity"

    val sign = if (value < 0) "-" else ""
    val raw = (if (value < 0) -value else value).toString()

    // Split "<int>.<frac>E<exp>" (any platform spelling) into a digit string and an exponent.
    val ePos = raw.indexOfFirst { it == 'E' || it == 'e' }
    val mantissa = if (ePos >= 0) raw.substring(0, ePos) else raw
    val exponent = if (ePos >= 0) raw.substring(ePos + 1).removePrefix("+").toInt() else 0
    val dot = mantissa.indexOf('.')
    val intPart = if (dot >= 0) mantissa.substring(0, dot) else mantissa
    val fracPart = if (dot >= 0) mantissa.substring(dot + 1) else ""

    var digits = intPart + fracPart
    // n: position of the decimal point relative to the start of `digits`.
    var n = intPart.length + exponent
    val leading = digits.indexOfFirst { it != '0' }
    digits = digits.substring(leading)
    n -= leading
    digits = digits.trimEnd('0')
    val k = digits.length

    val body = when {
        n in k..21 -> digits + "0".repeat(n - k)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            val expPart = if (e >= 0) "e+$e" else "e$e"
            (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + expPart
        }
    }

    return sign + body
}
