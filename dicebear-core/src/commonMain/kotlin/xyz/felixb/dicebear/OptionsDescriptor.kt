package xyz.felixb.dicebear

import xyz.felixb.dicebear.internal.COLOR_ORDER_FIXED
import xyz.felixb.dicebear.internal.COLOR_ORDER_RANDOM
import xyz.felixb.dicebear.internal.Json
import xyz.felixb.dicebear.internal.deepCopyJsonMap

/**
 * Describes every option a style accepts (type, range, enum values, …), e.g. to build an editor
 * UI without introspecting the style definition.
 *
 * ```kotlin
 * OptionsDescriptor(DiceBearStyles.adventurer).toMap()["eyesVariant"]
 * // {type=enum, values=[variant01, …], list=true, weighted=true}
 * ```
 */
public class OptionsDescriptor(private val style: Style) {
    private val descriptor: Map<String, Any?> by lazy { build() }

    /** A deep copy of the field map (option name → field description). Numbers are [Double]. */
    public fun toMap(): Map<String, Any?> = deepCopyJsonMap(descriptor)

    /** The field map as JSON text. */
    public fun toJson(): String = Json.stringify(descriptor)

    private fun build(): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(
            "seed" to mapOf("type" to "string"),
            "size" to mapOf("type" to "number", "min" to 1.0, "max" to 4096.0),
            "idRandomization" to mapOf("type" to "boolean"),
            "title" to mapOf("type" to "string"),
            "flip" to mapOf("type" to "enum", "values" to listOf("none", "horizontal", "vertical", "both"), "list" to true),
            "fontFamily" to mapOf("type" to "string", "list" to true),
            "fontWeight" to mapOf("type" to "number", "min" to 1.0, "max" to 1000.0, "list" to true),
            "scale" to mapOf("type" to "range", "min" to 0.0, "max" to 10.0),
            "borderRadius" to mapOf("type" to "range", "min" to 0.0, "max" to 50.0),
            "rotate" to ROTATE_RANGE,
            "translateX" to TRANSLATE_RANGE,
            "translateY" to TRANSLATE_RANGE,
        )

        val tags = HashSet<String>()

        // Aliases accept no options of their own.
        for ((name, component) in style.components) {
            if (component.extendsName != null) continue

            result["${name}Variant"] = mapOf(
                "type" to "enum",
                "values" to component.variants.keys.sorted(),
                "list" to true,
                "weighted" to true,
            )
            result["${name}Probability"] = mapOf("type" to "number", "min" to 0.0, "max" to 100.0)

            component.variants.values.forEach { tags += it.tags }
        }

        // `background` is always present; redefining it keeps the first position.
        for (name in style.colors.keys + "background") {
            val definition = style.colors[name]
            val contrastTo = definition?.contrastTo
            val notEqualTo = definition?.notEqualTo.orEmpty()

            result["${name}Color"] = buildMap {
                put("type", "color")
                put("list", true)
                if (!contrastTo.isNullOrEmpty()) put("contrastTo", contrastTo)
                if (notEqualTo.isNotEmpty()) put("notEqualTo", notEqualTo.toList())
            }
            result["${name}ColorFill"] = mapOf("type" to "enum", "values" to listOf("solid", "linear", "radial"), "list" to true)
            result["${name}ColorFillStops"] = mapOf("type" to "range", "min" to 2.0)
            result["${name}ColorAngle"] = ROTATE_RANGE
            result["${name}ColorOrder"] = mapOf("type" to "enum", "values" to listOf(COLOR_ORDER_RANDOM, COLOR_ORDER_FIXED))
        }

        if (tags.isNotEmpty()) {
            result["tags"] = mapOf("type" to "enum", "values" to tags.sorted(), "list" to true, "open" to true)
        }

        if (style.hasAnimations) {
            result["animation"] = mapOf("type" to "boolean")
            result["animationSpeed"] = ANIMATION_SPEED_RANGE
            result["animationDelay"] = ANIMATION_DELAY_RANGE

            for (name in style.animationNames) {
                result["${name}Animation"] = mapOf("type" to "boolean")
                result["${name}AnimationSpeed"] = ANIMATION_SPEED_RANGE
                result["${name}AnimationDelay"] = ANIMATION_DELAY_RANGE
            }
        }

        return result
    }

    private companion object {
        val ANIMATION_SPEED_RANGE = mapOf("type" to "range", "min" to 0.1, "max" to 10.0)
        val ANIMATION_DELAY_RANGE = mapOf("type" to "range", "min" to -3600.0, "max" to 3600.0)
        val ROTATE_RANGE = mapOf("type" to "range", "min" to -360.0, "max" to 360.0)
        val TRANSLATE_RANGE = mapOf("type" to "range", "min" to -1000.0, "max" to 1000.0)
    }
}
