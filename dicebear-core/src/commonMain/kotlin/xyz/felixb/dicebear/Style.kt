package xyz.felixb.dicebear

import xyz.felixb.dicebear.internal.Json
import xyz.felixb.dicebear.internal.Range
import xyz.felixb.dicebear.internal.SchemaValidator
import xyz.felixb.dicebear.internal.deepCopyJsonMap
import xyz.felixb.dicebear.internal.normalizeJsonValue

/**
 * A validated DiceBear style definition (the JSON format described at
 * https://www.dicebear.com/create-styles/definition-schema/).
 *
 * Build it once and reuse it for many avatars. Construction validates the definition against the
 * DiceBear JSON Schema and throws a [StyleValidationError] when it is invalid.
 *
 * ```kotlin
 * val style = Style.parse(jsonString)
 * val avatar = Avatar(style, mapOf("seed" to "Felix"))
 * ```
 */
public class Style private constructor(validated: Validated) {
    private val data: Map<String, Any?> = validated.data

    /**
     * Creates a style from an already decoded definition (maps, lists, strings, numbers,
     * booleans). The input is copied, so later mutation cannot affect this style.
     */
    @Throws(IllegalArgumentException::class)
    public constructor(definition: Map<String, Any?>) : this(validated(normalizeJsonValue(definition)))

    public companion object {
        /**
         * Parses and validates a style definition from its JSON text.
         *
         * @throws IllegalArgumentException for malformed JSON, [StyleValidationError] for an
         * invalid definition.
         */
        @Throws(IllegalArgumentException::class)
        public fun parse(json: String): Style = Style(validated(Json.parse(json)))

        @Suppress("UNCHECKED_CAST")
        private fun validated(definition: Any?): Validated {
            SchemaValidator.validateStyle(definition)
            return Validated(deepCopyJsonMap(definition as Map<String, Any?>))
        }
    }

    private class Validated(val data: Map<String, Any?>)

    init {
        validateAliases()
        validateAnimations()
    }

    /** The definition's `$id`, if set. */
    public val id: String? get() = data["\$id"] as? String

    /** The definition's `$schema`, if set. */
    public val schema: String? get() = data["\$schema"] as? String

    /** The definition's `$comment`, if set. */
    public val comment: String? get() = data["\$comment"] as? String

    /** Attribution metadata (source, creator, license). */
    public val meta: StyleMeta by lazy { StyleMeta(obj(data["meta"])) }

    /** A deep copy of the underlying definition. */
    public fun definition(): Map<String, Any?> = deepCopyJsonMap(data)

    /** The definition as JSON text. */
    public fun toJson(): String = Json.stringify(data)

    internal val canvas: Canvas by lazy { Canvas(obj(data["canvas"])) }

    /** Name → component; non-alias entries first, then aliases (order is observable). */
    internal val components: Map<String, Component> by lazy {
        val entries = obj(data["components"])
        val map = LinkedHashMap<String, Component>()
        for ((name, raw) in entries) {
            val d = obj(raw)
            if (!isAlias(d)) map[name] = Component(name, null, ComponentData(d))
        }
        for ((name, raw) in entries) {
            val d = obj(raw)
            if (isAlias(d)) {
                val target = d["extends"] as String
                val source = map[target]
                if (source != null) map[name] = Component(name, target, source.data)
            }
        }
        map
    }

    internal val colors: Map<String, ColorDefinition> by lazy {
        obj(data["colors"]).mapValuesTo(LinkedHashMap()) { ColorDefinition(obj(it.value)) }
    }

    internal fun attributes(): Map<String, Any?> = deepCopyJsonMap(obj(data["attributes"]))

    internal val hasAnimations: Boolean by lazy {
        var found = false
        visitElements { element, _ -> if (list(element["animations"]).isNotEmpty()) found = true }
        found
    }

    /** Sorted distinct names of the definition's animation timelines. */
    internal val animationNames: List<String> by lazy {
        val names = LinkedHashSet<String>()
        visitElements { element, _ ->
            for (animation in list(element["animations"])) {
                (obj(animation)["name"] as? String)?.let(names::add)
            }
        }
        names.sorted()
    }

    private fun validateAliases() {
        val components = data["components"] as? Map<*, *> ?: return
        val errors = mutableListOf<ValidationErrorDetail>()

        for ((name, raw) in components) {
            val d = obj(raw)
            if (!isAlias(d)) continue

            val target = d["extends"] as String
            val targetData = components[target]

            if (targetData == null) {
                errors += ValidationErrorDetail("/components/$name/extends", "references unknown component \"$target\"")
            } else if (isAlias(obj(targetData))) {
                errors += ValidationErrorDetail(
                    "/components/$name/extends",
                    "references alias \"$target\" — alias chains are not allowed",
                )
            }
        }

        if (errors.isNotEmpty()) throw StyleValidationError(errors)
    }

    private fun validateAnimations() {
        val errors = mutableListOf<ValidationErrorDetail>()

        visitElements { element, path ->
            list(element["animations"]).forEachIndexed { animationIndex, animation ->
                for ((trackName, track) in obj(obj(animation)["tracks"])) {
                    val keyframes = list(obj(track)["keyframes"])
                    for (i in 1 until keyframes.size) {
                        val at = (obj(keyframes[i])["at"] as Number).toDouble()
                        val previous = (obj(keyframes[i - 1])["at"] as Number).toDouble()
                        if (at <= previous) {
                            errors += ValidationErrorDetail(
                                "$path/animations/$animationIndex/tracks/$trackName/keyframes/$i/at",
                                "must be greater than the previous keyframe",
                            )
                        }
                    }
                }
            }
        }

        if (errors.isNotEmpty()) throw StyleValidationError(errors)
    }

    /** Walks the canvas tree and every non-alias variant tree. */
    private fun visitElements(visit: (Map<String, Any?>, String) -> Unit) {
        fun walk(elements: List<Any?>, path: String) {
            elements.forEachIndexed { index, raw ->
                val element = obj(raw)
                val elementPath = "$path/$index"
                visit(element, elementPath)
                (element["children"] as? List<*>)?.let { walk(it, "$elementPath/children") }
            }
        }

        walk(list(obj(data["canvas"])["elements"]), "/canvas/elements")

        for ((name, raw) in obj(data["components"])) {
            val component = obj(raw)
            if (isAlias(component)) continue
            for ((variantName, variant) in obj(component["variants"])) {
                walk(list(obj(variant)["elements"]), "/components/$name/variants/$variantName/elements")
            }
        }
    }

    private fun isAlias(d: Map<String, Any?>) = d.containsKey("extends")
}

/**
 * Attribution metadata of a style. Fields are `null` when absent (an empty string is kept as-is,
 * which matters for the generated license text).
 */
public class StyleMeta internal constructor(data: Map<String, Any?>) {
    private val source = obj(data["source"])
    private val creator = obj(data["creator"])
    private val license = obj(data["license"])

    public val sourceName: String? get() = source["name"] as? String
    public val sourceUrl: String? get() = source["url"] as? String
    public val creatorName: String? get() = creator["name"] as? String
    public val creatorUrl: String? get() = creator["url"] as? String
    public val licenseName: String? get() = license["name"] as? String
    public val licenseUrl: String? get() = license["url"] as? String
    public val licenseText: String? get() = license["text"] as? String
}

internal class Canvas(data: Map<String, Any?>) {
    val width: Double = (data["width"] as Number).toDouble()
    val height: Double = (data["height"] as Number).toDouble()
    val elements: List<ElementNode> by lazy { list(data["elements"]).map { ElementNode(obj(it)) } }
}

internal class ColorDefinition(private val data: Map<String, Any?>) {
    val values: List<String> by lazy { list(data["values"]).map { it as String } }
    val notEqualTo: List<String> by lazy { list(data["notEqualTo"]).map { it as String } }
    val contrastTo: String? get() = data["contrastTo"] as? String
}

/**
 * A component entry; aliases (`extends`) share the source's [data] but keep their own [name].
 * PRNG keys use [name], user options and `<defs>` ids use [sourceName].
 */
internal class Component(val name: String, val extendsName: String?, val data: ComponentData) {
    val sourceName: String get() = extendsName ?: name
    val width: Double get() = data.width
    val height: Double get() = data.height
    val probability: Double get() = data.probability ?: 100.0
    val rotate: Range? get() = data.rotate
    val scale: Range? get() = data.scale
    val translateX: Range? get() = data.translateX
    val translateY: Range? get() = data.translateY
    val variants: Map<String, ComponentVariant> get() = data.variants
}

internal class ComponentData(raw: Map<String, Any?>) {
    val width: Double = (raw["width"] as Number).toDouble()
    val height: Double = (raw["height"] as Number).toDouble()
    val probability: Double? = (raw["probability"] as? Number)?.toDouble()
    val rotate: Range? = rangeFromDefinition(raw["rotate"])
    val scale: Range? = rangeFromDefinition(raw["scale"])
    val translateX: Range? = rangeFromDefinition(obj(raw["translate"])["x"])
    val translateY: Range? = rangeFromDefinition(obj(raw["translate"])["y"])
    val variants: Map<String, ComponentVariant> by lazy {
        obj(raw["variants"]).mapValuesTo(LinkedHashMap()) { ComponentVariant(obj(it.value)) }
    }
}

internal class ComponentVariant(private val data: Map<String, Any?>) {
    val elements: List<ElementNode> by lazy { list(data["elements"]).map { ElementNode(obj(it)) } }
    val weight: Double get() = (data["weight"] as? Number)?.toDouble() ?: 1.0
    val tags: List<String> by lazy { list(data["tags"]).filterIsInstance<String>() }

    /** Without [value]: the bare `category` tag or any `category:…` tag. With: exactly `category:value`. */
    fun hasTag(category: String, value: String? = null): Boolean =
        if (value == null) tags.any { it == category || it.startsWith("$category:") } else "$category:$value" in tags
}

internal class ElementNode(private val data: Map<String, Any?>) {
    val type: String get() = data["type"] as String
    val name: String? get() = data["name"] as? String
    val value: Any? get() = data["value"]

    @Suppress("UNCHECKED_CAST")
    val attributes: Map<String, Any?>? get() = data["attributes"] as? Map<String, Any?>
    val animations: List<Map<String, Any?>> get() = list(data["animations"]).map(::obj)
    val children: List<ElementNode> by lazy { list(data["children"]).map { ElementNode(obj(it)) } }
}

/** Ranges in a style definition are always `{min, max, step?}` objects. */
internal fun rangeFromDefinition(value: Any?): Range? {
    val d = value as? Map<*, *> ?: return null
    return Range((d["min"] as Number).toDouble(), (d["max"] as Number).toDouble(), (d["step"] as? Number)?.toDouble())
}

@Suppress("UNCHECKED_CAST")
internal fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()

internal fun list(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()
