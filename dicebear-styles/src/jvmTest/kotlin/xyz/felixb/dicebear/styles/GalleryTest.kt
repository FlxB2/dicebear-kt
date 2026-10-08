package xyz.felixb.dicebear.styles

import xyz.felixb.dicebear.Avatar
import java.io.File
import kotlin.test.Test

/** Writes `build/gallery/index.html`: every bundled style rendered for a few seeds. */
class GalleryTest {
    @Test
    fun writeGallery() {
        val seeds = listOf("Felix", "Aneka", "Jade", "Mason")

        val rows = DiceBearStyles.names.joinToString("\n") { name ->
            val style = DiceBearStyles[name]!!
            val cells = seeds.joinToString("") { seed ->
                val avatar = Avatar(style) {
                    this.seed = seed
                    size = 96
                    idRandomization = true
                }
                "<figure>${avatar.svg}<figcaption>$seed</figcaption></figure>"
            }
            "<section><h2>$name</h2><div class=\"row\">$cells</div></section>"
        }

        val html = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>DiceBear Kotlin gallery</title>
            <style>
              body { font-family: system-ui, sans-serif; margin: 24px; background: #fafafa; color: #222; }
              main { display: grid; grid-template-columns: repeat(auto-fill, minmax(460px, 1fr)); gap: 16px; }
              section { background: #fff; border-radius: 12px; padding: 12px 16px; box-shadow: 0 1px 3px #0001; }
              h2 { font-size: 14px; margin: 0 0 8px; }
              .row { display: flex; gap: 12px; }
              figure { margin: 0; text-align: center; font-size: 11px; color: #777; }
            </style></head>
            <body><h1>DiceBear Kotlin — ${DiceBearStyles.names.size} styles, rendered offline on the JVM</h1>
            <main>$rows</main></body></html>
        """.trimIndent()

        val out = File("build/gallery/index.html")
        out.parentFile.mkdirs()
        out.writeText(html)
        println("Gallery written to ${out.absolutePath}")
    }
}
