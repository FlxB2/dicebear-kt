package xyz.felixb.dicebear.styles

import xyz.felixb.dicebear.Style

/**
 * The bundled DiceBear avatar styles (https://github.com/dicebear/styles).
 *
 * Every style is available as an extension property, e.g. [DiceBearStyles.lorelei], and parsed
 * lazily on first access. On Kotlin/JS and Kotlin/Wasm, styles that are never referenced are
 * removed from the bundle; [names] and [get] reference all of them.
 *
 * ```kotlin
 * val avatar = Avatar(DiceBearStyles.lorelei, mapOf("seed" to "Felix"))
 * ```
 */
public object DiceBearStyles {
    private val cache = HashMap<String, Style>()

    /** The names of all bundled styles, e.g. `adventurer-neutral`, `lorelei`, `pixel-art`. */
    public val names: List<String> get() = styleDefinitions.keys.toList()

    /** The style with the given name (as in [names]), or `null` if there is none. */
    public operator fun get(name: String): Style? {
        cache[name]?.let { return it }
        val json = styleDefinitions[name]?.invoke() ?: return null
        return Style.parse(json).also { cache[name] = it }
    }

    /** The raw JSON definition of the style with the given name, or `null` if there is none. */
    public fun definition(name: String): String? = styleDefinitions[name]?.invoke()
}
