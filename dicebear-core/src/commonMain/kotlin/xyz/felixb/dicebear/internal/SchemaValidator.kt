package xyz.felixb.dicebear.internal

import xyz.felixb.dicebear.OptionsValidationError
import xyz.felixb.dicebear.StyleValidationError
import xyz.felixb.dicebear.ValidationErrorDetail
import xyz.felixb.dicebear.internal.schema.definitionMinJson
import xyz.felixb.dicebear.internal.schema.optionsMinJson
import kotlin.math.floor

/**
 * Validates style definitions and options against the shared DiceBear draft-07 JSON Schemas.
 * Accept/reject decisions match the reference implementations (pinned by the parity fixtures);
 * error messages are specific to this port.
 */
internal object SchemaValidator {
    private val definitionSchema by lazy { JsonSchema(Json.parse(definitionMinJson())) }
    private val optionsSchema by lazy { JsonSchema(Json.parse(optionsMinJson())) }

    fun validateStyle(data: Any?) {
        val errors = definitionSchema.validate(data)
        if (errors.isNotEmpty()) throw StyleValidationError(errors)
    }

    fun validateOptions(data: Any?) {
        val errors = optionsSchema.validate(data)
        if (errors.isNotEmpty()) throw OptionsValidationError(errors)
    }
}

/** A draft-07 JSON Schema validator covering the keywords the DiceBear schemas use (and a few more). */
internal class JsonSchema(private val root: Any?) {
    private val patterns = HashMap<String, (String) -> Boolean>()

    fun validate(data: Any?): List<ValidationErrorDetail> {
        // JSON cannot represent non-finite numbers; the JS reference rejects them everywhere.
        val nonFinite = mutableListOf<ValidationErrorDetail>()
        collectNonFinite(data, "", nonFinite)
        if (nonFinite.isNotEmpty()) return nonFinite

        val errors = mutableListOf<ValidationErrorDetail>()
        check(root, data, "", errors)
        return errors
    }

    private fun collectNonFinite(node: Any?, path: String, out: MutableList<ValidationErrorDetail>) {
        when (node) {
            is Double -> if (!node.isFinite()) out += ValidationErrorDetail(path, "must be a finite number")
            is List<*> -> node.forEachIndexed { i, v -> collectNonFinite(v, "$path/$i", out) }
            is Map<*, *> -> for ((k, v) in node) collectNonFinite(v, "$path/${pointerSegment(k.toString())}", out)
        }
    }

    private fun isValid(schema: Any?, data: Any?, path: String): Boolean {
        val errors = mutableListOf<ValidationErrorDetail>()
        check(schema, data, path, errors)
        return errors.isEmpty()
    }

    private fun check(schema: Any?, data: Any?, path: String, errors: MutableList<ValidationErrorDetail>) {
        if (schema is Boolean) {
            if (!schema) errors += ValidationErrorDetail(path, "must not be present")
            return
        }
        val s = schema as? Map<*, *> ?: return

        // In draft-07, keywords next to `$ref` are ignored.
        (s["\$ref"] as? String)?.let {
            check(resolveRef(it), data, path, errors)
            return
        }

        fun fail(message: String) {
            errors += ValidationErrorDetail(path, message)
        }

        s["type"]?.let { type ->
            val types = if (type is List<*>) type.map { it as String } else listOf(type as String)
            if (types.none { matchesType(it, data) }) {
                fail("must be ${types.joinToString(",")}")
                return
            }
        }

        s["enum"]?.let { values ->
            if ((values as List<*>).none { jsonEquals(it, data) }) fail("must be equal to one of the allowed values")
        }

        if (s.containsKey("const") && !jsonEquals(s["const"], data)) fail("must be equal to constant")

        when (data) {
            is String -> checkString(s, data, ::fail)
            is Double -> checkNumber(s, data, ::fail)
            is Map<*, *> -> checkObject(s, data, path, errors, ::fail)
            is List<*> -> checkArray(s, data, path, errors, ::fail)
        }

        (s["allOf"] as? List<*>)?.forEach { check(it, data, path, errors) }

        (s["anyOf"] as? List<*>)?.let { options ->
            if (options.none { isValid(it, data, path) }) fail("must match a schema in anyOf")
        }

        (s["oneOf"] as? List<*>)?.let { options ->
            if (options.count { isValid(it, data, path) } != 1) fail("must match exactly one schema in oneOf")
        }

        if (s.containsKey("not") && isValid(s["not"], data, path)) fail("must NOT be valid")

        if (s.containsKey("if")) {
            if (isValid(s["if"], data, path)) {
                if (s.containsKey("then")) check(s["then"], data, path, errors)
            } else if (s.containsKey("else")) {
                check(s["else"], data, path, errors)
            }
        }
    }

    private fun checkString(s: Map<*, *>, data: String, fail: (String) -> Unit) {
        val length by lazy { codePointCount(data) }
        (s["maxLength"] as? Double)?.let { if (length > it) fail("must NOT have more than ${it.toInt()} characters") }
        (s["minLength"] as? Double)?.let { if (length < it) fail("must NOT have fewer than ${it.toInt()} characters") }
        (s["pattern"] as? String)?.let { if (!pattern(it)(data)) fail("must match pattern \"$it\"") }
    }

    private fun checkNumber(s: Map<*, *>, data: Double, fail: (String) -> Unit) {
        (s["minimum"] as? Double)?.let { if (data < it) fail("must be >= ${jsNumberToString(it)}") }
        (s["maximum"] as? Double)?.let { if (data > it) fail("must be <= ${jsNumberToString(it)}") }
        (s["exclusiveMinimum"] as? Double)?.let { if (data <= it) fail("must be > ${jsNumberToString(it)}") }
        (s["exclusiveMaximum"] as? Double)?.let { if (data >= it) fail("must be < ${jsNumberToString(it)}") }
        (s["multipleOf"] as? Double)?.let {
            val q = data / it
            if (q != floor(q)) fail("must be multiple of ${jsNumberToString(it)}")
        }
    }

    private fun checkObject(
        s: Map<*, *>,
        data: Map<*, *>,
        path: String,
        errors: MutableList<ValidationErrorDetail>,
        fail: (String) -> Unit,
    ) {
        (s["required"] as? List<*>)?.forEach { key ->
            if (!data.containsKey(key)) fail("must have required property '$key'")
        }
        (s["minProperties"] as? Double)?.let { if (data.size < it) fail("must NOT have fewer than ${it.toInt()} properties") }
        (s["maxProperties"] as? Double)?.let { if (data.size > it) fail("must NOT have more than ${it.toInt()} properties") }

        val properties = s["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val patternProperties = (s["patternProperties"] as? Map<*, *>)?.map { (k, v) -> pattern(k as String) to v }.orEmpty()
        val additional = s["additionalProperties"]
        val propertyNames = s["propertyNames"]

        for ((rawKey, value) in data) {
            val key = rawKey as String
            val childPath = "$path/${pointerSegment(key)}"

            if (propertyNames != null && !isValid(propertyNames, key, childPath)) {
                errors += ValidationErrorDetail(childPath, "property name must be valid")
            }

            var matched = false
            if (properties.containsKey(key)) {
                matched = true
                check(properties[key], value, childPath, errors)
            }
            for ((matches, schema) in patternProperties) {
                if (matches(key)) {
                    matched = true
                    check(schema, value, childPath, errors)
                }
            }
            if (!matched && additional != null) {
                if (additional == false) {
                    errors += ValidationErrorDetail(path, "must NOT have additional properties ('$key')")
                } else {
                    check(additional, value, childPath, errors)
                }
            }
        }

        (s["dependencies"] as? Map<*, *>)?.forEach { (key, dependency) ->
            if (!data.containsKey(key)) return@forEach
            if (dependency is List<*>) {
                dependency.forEach { if (!data.containsKey(it)) fail("must have property '$it' when '$key' is present") }
            } else {
                check(dependency, data, path, errors)
            }
        }
    }

    private fun checkArray(
        s: Map<*, *>,
        data: List<*>,
        path: String,
        errors: MutableList<ValidationErrorDetail>,
        fail: (String) -> Unit,
    ) {
        (s["minItems"] as? Double)?.let { if (data.size < it) fail("must NOT have fewer than ${it.toInt()} items") }
        (s["maxItems"] as? Double)?.let { if (data.size > it) fail("must NOT have more than ${it.toInt()} items") }

        if (s["uniqueItems"] == true) {
            for (i in data.indices) {
                for (j in 0 until i) {
                    if (jsonEquals(data[i], data[j])) {
                        fail("must NOT have duplicate items (items ## $j and $i are identical)")
                        return
                    }
                }
            }
        }

        when (val items = s["items"]) {
            is List<*> -> data.forEachIndexed { i, v ->
                when {
                    i < items.size -> check(items[i], v, "$path/$i", errors)
                    s.containsKey("additionalItems") -> check(s["additionalItems"], v, "$path/$i", errors)
                }
            }
            null -> Unit
            else -> data.forEachIndexed { i, v -> check(items, v, "$path/$i", errors) }
        }

        if (s.containsKey("contains") && data.indices.none { isValid(s["contains"], data[it], "$path/$it") }) {
            fail("must contain at least 1 valid item(s)")
        }
    }

    private fun matchesType(type: String, data: Any?): Boolean = when (type) {
        "object" -> data is Map<*, *>
        "array" -> data is List<*>
        "string" -> data is String
        "number" -> data is Double && data.isFinite()
        "integer" -> data is Double && data.isFinite() && floor(data) == data
        "boolean" -> data is Boolean
        "null" -> data == null
        else -> false
    }

    private fun resolveRef(ref: String): Any? {
        require(ref.startsWith("#")) { "Unsupported \$ref: $ref" }
        var node: Any? = root
        for (raw in ref.removePrefix("#").split('/').drop(1)) {
            val segment = raw.replace("~1", "/").replace("~0", "~")
            node = when (node) {
                is Map<*, *> -> node[segment]
                is List<*> -> node[segment.toInt()]
                else -> null
            }
        }
        return node
    }

    /**
     * Compiles an ECMA-262 pattern. A trailing `$` becomes `(?![\s\S])` (true end of input):
     * on the JVM `$` would also match before a final newline, which JS does not allow.
     */
    private fun pattern(source: String): (String) -> Boolean = patterns.getOrPut(source) {
        val anchoredEnd = source.endsWith("$") && !source.endsWith("\\$")
        val regex = Regex(if (anchoredEnd) source.dropLast(1) + "(?![\\s\\S])" else source)
        ({ regex.containsMatchIn(it) })
    }

    private companion object {
        fun pointerSegment(key: String) = key.replace("~", "~0").replace("/", "~1")

        fun codePointCount(s: String): Int {
            var count = 0
            var i = 0
            while (i < s.length) {
                if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) i++
                i++
                count++
            }
            return count
        }

        fun jsonEquals(a: Any?, b: Any?): Boolean = when {
            a is Double && b is Double -> a == b
            a is Map<*, *> && b is Map<*, *> -> a.size == b.size && a.all { (k, v) -> b.containsKey(k) && jsonEquals(v, b[k]) }
            a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { jsonEquals(a[it], b[it]) }
            else -> a == b
        }
    }
}
