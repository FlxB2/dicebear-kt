import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Makes the [EmbedJsonTask] type available to the build scripts. */
class EmbedJsonPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}

/**
 * Turns every `*.json` file below [sourceDir] into Kotlin source, so the data is available on every
 * Kotlin Multiplatform target without platform-specific resource loading.
 *
 * Each file becomes an `internal fun <name>Json(): String` returning the minified JSON (whitespace
 * outside of strings removed, nothing else touched, so key order and number spelling survive).
 * An index `internal val <indexName>: Map<String, () -> String>` maps each relative path (without
 * the `.json` extension) to its function.
 *
 * With [styleAccessors] enabled, every file additionally gets a public, lazily parsed `Style`
 * accessor plus a public raw-definition accessor — the public API of `dicebear-styles`.
 */
@CacheableTask
abstract class EmbedJsonTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val indexName: Property<String>

    @get:Input
    abstract val styleAccessors: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        styleAccessors.convention(false)
    }

    @TaskAction
    fun generate() {
        val root = sourceDir.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()

        val pkg = packageName.get()
        val dir = out.resolve(pkg.replace('.', '/'))
        dir.mkdirs()

        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        val index = StringBuilder()
        index.append(HEADER).append("package $pkg\n\n")
        index.append("internal val ${indexName.get()}: Map<String, () -> String> = mapOf(\n")

        for (file in files) {
            val key = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".json")
            val ident = identifier(key)
            val json = minify(file.readText())

            val source = StringBuilder()
            source.append(HEADER).append("package $pkg\n\n")

            if (styleAccessors.get()) {
                source.append("import xyz.felixb.dicebear.Style\n\n")
                source.append(styleAccessorSource(key, ident, file))
            }

            source.append("internal fun ${ident}Json(): String = buildString(${json.length}) {\n")
            for (chunk in json.chunked(CHUNK)) {
                source.append("    append(\"").append(escape(chunk)).append("\")\n")
            }
            source.append("}\n")

            dir.resolve("${ident.replaceFirstChar(Char::uppercase)}Json.kt").writeText(source.toString())
            index.append("    \"$key\" to ::${ident}Json,\n")
        }

        index.append(")\n")
        dir.resolve("${indexName.get().replaceFirstChar(Char::uppercase)}.kt").writeText(index.toString())
    }

    private fun styleAccessorSource(key: String, ident: String, file: java.io.File): String {
        @Suppress("UNCHECKED_CAST")
        val meta = ((JsonSlurper().parse(file) as Map<String, Any?>)["meta"] as? Map<String, Any?>).orEmpty()

        fun field(block: String, name: String) =
            ((meta[block] as? Map<*, *>)?.get(name) as? String)?.takeIf { it.isNotBlank() }

        fun withUrl(text: String, block: String) = text + (field(block, "url")?.let { " ($it)" } ?: "") + "."

        val doc = buildList {
            add("The DiceBear \"$key\" avatar style.")
            field("source", "name")?.let { add(""); add(withUrl("Based on \"$it\"", "source")) }
            field("creator", "name")?.let { add(withUrl("Created by $it", "creator")) }
            field("license", "name")?.let { add(withUrl("Licensed under $it", "license")) }
        }.joinToString("\n") { if (it.isEmpty()) " *" else " * ${it.replace("*/", "*&#47;")}" }

        return """
            |private val ${ident}Style: Style by lazy { Style.parse(${ident}Json()) }
            |
            |/**
            |$doc
            | */
            |public val DiceBearStyles.$ident: Style get() = ${ident}Style
            |
            |/** The raw JSON style definition of [DiceBearStyles.$ident]. */
            |public val DiceBearStyles.${ident}Definition: String get() = ${ident}Json()
            |
            |""".trimMargin()
    }

    private companion object {
        const val CHUNK = 8_000
        const val HEADER = "// Generated by EmbedJsonTask from the JSON sources. Do not edit.\n\n"

        fun identifier(key: String): String {
            val parts = key.split('/', '-', '_', '.').filter(String::isNotEmpty)
            return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
        }

        /** Drops insignificant whitespace; strings are copied verbatim. */
        fun minify(json: String): String {
            val out = StringBuilder(json.length)
            var inString = false
            var i = 0
            while (i < json.length) {
                val c = json[i]
                if (inString) {
                    out.append(c)
                    if (c == '\\') {
                        out.append(json[i + 1])
                        i++
                    } else if (c == '"') {
                        inString = false
                    }
                } else if (c == '"') {
                    inString = true
                    out.append(c)
                } else if (!c.isWhitespace()) {
                    out.append(c)
                }
                i++
            }
            return out.toString()
        }

        /** Escapes a chunk as the body of a Kotlin string literal (ASCII-only output). */
        fun escape(s: String): String {
            val out = StringBuilder(s.length + 16)
            for (c in s) {
                when {
                    c == '\\' -> out.append("\\\\")
                    c == '"' -> out.append("\\\"")
                    c == '$' -> out.append("\\$")
                    c.code in 0x20..0x7e -> out.append(c)
                    else -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                }
            }
            return out.toString()
        }
    }
}
