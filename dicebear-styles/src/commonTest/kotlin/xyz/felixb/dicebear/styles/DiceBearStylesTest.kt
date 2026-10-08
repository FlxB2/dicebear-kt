package xyz.felixb.dicebear.styles

import xyz.felixb.dicebear.Avatar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiceBearStylesTest {
    @Test
    fun everyBundledStyleParsesAndRenders() {
        assertEquals(63, DiceBearStyles.names.size)

        for (name in DiceBearStyles.names) {
            val style = DiceBearStyles[name] ?: error("$name missing")
            val svg = Avatar(style, mapOf("seed" to "Felix")).svg

            assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), name)
            assertTrue(svg.endsWith("</svg>"), name)
        }
    }

    @Test
    fun renderingIsDeterministic() {
        val first = Avatar(DiceBearStyles.adventurer, mapOf("seed" to "Felix")).svg
        val second = Avatar(DiceBearStyles.adventurer) { seed = "Felix" }.svg
        val other = Avatar(DiceBearStyles.adventurer, mapOf("seed" to "Aneka")).svg

        assertEquals(first, second)
        assertTrue(first != other)
    }

    @Test
    fun accessorsAndRegistryShareInstances() {
        assertSame(DiceBearStyles.lorelei, DiceBearStyles.lorelei)
        assertEquals(DiceBearStyles.loreleiDefinition, DiceBearStyles.definition("lorelei"))
        assertNull(DiceBearStyles["does-not-exist"])
    }
}
