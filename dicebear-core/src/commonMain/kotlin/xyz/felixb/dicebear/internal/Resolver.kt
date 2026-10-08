package xyz.felixb.dicebear.internal

import xyz.felixb.dicebear.CircularColorReferenceError
import xyz.felixb.dicebear.ColorDefinition
import xyz.felixb.dicebear.Component
import xyz.felixb.dicebear.ComponentVariant
import xyz.felixb.dicebear.Style

internal data class ComponentTransform(
    val rotate: Double,
    val translateX: Double,
    val translateY: Double,
    val scale: Double,
)

/**
 * Derives every deterministic value for an avatar from the style, the options and a seeded PRNG.
 * Each accessor memoizes its result; the memo (in first-resolution order) is also the
 * resolved-options snapshot exposed by the avatar. The seed itself never lands in it.
 */
internal class Resolver(private val style: Style, private val options: Options) {
    private val prng = Prng(options.seed() ?: "")
    private val colorResolving = mutableListOf<String>()
    private val result = LinkedHashMap<String, Any?>()

    private class TagFilter(
        val allowGroups: List<Pair<String, List<String>>>,
        val bares: Set<String>,
        val disallows: List<Pair<String, String?>>,
        val bareDisallows: Set<String>,
    )

    private val tagFilter: TagFilter by lazy {
        val allows = LinkedHashMap<String, MutableList<String>>()
        val bares = LinkedHashSet<String>()
        val disallows = mutableListOf<Pair<String, String?>>()
        val bareDisallows = HashSet<String>()

        for (token in options.tags) {
            when {
                token.negated -> {
                    disallows += token.category to token.value
                    if (token.value == null) bareDisallows += token.category
                }
                token.value != null -> allows.getOrPut(token.category) { mutableListOf() } += token.value
                else -> bares += token.category
            }
        }

        TagFilter(allows.map { it.key to it.value }, bares, disallows, bareDisallows)
    }

    fun seed(): String = options.seed() ?: ""

    fun size(): Double? = memo("size") { options.size() }

    fun idRandomization(): Boolean = memo("idRandomization") { options.idRandomization() ?: false }

    /** The global animation switch (never seed dependent). */
    fun animation(): Boolean = memo("animation") { options.animation() ?: false }

    fun animationPlays(name: String?): Boolean {
        val value = name?.let(options::animationFor)
        if (name == null || value == null) return animation()
        return memo("${name}Animation") { value }
    }

    fun animationSpeed(): Double = memoFloat("animationSpeed", options.animationSpeed(), 1.0)

    fun animationSpeedFor(name: String?): Double {
        val range = name?.let(options::animationSpeedFor)
        if (name == null || range == null) return animationSpeed()
        return memoFloat("${name}AnimationSpeed", range, 1.0)
    }

    fun animationDelay(): Double = memoFloat("animationDelay", options.animationDelay(), 0.0)

    fun animationDelayFor(name: String?): Double {
        val range = name?.let(options::animationDelayFor)
        if (name == null || range == null) return animationDelay()
        return memoFloat("${name}AnimationDelay", range, 0.0)
    }

    fun title(): String? = memo("title") { options.title() }

    fun flip(): String = memo("flip") { prng.pick("flip", options.flip()) ?: "none" }

    fun fontFamily(): String = memo("fontFamily") { prng.pick("fontFamily", options.fontFamily()) ?: "system-ui" }

    fun fontWeight(): Double = memo("fontWeight") { prng.pick("fontWeight", options.fontWeight()) ?: 400.0 }

    fun scale(): Double = memoFloat("scale", options.scale(), 1.0)

    fun borderRadius(): Double = memoFloat("borderRadius", options.borderRadius(), 0.0)

    fun rotate(): Double = memoFloat("rotate", options.rotate(), 0.0)

    fun translateX(): Double = memoFloat("translateX", options.translateX(), 0.0)

    fun translateY(): Double = memoFloat("translateY", options.translateY(), 0.0)

    /**
     * The chosen variant of a component, or `null` when unknown or rolled invisible. User options
     * are read via the source name (shared by aliases), PRNG keys use the element's own name.
     */
    fun variant(name: String): String? = memo("${name}Variant") {
        val component = style.components[name]
        if (component == null || !isVisible(name, component)) {
            null
        } else {
            prng.weightedPick("${name}Variant", variantWeights(component))
        }
    }

    /** `${name}Variant` takes precedence over the global `tags` filter; otherwise all variants. */
    private fun variantWeights(component: Component): Map<String, Double> {
        val variants = component.variants
        val named = options.componentVariant(component.sourceName)

        val names: Iterable<String> = when {
            named != null -> named.keys
            options.tags.isNotEmpty() -> tagFilteredNames(variants)
            else -> variants.keys
        }

        val weights = LinkedHashMap<String, Double>()
        for (name in names) {
            val variant = variants[name] ?: continue
            weights[name] = named?.getValue(name) ?: variant.weight
        }
        return weights
    }

    /** Applies the axis-scoped allows, bare requirements and disallows of the `tags` filter. */
    private fun tagFilteredNames(variants: Map<String, ComponentVariant>): List<String> {
        val filter = tagFilter

        // A bare token only binds where its category is in use on this component.
        val required = filter.bares.filter { category ->
            category !in filter.bareDisallows && variants.values.any { it.hasTag(category) }
        }

        return variants.filter { (_, variant) ->
            val allowed = filter.allowGroups.all { (category, values) ->
                !variant.hasTag(category) || values.any { variant.hasTag(category, it) }
            } && required.all { variant.hasTag(it) }
            val disallowed = filter.disallows.any { (category, value) -> variant.hasTag(category, value) }
            allowed && !disallowed
        }.keys.toList()
    }

    /** The final stop list of a named color (memoized — also guards against exponential re-resolution). */
    fun color(name: String): List<String> = memo("${name}Color") { resolveColor(name) }

    fun colorFill(name: String): String =
        memo("${name}ColorFill") { prng.pick("${name}ColorFill", options.colorFill(name)) ?: "solid" }

    /** Only drawn when a gradient is built, so it only lands in the snapshot for gradients. */
    fun colorAngle(name: String): Double = memoFloat("${name}ColorAngle", options.colorAngle(name), 0.0)

    /** Not memoized: no PRNG pick, so it stays out of the snapshot. */
    fun colorOrder(name: String): String = options.colorOrder(name) ?: COLOR_ORDER_RANDOM

    /** Memoized per component in Rotate, TranslateX, TranslateY, Scale order. */
    fun componentTransform(name: String): ComponentTransform {
        val component = style.components[name]
        return ComponentTransform(
            rotate = memoFloat("${name}Rotate", component?.rotate, 0.0),
            translateX = memoFloat("${name}TranslateX", component?.translateX, 0.0),
            translateY = memoFloat("${name}TranslateY", component?.translateY, 0.0),
            scale = memoFloat("${name}Scale", component?.scale, 1.0),
        )
    }

    /** The snapshot of every resolved value, `null` meaning unset. Aliases the internal memo. */
    fun resolved(): Map<String, Any?> = result

    private fun isVisible(name: String, component: Component): Boolean =
        prng.bool("${name}Probability", options.componentProbability(component.sourceName) ?: component.probability)

    private fun resolveColor(name: String): List<String> {
        val userColors = options.color(name)
        val styleColor = style.colors[name]
        val source = userColors ?: styleColor?.values ?: emptyList()

        var candidates = source.map(Color::toHex)
        val fixed = colorOrder(name) == COLOR_ORDER_FIXED

        // colorFill is memoized inside this computation, so it precedes `${name}Color` in the snapshot.
        val fill = colorFill(name)
        val stops = if (fill == "solid") 1 else colorFillStops(name, if (fixed) candidates.size else 2)

        if (styleColor == null) return takeN(order(name, candidates, fixed), stops)

        if (name in colorResolving) throw CircularColorReferenceError(colorResolving + name)

        colorResolving += name
        val contrastTo = contrastTo(styleColor)

        try {
            if (contrastTo != null && !fixed) {
                val refColors = color(contrastTo)
                if (refColors.isNotEmpty()) candidates = Color.sortByContrast(candidates, refColors[0])
            }

            if (styleColor.notEqualTo.isNotEmpty()) {
                val excluded = styleColor.notEqualTo.flatMap { color(it) }
                candidates = Color.filterNotEqualTo(candidates, excluded)
            }
        } finally {
            colorResolving.removeAt(colorResolving.size - 1)
        }

        // Keep the contrast order instead of shuffling.
        val ordered = if (contrastTo != null) candidates else order(name, candidates, fixed)
        return takeN(ordered, stops)
    }

    private fun order(name: String, candidates: List<String>, fixed: Boolean): List<String> =
        if (fixed) candidates else prng.shuffle("${name}Color", candidates)

    private fun contrastTo(styleColor: ColorDefinition): String? = styleColor.contrastTo?.takeIf { it.isNotEmpty() }

    /** Not memoized: `${name}ColorFillStops` never appears in the snapshot. */
    private fun colorFillStops(name: String, fallback: Int): Int =
        options.colorFillStops(name)?.let { prng.integer("${name}ColorFillStops", it) } ?: fallback

    private fun memoFloat(key: String, range: Range?, fallback: Double): Double =
        memo(key) { if (range != null) prng.float(key, range) else fallback }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> memo(key: String, compute: () -> T): T {
        if (result.containsKey(key)) return result[key] as T
        val value = compute()
        result[key] = value
        return value
    }

    private fun takeN(list: List<String>, n: Int): List<String> = list.subList(0, n.coerceIn(0, list.size)).toList()
}
