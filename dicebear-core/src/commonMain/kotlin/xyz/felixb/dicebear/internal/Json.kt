package xyz.felixb.dicebear.internal

/**
 * A minimal JSON reader/writer working on plain Kotlin values, so the engine has no dependencies.
 *
 * Decoded values are `Map<String, Any?>` (insertion ordered — attribute and key order is
 * load-bearing for byte parity with the JS reference), `List<Any?>`, `String`, `Double`,
 * `Boolean` and `null`. Every number is a `Double`, like in JavaScript.
 */
internal object Json {
    fun parse(text: String): Any? = Parser(text).parseDocument()

    /** Serializes like JavaScript's `JSON.stringify` (no whitespace). */
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is String -> writeString(out, value)
            is Number -> {
                val d = value.toDouble()
                out.append(if (d.isFinite()) jsNumberToString(d) else "null")
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k as String)
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> writeString(out, value.toString())
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 -> out.append(unicodeEscape(c))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    out.append(c).append(s[i + 1])
                    i++
                }
                // Well-formed JSON.stringify escapes lone surrogates.
                c.isSurrogate() -> out.append(unicodeEscape(c))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    private fun unicodeEscape(c: Char) = "\\u" + c.code.toString(16).padStart(4, '0')

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (i != s.length) fail("Unexpected trailing content")
            return value
        }

        private fun parseValue(): Any? {
            if (i >= s.length) fail("Unexpected end of input")
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipWhitespace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a string key")
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipWhitespace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            i++
            val out = StringBuilder()
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Invalid unicode escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Invalid unicode escape")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> fail("Invalid escape '\\$e'")
                        }
                    }
                    c.code < 0x20 -> fail("Unescaped control character in string")
                    else -> out.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            if (peek() == '-') i++
            if (peek() == '0') {
                i++
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') i++
            } else {
                fail("Invalid number")
            }
            if (peek() == '.') {
                i++
                if (peek() !in '0'..'9') fail("Invalid number")
                while (peek() in '0'..'9') i++
            }
            if (peek() == 'e' || peek() == 'E') {
                i++
                if (peek() == '+' || peek() == '-') i++
                if (peek() !in '0'..'9') fail("Invalid number")
                while (peek() in '0'..'9') i++
            }
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("Unexpected token")
            i += word.length
            return value
        }

        private fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c'")
            i++
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException("Invalid JSON at offset $i: $message")
    }
}

/**
 * Converts a caller-supplied value into the engine's JSON model: any [Number] becomes a [Double],
 * arrays and other iterables become lists, map keys become strings. Unsupported values are kept
 * as-is, so schema validation rejects them.
 */
internal fun normalizeJsonValue(value: Any?): Any? = when (value) {
    null, is String, is Boolean, is Double -> value
    is Number -> value.toDouble()
    is Map<*, *> -> LinkedHashMap<String, Any?>(value.size).also { map ->
        for ((k, v) in value) map[k.toString()] = normalizeJsonValue(v)
    }
    is Iterable<*> -> value.map(::normalizeJsonValue)
    is Array<*> -> value.map(::normalizeJsonValue)
    is IntArray -> value.map { it.toDouble() }
    is DoubleArray -> value.toList()
    is FloatArray -> value.map { it.toDouble() }
    is LongArray -> value.map { it.toDouble() }
    else -> value
}

/** Deep-copies a JSON value tree (the JS port's `structuredClone`), preserving key order. */
internal fun deepCopyJson(value: Any?): Any? = when (value) {
    is Map<*, *> -> LinkedHashMap<String, Any?>(value.size).also { map ->
        for ((k, v) in value) map[k as String] = deepCopyJson(v)
    }
    is List<*> -> value.map(::deepCopyJson)
    else -> value
}

@Suppress("UNCHECKED_CAST")
internal fun deepCopyJsonMap(map: Map<String, Any?>): Map<String, Any?> = deepCopyJson(map) as Map<String, Any?>
