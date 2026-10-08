package xyz.felixb.dicebear.internal

/** Escapes the five XML predefined entities in a single pass. */
internal fun escapeXml(value: String): String {
    if (value.none { it == '&' || it == '\'' || it == '"' || it == '<' || it == '>' }) return value

    val out = StringBuilder(value.length + 16)
    for (c in value) {
        when (c) {
            '&' -> out.append("&amp;")
            '\'' -> out.append("&apos;")
            '"' -> out.append("&quot;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(c)
        }
    }
    return out.toString()
}
