package xyz.felixb.dicebear.internal

internal const val COLOR_ORDER_RANDOM = "random"
internal const val COLOR_ORDER_FIXED = "fixed"

/** A parsed `tags` filter token (`category`, `category:value`, optionally `!`-negated). */
internal data class TagFilterToken(val category: String, val value: String?, val negated: Boolean)

/**
 * Validated user options with normalized accessors (scalar-or-list options always come back as
 * lists, range options as [Range]). A JSON `null` behaves like an absent key.
 */
internal class Options(data: Map<String, Any?>) {
    private val data: Map<String, Any?> = run {
        SchemaValidator.validateOptions(data)
        deepCopyJsonMap(data)
    }

    fun seed(): String? = data["seed"] as? String
    fun size(): Double? = (data["size"] as? Number)?.toDouble()
    fun idRandomization(): Boolean? = data["idRandomization"] as? Boolean
    fun title(): String? = data["title"] as? String
    fun flip(): List<String> = stringList(data["flip"])
    fun fontFamily(): List<String> = stringList(data["fontFamily"])
    fun fontWeight(): List<Double> = numberList(data["fontWeight"])
    fun scale(): Range? = range(data["scale"])
    fun borderRadius(): Range? = range(data["borderRadius"])
    fun rotate(): Range? = range(data["rotate"])
    fun translateX(): Range? = range(data["translateX"])
    fun translateY(): Range? = range(data["translateY"])
    fun animation(): Boolean? = data["animation"] as? Boolean
    fun animationFor(name: String): Boolean? = data["${name}Animation"] as? Boolean
    fun animationSpeed(): Range? = range(data["animationSpeed"])
    fun animationSpeedFor(name: String): Range? = range(data["${name}AnimationSpeed"])
    fun animationDelay(): Range? = range(data["animationDelay"])
    fun animationDelayFor(name: String): Range? = range(data["${name}AnimationDelay"])

    val tags: List<TagFilterToken> by lazy { stringList(data["tags"]).map(::parseTagToken) }

    /** `${name}Variant` as a weight map (strings/lists weigh 1), or `null` when unset. */
    fun componentVariant(name: String): Map<String, Double>? = when (val raw = data["${name}Variant"]) {
        is String -> mapOf(raw to 1.0)
        is List<*> -> LinkedHashMap<String, Double>().also { m -> raw.filterIsInstance<String>().forEach { m[it] = 1.0 } }
        is Map<*, *> -> LinkedHashMap<String, Double>().also { m ->
            for ((k, v) in raw) if (v is Number) m[k as String] = v.toDouble()
        }
        else -> null
    }

    fun componentProbability(name: String): Double? = (data["${name}Probability"] as? Number)?.toDouble()

    /** `null` (not empty) when unset, so the resolver falls back to the style palette. */
    fun color(name: String): List<String>? = data["${name}Color"]?.let(::stringList)
    fun colorFill(name: String): List<String> = stringList(data["${name}ColorFill"])
    fun colorAngle(name: String): Range? = range(data["${name}ColorAngle"])
    fun colorFillStops(name: String): Range? = range(data["${name}ColorFillStops"])
    fun colorOrder(name: String): String? = data["${name}ColorOrder"] as? String

    private companion object {
        fun parseTagToken(token: String): TagFilterToken {
            val negated = token.startsWith("!")
            val body = if (negated) token.substring(1) else token
            val sep = body.indexOf(':')
            return if (sep == -1) {
                TagFilterToken(body, null, negated)
            } else {
                TagFilterToken(body.substring(0, sep), body.substring(sep + 1), negated)
            }
        }

        fun stringList(value: Any?): List<String> = when (value) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> listOf(value)
            else -> emptyList()
        }

        fun numberList(value: Any?): List<Double> = when (value) {
            is List<*> -> value.filterIsInstance<Number>().map { it.toDouble() }
            is Number -> listOf(value.toDouble())
            else -> emptyList()
        }

        /** Bare number or `[n]` → fixed; list → min/max of its numbers; `[]`/unset → `null`. */
        fun range(value: Any?): Range? = when (value) {
            is Number -> value.toDouble().let { Range(it, it) }
            is List<*> -> {
                val numbers = value.filterIsInstance<Number>().map { it.toDouble() }
                if (numbers.isEmpty()) null else Range(numbers.min(), numbers.max())
            }
            else -> null
        }
    }
}
