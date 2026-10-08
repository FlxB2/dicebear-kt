package xyz.felixb.dicebear.reference

import xyz.felixb.dicebear.Avatar
import xyz.felixb.dicebear.Style
import xyz.felixb.dicebear.internal.Json
import xyz.felixb.dicebear.list
import xyz.felixb.dicebear.obj
import xyz.felixb.dicebear.reference.styles.referenceStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Every bundled style, rendered for several seeds and option sets, must be byte-identical to the
 * official `@dicebear/core` output recorded in `reference/avatars.json`.
 */
class ReferenceAvatarsTest {
    @Test
    fun matchesOfficialJavaScriptOutput() {
        val cases = list(Json.parse(referenceFiles.getValue("avatars")())).map(::obj)
        val styles = HashMap<String, Style>()
        val failures = mutableListOf<String>()

        assertEquals(referenceStyles.keys, cases.map { it["style"] as String }.toSet(), "every bundled style is covered")

        for (c in cases) {
            val name = c["style"] as String
            val style = styles.getOrPut(name) { Style.parse(referenceStyles.getValue(name)()) }
            val avatar = Avatar(style, obj(c["options"]))
            val label = "$name ${Json.stringify(c["options"])}"

            if (avatar.svg != c["svg"]) failures += "$label: SVG differs"
            if (Json.stringify(avatar.resolvedOptions) != Json.stringify(c["resolvedOptions"])) {
                failures += "$label: resolved options differ"
            }
        }

        if (failures.isNotEmpty()) fail("${failures.size}/${cases.size} cases differ:\n" + failures.joinToString("\n"))
    }
}
