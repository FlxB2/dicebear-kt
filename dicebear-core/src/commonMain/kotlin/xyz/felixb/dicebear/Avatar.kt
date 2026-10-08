package xyz.felixb.dicebear

import xyz.felixb.dicebear.internal.Json
import xyz.felixb.dicebear.internal.Options
import xyz.felixb.dicebear.internal.Renderer
import xyz.felixb.dicebear.internal.Resolver
import xyz.felixb.dicebear.internal.deepCopyJson
import xyz.felixb.dicebear.internal.normalizeJsonValue

/**
 * A rendered DiceBear avatar. Rendering happens entirely offline and is deterministic: the same
 * style, seed and options always produce byte-identical SVG — identical to the official JS, PHP,
 * Python, Rust, Go, Dart and C# implementations.
 *
 * ```kotlin
 * val avatar = Avatar(DiceBearStyles.lorelei, mapOf("seed" to "Felix", "size" to 128))
 * avatar.svg          // <svg …>…</svg>
 * avatar.toDataUri()  // data:image/svg+xml;charset=utf-8,…
 * ```
 *
 * [options] uses the option names of the DiceBear documentation (e.g. `seed`, `size`, `flip`,
 * `backgroundColor`, `eyesVariant`, `eyesProbability`); values may be strings, numbers, booleans,
 * lists and maps. See also the builder overload `Avatar(style) { seed = "Felix" }`.
 *
 * @throws OptionsValidationError when the options are invalid.
 * @throws CircularColorReferenceError when the style's colors reference each other in a cycle.
 */
public class Avatar @Throws(IllegalArgumentException::class, IllegalStateException::class) constructor(
    style: Style,
    options: Map<String, Any?> = emptyMap(),
) {
    /** The SVG markup. */
    public val svg: String

    private val resolved: Map<String, Any?>

    init {
        @Suppress("UNCHECKED_CAST")
        val resolver = Resolver(style, Options(normalizeJsonValue(options) as Map<String, Any?>))
        svg = Renderer(style, resolver).render()
        resolved = resolver.resolved()
    }

    /**
     * Every value resolved while rendering (e.g. the chosen `eyesVariant`, `backgroundColor`,
     * `rotate`), in resolution order. Unset values and the seed are omitted; numbers are [Double].
     */
    @Suppress("UNCHECKED_CAST")
    public val resolvedOptions: Map<String, Any?>
        get() = deepCopyJson(resolved.filterValues { it != null }) as Map<String, Any?>

    /** The SVG markup. */
    override fun toString(): String = svg

    /** `{"svg": …, "options": …}` as JSON text, matching the JS `JSON.stringify(avatar)`. */
    public fun toJson(): String = Json.stringify(mapOf("svg" to svg, "options" to resolvedOptions))

    /**
     * The SVG as a `data:image/svg+xml` URI, percent-encoded exactly like JavaScript's
     * `encodeURIComponent`.
     */
    @Throws(IllegalArgumentException::class)
    public fun toDataUri(): String = "data:image/svg+xml;charset=utf-8," + encodeUriComponent(svg)

    private companion object {
        private const val UNRESERVED = "-_.!~*'()"
        private const val HEX = "0123456789ABCDEF"

        fun encodeUriComponent(s: String): String {
            for (i in s.indices) {
                val c = s[i]
                val paired = (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) ||
                    (c.isLowSurrogate() && i > 0 && s[i - 1].isHighSurrogate())
                require(!c.isSurrogate() || paired) {
                    "The SVG contains an unpaired surrogate at index $i and cannot be percent-encoded"
                }
            }

            val out = StringBuilder(s.length * 2)
            for (byte in s.encodeToByteArray()) {
                val b = byte.toInt() and 0xFF
                val c = b.toChar()
                if (b < 0x80 && (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in UNRESERVED)) {
                    out.append(c)
                } else {
                    out.append('%').append(HEX[b shr 4]).append(HEX[b and 0xF])
                }
            }
            return out.toString()
        }
    }
}

/**
 * Renders an avatar with options assembled by a builder:
 *
 * ```kotlin
 * val avatar = Avatar(DiceBearStyles.adventurer) {
 *     seed = "Felix"
 *     size = 128
 *     backgroundColor = listOf("b6e3f4", "c0aede")
 *     this["eyesVariant"] = listOf("variant01", "variant02")
 * }
 * ```
 */
@Throws(IllegalArgumentException::class, IllegalStateException::class)
public fun Avatar(style: Style, options: AvatarOptions.() -> Unit): Avatar =
    Avatar(style, AvatarOptions().apply(options).toMap())

/**
 * A builder for avatar options. The common options are typed properties; style-specific options
 * (`<component>Variant`, `<component>Probability`, `<color>Color`, …) are set with [set].
 */
public class AvatarOptions {
    private val values = LinkedHashMap<String, Any?>()

    /** Sets any option by its DiceBear name. A `null` value removes it. */
    public operator fun set(name: String, value: Any?) {
        if (value == null) values.remove(name) else values[name] = value
    }

    /** Returns an option by its DiceBear name. */
    public operator fun get(name: String): Any? = values[name]

    /** The seed the avatar is derived from (e.g. a username). */
    public var seed: String? by option("seed")

    /** Width and height of the SVG in pixels; unset means no fixed size. */
    public var size: Number? by option("size")

    /** Accessible title; without it the SVG is `aria-hidden`. */
    public var title: String? by option("title")

    /** Suffix every SVG id randomly (non-deterministic) so equal avatars can share a document. */
    public var idRandomization: Boolean? by option("idRandomization")

    /** `none`, `horizontal`, `vertical`, `both` — or a list to pick from. */
    public var flip: Any? by option("flip")

    /** Rotation in degrees: a number or a `[min, max]` list. */
    public var rotate: Any? by option("rotate")

    /** Scale factor: a number or a `[min, max]` list. */
    public var scale: Any? by option("scale")

    /** Corner radius in percent (0–50): a number or a `[min, max]` list. */
    public var borderRadius: Any? by option("borderRadius")

    /** Horizontal offset in percent: a number or a `[min, max]` list. */
    public var translateX: Any? by option("translateX")

    /** Vertical offset in percent: a number or a `[min, max]` list. */
    public var translateY: Any? by option("translateY")

    /** Background color(s) as hex (with or without `#`): a string or a list to pick from. */
    public var backgroundColor: Any? by option("backgroundColor")

    /** `solid`, `linear`, `radial` — or a list to pick from. */
    public var backgroundColorFill: Any? by option("backgroundColorFill")

    /** Font family (or list) for text-based styles such as `initials`. */
    public var fontFamily: Any? by option("fontFamily")

    /** Font weight (or list) for text-based styles such as `initials`. */
    public var fontWeight: Any? by option("fontWeight")

    /** Tag filter, e.g. `listOf("hairLength:long", "!glasses")`. */
    public var tags: Any? by option("tags")

    /** Turns on declarative animations for styles that carry them. */
    public var animation: Boolean? by option("animation")

    /** A snapshot of the options as a map, usable with the `Avatar(style, map)` constructor. */
    public fun toMap(): Map<String, Any?> = LinkedHashMap(values)

    private fun <T> option(name: String) = OptionDelegate<T>(name)

    private inner class OptionDelegate<T>(private val name: String) :
        kotlin.properties.ReadWriteProperty<AvatarOptions, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: AvatarOptions, property: kotlin.reflect.KProperty<*>): T? = values[name] as T?

        override fun setValue(thisRef: AvatarOptions, property: kotlin.reflect.KProperty<*>, value: T?) {
            set(name, value)
        }
    }
}
