package xyz.felixb.dicebear.parity

import xyz.felixb.dicebear.Avatar
import xyz.felixb.dicebear.CircularColorReferenceError
import xyz.felixb.dicebear.OptionsDescriptor
import xyz.felixb.dicebear.Style
import xyz.felixb.dicebear.StyleValidationError
import xyz.felixb.dicebear.ValidationError
import xyz.felixb.dicebear.internal.Color
import xyz.felixb.dicebear.internal.Json
import xyz.felixb.dicebear.internal.Mulberry32
import xyz.felixb.dicebear.internal.Options
import xyz.felixb.dicebear.internal.Prng
import xyz.felixb.dicebear.internal.Range
import xyz.felixb.dicebear.internal.fnv1aHash
import xyz.felixb.dicebear.internal.fnv1aHex
import xyz.felixb.dicebear.internal.formatNumber
import xyz.felixb.dicebear.internal.initialsFromSeed
import xyz.felixb.dicebear.list
import xyz.felixb.dicebear.obj
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The cross-language parity suite of the DiceBear monorepo (`tests/fixtures/parity`): every
 * official implementation must reproduce these results exactly, byte for byte.
 *
 * Each test collects all mismatches before failing, so one run shows the full picture.
 */
class ParityTest {
    private fun fixture(name: String): Any? =
        Json.parse(parityFiles[name]?.invoke() ?: error("Missing parity fixture $name"))

    private fun cases(name: String): List<Map<String, Any?>> = list(fixture(name)).map(::obj)

    private fun styleNames(): List<String> =
        parityFiles.keys.filter { it.startsWith("styles/") }.map { it.removePrefix("styles/") }.sorted()

    private fun assertAll(failures: List<String>) {
        if (failures.isNotEmpty()) fail("${failures.size} mismatch(es):\n" + failures.joinToString("\n"))
    }

    private fun Any?.asRange(): Range {
        val m = obj(this)
        return Range((m["min"] as Double), (m["max"] as Double), m["step"] as? Double)
    }

    @Test
    fun fnv1a() {
        val failures = mutableListOf<String>()
        for (c in cases("fnv1a")) {
            val input = c["input"] as String
            val hash = (c["hash"] as Double).toLong()
            if (fnv1aHash(input) != hash) failures += "hash(${Json.stringify(input)}) = ${fnv1aHash(input)}, want $hash"
            if (fnv1aHex(input) != c["hex"]) failures += "hex(${Json.stringify(input)}) = ${fnv1aHex(input)}, want ${c["hex"]}"
        }
        assertAll(failures)
    }

    @Test
    fun mulberry32() {
        val failures = mutableListOf<String>()
        for (c in cases("mulberry32")) {
            val seed = (c["seed"] as Double).toLong()
            val prng = Mulberry32(seed)
            list(c["sequence"]).map(::obj).forEachIndexed { i, step ->
                val float = prng.nextFloat()
                val state = prng.state()
                if (float != step["float"]) failures += "seed $seed step $i: float $float, want ${step["float"]}"
                if (state != (step["state"] as Double).toInt()) failures += "seed $seed step $i: state $state, want ${step["state"]}"
            }
        }
        assertAll(failures)
    }

    @Test
    fun prng() {
        val root = obj(fixture("prng"))
        val failures = mutableListOf<String>()

        fun check(method: String, block: (Map<String, Any?>, Prng) -> Pair<Any?, Any?>) {
            val entries = list(root[method]).map(::obj)
            assertTrue(entries.isNotEmpty(), method)
            for (c in entries) {
                val (got, want) = block(c, Prng(c["seed"] as String))
                if (Json.stringify(got) != Json.stringify(want)) {
                    failures += "$method(${Json.stringify(c)}) = ${Json.stringify(got)}"
                }
            }
        }

        check("getValue") { c, p -> p.getValue(c["key"] as String) to c["result"] }
        check("pick") { c, p -> p.pick(c["key"] as String, list(c["items"]).map { it!! }) to c["result"] }
        check("weightedPick") { c, p ->
            p.weightedPick(c["key"] as String, obj(c["weights"]).mapValues { it.value as Double }) to c["result"]
        }
        check("bool") { c, p ->
            val likelihood = c["likelihood"] as? Double
            (if (likelihood == null) p.bool(c["key"] as String) else p.bool(c["key"] as String, likelihood)) to c["result"]
        }
        check("float") { c, p -> p.float(c["key"] as String, c["range"].asRange()) to c["result"] }
        check("integer") { c, p -> p.integer(c["key"] as String, c["range"].asRange()).toDouble() to c["result"] }
        check("shuffle") { c, p -> p.shuffle(c["key"] as String, list(c["items"]).map { it!! }) to c["result"] }

        assertAll(failures)
    }

    @Test
    fun numbers() {
        val failures = mutableListOf<String>()
        for (c in cases("numbers")) {
            val got = formatNumber(c["input"] as Double)
            if (got != c["output"]) failures += "formatNumber(${c["input"]}) = $got, want ${c["output"]}"
        }
        assertAll(failures)
    }

    @Test
    fun initials() {
        val failures = mutableListOf<String>()
        for (c in cases("initials")) {
            val seed = c["seed"] as String
            val got = initialsFromSeed(seed)
            if (got != c["result"]) failures += "initials(${Json.stringify(seed)}) = ${Json.stringify(got)}, want ${Json.stringify(c["result"])}"
        }
        assertAll(failures)
    }

    @Test
    fun colors() {
        val root = obj(fixture("colors"))
        val failures = mutableListOf<String>()

        fun check(method: String, block: (Map<String, Any?>) -> Any?) {
            val entries = list(root[method]).map(::obj)
            assertTrue(entries.isNotEmpty(), method)
            for (c in entries) {
                val got = block(c)
                if (Json.stringify(got) != Json.stringify(c["result"])) {
                    failures += "$method(${Json.stringify(c)}) = ${Json.stringify(got)}"
                }
            }
        }

        fun strings(value: Any?) = list(value).map { it as String }

        check("toHex") { Color.toHex(it["input"] as String) }
        check("toRgbHex") { Color.toRgbHex(it["input"] as String) }
        check("parseHex") { Color.parseHex(it["input"] as String) }
        check("luminance") { Color.luminance(it["input"] as String) }
        check("sortByContrast") { Color.sortByContrast(strings(it["candidates"]), it["refColor"] as String) }
        check("filterNotEqualTo") { Color.filterNotEqualTo(strings(it["candidates"]), strings(it["excluded"])) }

        assertAll(failures)
    }

    @Test
    fun validation() {
        val root = obj(fixture("validation"))
        val failures = mutableListOf<String>()

        val styles = list(root["styles"]).map(::obj)
        val options = list(root["options"]).map(::obj)
        assertTrue(styles.isNotEmpty() && options.isNotEmpty())

        for (c in styles) {
            val error = runCatching { Style(obj(c["definition"])) }.exceptionOrNull()
            when {
                c["valid"] == true && error != null -> failures += "style ${c["id"]}: rejected but valid ($error)"
                c["valid"] == false && error == null -> failures += "style ${c["id"]}: accepted but invalid"
                error != null && error !is StyleValidationError -> failures += "style ${c["id"]}: unexpected $error"
            }
        }

        for (c in options) {
            val raw = c["options"]
            val error = runCatching {
                if (raw !is Map<*, *>) {
                    // Non-object options can only come from JSON; validate them directly.
                    xyz.felixb.dicebear.internal.SchemaValidator.validateOptions(raw)
                } else {
                    Options(obj(raw))
                }
            }.exceptionOrNull()
            when {
                c["valid"] == true && error != null -> failures += "options ${c["id"]}: rejected but valid ($error)"
                c["valid"] == false && error == null -> failures += "options ${c["id"]}: accepted but invalid"
                error != null && error !is ValidationError -> failures += "options ${c["id"]}: unexpected $error"
            }
        }

        assertAll(failures)
    }

    @Test
    fun circularColors() {
        val cases = list(obj(fixture("validation"))["circularColors"]).map(::obj)
        assertTrue(cases.isNotEmpty())

        for (c in cases) {
            val chain = list(c["chain"]).map { it as String }
            val style = Style(obj(c["style"]))
            try {
                Avatar(style, obj(c["options"]))
                fail("${c["id"]}: expected a CircularColorReferenceError")
            } catch (e: CircularColorReferenceError) {
                assertEquals(chain, e.chain, c["id"] as String)
                assertEquals("Circular color reference: ${chain.joinToString(" → ")}", e.message)
            }
        }
    }

    @Test
    fun avatars() {
        val names = styleNames()
        assertTrue(names.isNotEmpty())
        val failures = mutableListOf<String>()

        for (name in names) {
            val style = Style.parse(parityFiles.getValue("styles/$name")())

            for (c in cases("avatars/$name")) {
                val id = "$name/${c["id"]}"
                val avatar = try {
                    Avatar(style, obj(c["options"]))
                } catch (e: Exception) {
                    failures += "$id: threw $e"
                    continue
                }

                val want = c["svg"] as String
                if (avatar.svg != want) {
                    val i = avatar.svg.zip(want).indexOfFirst { (a, b) -> a != b }.let { if (it < 0) minOf(avatar.svg.length, want.length) else it }
                    failures += "$id: SVG differs at $i\n   got …${avatar.svg.window(i)}…\n  want …${want.window(i)}…"
                }

                val gotOptions = Json.stringify(avatar.resolvedOptions)
                val wantOptions = Json.stringify(c["resolvedOptions"])
                if (gotOptions != wantOptions) failures += "$id: resolvedOptions\n   got $gotOptions\n  want $wantOptions"

                (c["dataUri"] as? String)?.let {
                    if (avatar.toDataUri() != it) failures += "$id: dataUri differs"
                }
            }
        }

        assertAll(failures)
    }

    @Test
    fun descriptors() {
        val failures = mutableListOf<String>()
        for (name in styleNames()) {
            val style = Style.parse(parityFiles.getValue("styles/$name")())
            val got = OptionsDescriptor(style).toJson()
            val want = Json.stringify(fixture("descriptors/$name"))
            if (got != want) failures += "$name:\n   got $got\n  want $want"
        }
        assertAll(failures)
    }

    private fun String.window(i: Int) = substring(maxOf(0, i - 50), minOf(length, i + 50))
}
