package xyz.felixb.dicebear.internal

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A numeric range with an optional quantization step. */
internal data class Range(val min: Double, val max: Double, val step: Double? = null)

// All 32-bit arithmetic below uses Int: Kotlin's Int `+`/`*` wrap mod 2^32 on every target
// (Kotlin/JS compiles `*` to `Math.imul`), exactly like the JS reference, while Long would be
// emulated in software on Kotlin/JS. Unsigned views are only produced at the edges.

private const val TWO_POW_32 = 4294967296.0

/** FNV-1a over the UTF-16 code units of [input] (matching JS `charCodeAt`), as raw 32 bits. */
internal fun fnv1a32(input: String): Int {
    var hash = 0x811c9dc5.toInt()
    for (c in input) {
        hash = (hash xor c.code) * 0x01000193
    }
    return hash
}

/** The unsigned 32-bit FNV-1a hash of [input]. */
internal fun fnv1aHash(input: String): Long = fnv1a32(input).toLong() and 0xFFFFFFFFL

/** The FNV-1a hash of [input] as an 8-character lowercase hex string. */
internal fun fnv1aHex(input: String): String {
    val hash = fnv1a32(input)
    val chars = CharArray(8) { i -> HEX_DIGITS[(hash ushr (28 - 4 * i)) and 0xF] }
    return chars.concatToString()
}

private const val HEX_DIGITS = "0123456789abcdef"

/** Mulberry32 PRNG (Tommy Ettinger's C reference) on wrapping 32-bit Int arithmetic. */
internal class Mulberry32(seed: Int) {
    constructor(seed: Long) : this(seed.toInt())

    private var state: Int = seed

    /** The next raw 32 bits (interpret as unsigned). */
    fun next(): Int {
        state += 0x6d2b79f5
        val z = state

        var t = (z xor (z ushr 15)) * (z or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))

        return t xor (t ushr 14)
    }

    /** Next value in `[0, 1)`; exact, since `unsigned(next()) / 2^32` is representable. */
    fun nextFloat(): Double {
        val v = next().toDouble()
        return (if (v < 0) v + TWO_POW_32 else v) / TWO_POW_32
    }

    /** The internal state as a signed 32-bit value (the JS `| 0` view). */
    fun state(): Int = state
}

/**
 * Key-based pseudorandom number generator: the same seed + key always yields the same value,
 * regardless of call order.
 */
internal class Prng(private val seed: String) {
    fun getValue(key: String): Double = Mulberry32(fnv1a32("$seed:$key")).nextFloat()

    /** Picks one item; duplicates (by JS string form) are collapsed and the rest sorted first. */
    fun <T : Any> pick(key: String, items: List<T>): T? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items[0]

        val unique = uniqueByJsString(items)
        if (unique.size == 1) return unique[0]

        val sorted = unique.sortedWith(JS_STRING_ORDER)
        return sorted[floor(getValue(key) * sorted.size).toInt()]
    }

    /** Picks a key proportional to its weight; all-zero weights fall back to [pick]. */
    fun weightedPick(key: String, weights: Map<String, Double>): String? {
        if (weights.isEmpty()) return null
        if (weights.size == 1) return weights.keys.first()

        val sorted = weights.keys.sorted()

        // Summed in sorted-key order: float addition is not associative.
        var total = 0.0
        for (k in sorted) total += weights.getValue(k)

        if (total == 0.0) return pick(key, sorted)

        val threshold = getValue(key) * total
        var cumulative = 0.0
        for (k in sorted) {
            cumulative += weights.getValue(k)
            if (threshold < cumulative) return k
        }

        return sorted.last()
    }

    /** `true` with the given probability (0–100). */
    fun bool(key: String, likelihood: Double = 50.0): Boolean = getValue(key) * 100 < likelihood

    /** A float in [range], rounded to four decimals; quantized when `range.step > 0`. */
    fun float(key: String, range: Range): Double {
        val lo = min(range.min, range.max)
        val hi = max(range.min, range.max)
        val step = range.step ?: 0.0

        val value = if (step > 0) {
            val buckets = floor((hi - lo) / step) + 1
            val i = floor(getValue(key) * buckets)
            lo + i * step
        } else {
            lo + getValue(key) * (hi - lo)
        }

        return roundHalfUp(value * 10000) / 10000
    }

    /** An integer in [range] (inclusive). */
    fun integer(key: String, range: Range): Int {
        val lo = min(range.min, range.max)
        val hi = max(range.min, range.max)
        return (floor(getValue(key) * (hi - lo + 1)) + lo).toInt()
    }

    /** Fisher-Yates shuffle with chained Mulberry32 state over the deduplicated, sorted items. */
    fun <T : Any> shuffle(key: String, items: List<T>): List<T> {
        if (items.size <= 1) return items.toList()

        val result = uniqueByJsString(items).sortedWith(JS_STRING_ORDER).toMutableList()
        val prng = Mulberry32(fnv1a32("$seed:$key"))

        for (i in result.size - 1 downTo 1) {
            val j = floor(prng.nextFloat() * (i + 1)).toInt()
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }

        return result
    }

    private companion object {
        val JS_STRING_ORDER = Comparator<Any> { a, b -> jsString(a).compareTo(jsString(b)) }

        fun jsString(item: Any): String = if (item is Number) jsNumberToString(item.toDouble()) else item.toString()

        fun <T : Any> uniqueByJsString(items: List<T>): List<T> {
            val seen = HashSet<String>()
            return items.filter { seen.add(jsString(it)) }
        }
    }
}
