package xyz.felixb.dicebear.internal

import xyz.felixb.dicebear.StyleMeta

private fun String?.unset() = this.isNullOrEmpty()

/**
 * Returns a single-line attribution for the style, or an empty string when the style carries no
 * attribution data. The typographic quotes are part of the byte-parity contract.
 */
internal fun licenseText(meta: StyleMeta): String {
    val sourceName = meta.sourceName
    val sourceUrl = meta.sourceUrl
    val creatorName = meta.creatorName
    val licenseName = meta.licenseName
    val licenseUrl = meta.licenseUrl

    if (sourceName.unset() && creatorName.unset() && licenseName.unset()) return ""

    var title = if (sourceName.unset()) "Design" else "\u201C$sourceName\u201D"
    if (!sourceUrl.unset()) title += " ($sourceUrl)"

    // Nullish, not falsy: an empty creator name stays empty, only a missing one is "Unknown".
    val creator = "\u201C${creatorName ?: "Unknown"}\u201D"

    val result = StringBuilder()

    // Skip the "Remix of" prefix for MIT-licensed or DiceBear-original styles.
    if (licenseName != "MIT" && creatorName != "DiceBear" && !sourceName.unset()) {
        result.append("Remix of ")
    }

    result.append("$title by $creator")

    if (!licenseName.unset()) {
        result.append(", licensed under \u201C$licenseName\u201D")
        if (!licenseUrl.unset()) result.append(" ($licenseUrl)")
    }

    return result.toString()
}

/** Builds the embedded RDF/Dublin Core `<metadata>` block, or an empty string. */
internal fun licenseXml(meta: StyleMeta): String {
    val title = meta.sourceName
    val creatorName = meta.creatorName
    val sourceUrl = meta.sourceUrl
    val licenseUrl = meta.licenseUrl
    val rights = licenseText(meta)

    if (title.unset() && creatorName.unset() && sourceUrl.unset() && licenseUrl.unset() && rights.isEmpty()) {
        return ""
    }

    val fields = StringBuilder()
    if (!title.unset()) fields.append("<dc:title>${escapeXml(title!!)}</dc:title>")
    if (!creatorName.unset()) fields.append("<dc:creator>${escapeXml(creatorName!!)}</dc:creator>")
    if (!sourceUrl.unset()) {
        fields.append("<dc:source xsi:type=\"dcterms:URI\">${escapeXml(sourceUrl!!)}</dc:source>")
    }
    if (!licenseUrl.unset()) {
        fields.append("<dcterms:license xsi:type=\"dcterms:URI\">${escapeXml(licenseUrl!!)}</dcterms:license>")
    }
    if (rights.isNotEmpty()) fields.append("<dc:rights>${escapeXml(rights)}</dc:rights>")

    return "<metadata" +
        " xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"" +
        " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
        " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
        " xmlns:dcterms=\"http://purl.org/dc/terms/\">" +
        "<rdf:RDF><rdf:Description>$fields</rdf:Description></rdf:RDF>" +
        "</metadata>"
}
