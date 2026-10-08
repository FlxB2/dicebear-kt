package xyz.felixb.dicebear.styles

import xyz.felixb.dicebear.Avatar
import java.io.File
import kotlin.test.Test

/** Writes `build/gallery/moods.html`: 100 avatars of the `moods` style. */
class MoodsGalleryTest {
    @Test
    fun writeMoodsGallery() {
        val cells = (1..100).joinToString("") { i ->
            val avatar = Avatar(DiceBearStyles.moods) {
                seed = "mood-$i"
                size = 112
                idRandomization = true
            }
            "<figure>${avatar.svg}<figcaption>mood-$i</figcaption></figure>"
        }

        val html = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>100 moods</title>
            <style>
              body { font-family: system-ui, sans-serif; margin: 24px; background: #fafafa; color: #222; }
              main { display: grid; grid-template-columns: repeat(10, 112px); gap: 12px; }
              figure { margin: 0; text-align: center; font-size: 10px; color: #777; }
            </style></head>
            <body><h1>100 × moods</h1><main>$cells</main></body></html>
        """.trimIndent()

        val out = File("build/gallery/moods.html")
        out.parentFile.mkdirs()
        out.writeText(html)
    }
}
