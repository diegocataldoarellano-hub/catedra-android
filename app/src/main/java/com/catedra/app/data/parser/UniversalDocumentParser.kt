package com.catedra.app.data.parser

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.catedra.app.data.model.DocSection
import com.catedra.app.data.model.DocType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

data class ParsedDocumentResult(
    val fileName: String,
    val title: String,
    val mimeType: String,
    val docType: DocType,
    val sections: List<DocSection>,
    val totalChars: Int
)

object UniversalDocumentParser {

    @Volatile
    private var pdfBoxInitialized = false

    private fun ensurePdfBox(context: Context) {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "documento"
        }
        var name: String? = null
        runCatching {
            val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Documento"
    }

    suspend fun parseUri(
        context: Context,
        uri: Uri,
        forcedFileName: String? = null,
        forcedMime: String? = null
    ): ParsedDocumentResult = withContext(Dispatchers.IO) {
        val fileName = forcedFileName ?: resolveDisplayName(context, uri)
        val mimeType = forcedMime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        val docType = DocType.fromFileNameAndMime(fileName, mimeType)
        val cleanTitle = fileName.substringBeforeLast('.').ifBlank { fileName }

        val sections = when (docType) {
            DocType.PDF, DocType.PAPER -> parsePdf(context, uri)
            DocType.DOCX -> {
                if (fileName.lowercase().endsWith(".odt") || mimeType.contains("opendocument.text")) {
                    openStream(context, uri)?.use { parseOdfText(it) } ?: emptyList()
                } else {
                    openStream(context, uri)?.use { parseDocx(it) } ?: emptyList()
                }
            }
            DocType.XLSX -> {
                if (fileName.lowercase().endsWith(".ods") || mimeType.contains("opendocument.spreadsheet")) {
                    openStream(context, uri)?.use { parseOdsSpreadsheet(it) } ?: emptyList()
                } else {
                    openStream(context, uri)?.use { parseXlsx(it) } ?: emptyList()
                }
            }
            DocType.CSV -> openStream(context, uri)?.use { parseCsvOrTsv(it, fileName.lowercase().endsWith(".tsv")) } ?: emptyList()
            DocType.PPTX -> openStream(context, uri)?.use { parsePptx(it) } ?: emptyList()
            DocType.EPUB -> openStream(context, uri)?.use { parseEpub(it) } ?: emptyList()
            DocType.DOC -> {
                if (fileName.lowercase().endsWith(".rtf") || mimeType.contains("rtf")) {
                    openStream(context, uri)?.use { parseRtf(it) } ?: emptyList()
                } else {
                    openStream(context, uri)?.use { parseLegacyBinaryOffice(it, "DOC") } ?: emptyList()
                }
            }
            DocType.XLS -> openStream(context, uri)?.use { parseLegacyBinaryOffice(it, "XLS") } ?: emptyList()
            DocType.IMAGE -> listOf(
                DocSection(
                    index = 1,
                    label = "Imagen 1",
                    title = cleanTitle,
                    content = "[Documento visual / imagen: $fileName. Activa el análisis visual o pregunta al Docente para leer texto, ecuaciones, gráficos o apuntes de esta imagen.]",
                    isVisualPage = true
                )
            )
            DocType.TEXT, DocType.UNKNOWN -> {
                val isHtml = fileName.lowercase().endsWith(".html") || fileName.lowercase().endsWith(".htm") || mimeType.contains("html")
                openStream(context, uri)?.use { parsePlainOrHtml(it, isHtml) } ?: emptyList()
            }
        }

        val finalSections = if (sections.isEmpty()) {
            listOf(
                DocSection(
                    index = 1,
                    label = "Sección 1",
                    title = cleanTitle,
                    content = "[No se pudo extraer texto plano de $fileName. Si es un documento escaneado o visual, puedes abrir el Lector y usar 'Analizar página visualmente'.]",
                    isVisualPage = docType == DocType.PDF || docType == DocType.IMAGE
                )
            )
        } else sections

        ParsedDocumentResult(
            fileName = fileName,
            title = cleanTitle,
            mimeType = mimeType,
            docType = docType,
            sections = finalSections,
            totalChars = finalSections.sumOf { it.content.length }
        )
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            FileInputStream(File(uri.path!!))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun parsePdf(context: Context, uri: Uri): List<DocSection> {
        ensurePdfBox(context)
        val sections = mutableListOf<DocSection>()
        openStream(context, uri)?.use { input ->
            PDDocument.load(input).use { pdDoc ->
                val totalPages = pdDoc.numberOfPages
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                for (page in 1..totalPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val rawText = runCatching { stripper.getText(pdDoc) }.getOrDefault("")
                    val cleaned = cleanWhitespace(rawText)
                    val isLikelyScan = cleaned.length < 35
                    val firstLine = cleaned.lineSequence().firstOrNull { it.trim().length > 3 }?.take(72)
                        ?: if (isLikelyScan) "Página visual / escaneada $page" else "Página $page"
                    val content = if (isLikelyScan && cleaned.isBlank()) {
                        "[Página $page con contenido visual, gráfico o escaneado sin texto incrustado. Usa 'Analizar visualmente' en el Lector para leerla con IA multimodal.]"
                    } else {
                        cleaned
                    }
                    sections.add(
                        DocSection(
                            index = page,
                            label = "Pág. $page",
                            title = firstLine,
                            content = content,
                            isVisualPage = isLikelyScan
                        )
                    )
                }
            }
        }
        return sections
    }

    private fun newXmlPullParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser()
    }

    internal fun parseDocx(input: InputStream): List<DocSection> {
        val entries = readZipEntries(input, setOf("word/document.xml", "word/footnotes.xml"))
        val docXml = entries["word/document.xml"] ?: return emptyList()
        val footnotesXml = entries["word/footnotes.xml"]

        val paragraphs = extractWordXmlParagraphs(docXml)
        if (footnotesXml != null) {
            val notes = extractWordXmlParagraphs(footnotesXml)
            if (notes.isNotEmpty()) {
                paragraphs.add("## Notas al pie")
                paragraphs.addAll(notes)
            }
        }
        return chunkParagraphsIntoSections(paragraphs, prefix = "Sección")
    }

    private fun extractWordXmlParagraphs(xmlBytes: ByteArray): MutableList<String> {
        val result = mutableListOf<String>()
        val parser = newXmlPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inParagraph = false
        var inTextRun = false
        var inTableRow = false
        var isHeading = false
        val currentPara = StringBuilder()
        val rowCells = mutableListOf<String>()
        val currentCell = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "w:tr" -> {
                            inTableRow = true
                            rowCells.clear()
                        }
                        "w:tc" -> currentCell.setLength(0)
                        "w:p" -> {
                            inParagraph = true
                            isHeading = false
                            currentPara.setLength(0)
                        }
                        "w:t" -> if (inParagraph) inTextRun = true
                        "w:pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "w:val") ?: ""
                            if (styleVal.contains("Heading", ignoreCase = true) ||
                                styleVal.contains("Title", ignoreCase = true) ||
                                styleVal.contains("Ttulo", ignoreCase = true) ||
                                styleVal.contains("Título", ignoreCase = true)
                            ) {
                                isHeading = true
                            }
                        }
                        "w:tab" -> if (inParagraph) currentPara.append("\t")
                        "w:br" -> if (inParagraph) currentPara.append("\n")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph && inTextRun) {
                        val t = parser.text ?: ""
                        if (t.isNotEmpty()) currentPara.append(t)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "w:t" -> inTextRun = false
                        "w:p" -> {
                            val text = currentPara.toString().trim()
                            if (text.isNotEmpty()) {
                                if (inTableRow) {
                                    if (currentCell.isNotEmpty()) currentCell.append(" ")
                                    currentCell.append(text)
                                } else {
                                    result.add(if (isHeading) "## $text" else text)
                                }
                            }
                            inParagraph = false
                            inTextRun = false
                        }
                        "w:tc" -> {
                            rowCells.add(currentCell.toString().trim())
                            currentCell.setLength(0)
                        }
                        "w:tr" -> {
                            if (rowCells.any { it.isNotBlank() }) {
                                result.add("| " + rowCells.joinToString(" | ") + " |")
                            }
                            inTableRow = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    internal fun parseXlsx(input: InputStream): List<DocSection> {
        val allEntries = readAllZipEntriesFiltered(input) { name ->
            name == "xl/sharedStrings.xml" ||
                name == "xl/workbook.xml" ||
                name == "xl/_rels/workbook.xml.rels" ||
                (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
        }

        val sharedStrings = allEntries["xl/sharedStrings.xml"]?.let { parseXlsxSharedStrings(it) } ?: emptyList()
        val sheetNames = parseXlsxSheetNames(allEntries["xl/workbook.xml"], allEntries["xl/_rels/workbook.xml.rels"])

        val sheetEntries = allEntries.entries
            .filter { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }
            .sortedBy { extractTrailingNumber(it.key) }

        val sections = mutableListOf<DocSection>()
        var sectionIdx = 1
        for ((idx, entry) in sheetEntries.withIndex()) {
            val targetName = entry.key.removePrefix("xl/")
            val sheetTitle = sheetNames[targetName] ?: sheetNames["/$targetName"] ?: "Hoja ${idx + 1}"
            val rows = parseXlsxSheetRows(entry.value, sharedStrings)
            if (rows.isEmpty()) continue

            val headerLine = rows.firstOrNull()?.let { "| " + it.joinToString(" | ") + " |" } ?: ""
            val chunkSize = 45
            var startRow = 0
            while (startRow < rows.size) {
                val endRow = min(startRow + chunkSize, rows.size)
                val sb = StringBuilder()
                sb.appendLine("Hoja: $sheetTitle (Filas ${startRow + 1} a $endRow de ${rows.size})")
                if (startRow > 0 && headerLine.isNotBlank()) {
                    sb.appendLine("Encabezado: $headerLine")
                }
                for (r in startRow until endRow) {
                    val rowStr = rows[r].joinToString(" | ")
                    sb.appendLine("F${r + 1}: | $rowStr |")
                }
                val label = if (rows.size <= chunkSize) {
                    "Hoja: $sheetTitle"
                } else {
                    "Hoja: $sheetTitle (F${startRow + 1}-$endRow)"
                }
                sections.add(
                    DocSection(
                        index = sectionIdx++,
                        label = label,
                        title = "$sheetTitle · Filas ${startRow + 1}-$endRow",
                        content = sb.toString().trim()
                    )
                )
                startRow = endRow
            }
        }
        return sections
    }

    private fun parseXlsxSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = newXmlPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        sb.setLength(0)
                    } else if (inSi && parser.name == "t") {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> if (inSi && inT) sb.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") {
                        list.add(sb.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseXlsxSheetNames(workbookXml: ByteArray?, relsXml: ByteArray?): Map<String, String> {
        if (workbookXml == null) return emptyMap()
        val relIdToTarget = mutableMapOf<String, String>()
        if (relsXml != null) {
            runCatching {
                val p = newXmlPullParser()
                p.setInput(ByteArrayInputStream(relsXml), "UTF-8")
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                        val id = p.getAttributeValue(null, "Id")
                        val target = p.getAttributeValue(null, "Target")
                        if (id != null && target != null) relIdToTarget[id] = target.removePrefix("/").removePrefix("xl/")
                    }
                    ev = p.next()
                }
            }
        }
        val targetToName = mutableMapOf<String, String>()
        runCatching {
            val p = newXmlPullParser()
            p.setInput(ByteArrayInputStream(workbookXml), "UTF-8")
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                    val name = p.getAttributeValue(null, "name") ?: "Hoja"
                    val rId = p.getAttributeValue(null, "r:id")
                    val target = rId?.let { relIdToTarget[it] }
                    if (target != null) targetToName[target] = name
                }
                ev = p.next()
            }
        }
        return targetToName
    }

    private fun parseXlsxSheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = newXmlPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellRef = ""
        var inV = false
        var inInlineT = false
        val cellVal = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellRef = parser.getAttributeValue(null, "r") ?: ""
                            cellVal.setLength(0)
                        }
                        "v" -> inV = true
                        "t" -> inInlineT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inInlineT) cellVal.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inV = false
                        "t" -> inInlineT = false
                        "c" -> {
                            val raw = cellVal.toString().trim()
                            val resolved = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (resolved.isNotBlank()) {
                                val prefix = if (cellRef.isNotBlank()) "$cellRef=" else ""
                                currentRow.add("$prefix$resolved")
                            }
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) rows.add(currentRow)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    internal fun parsePptx(input: InputStream): List<DocSection> {
        val allEntries = readAllZipEntriesFiltered(input) { name ->
            (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) ||
                (name.startsWith("ppt/notesSlides/notesSlide") && name.endsWith(".xml"))
        }
        val slides = allEntries.entries
            .filter { it.key.startsWith("ppt/slides/slide") && it.key.endsWith(".xml") }
            .sortedBy { extractTrailingNumber(it.key) }

        val sections = mutableListOf<DocSection>()
        for ((idx, entry) in slides.withIndex()) {
            val slideNum = extractTrailingNumber(entry.key).takeIf { it > 0 } ?: (idx + 1)
            val slideText = extractXmlParagraphText(entry.value, paragraphTags = setOf("a:p"), textTags = setOf("a:t"))
            val notesKey = "ppt/notesSlides/notesSlide$slideNum.xml"
            val notesText = allEntries[notesKey]?.let {
                extractXmlParagraphText(it, paragraphTags = setOf("a:p"), textTags = setOf("a:t"))
            } ?: ""
            val combined = buildString {
                append(slideText)
                if (notesText.isNotBlank()) {
                    append("\n\n[Notas del expositor]:\n")
                    append(notesText)
                }
            }.trim()
            val title = slideText.lineSequence().firstOrNull { it.trim().length > 2 }?.take(70) ?: "Diapositiva $slideNum"
            sections.add(
                DocSection(
                    index = slideNum,
                    label = "Diap. $slideNum",
                    title = title,
                    content = combined.ifBlank { "[Diapositiva $slideNum visual sin texto]" },
                    isVisualPage = combined.isBlank()
                )
            )
        }
        return sections
    }

    internal fun parseEpub(input: InputStream): List<DocSection> {
        val entries = readAllZipEntriesFiltered(input) { name ->
            val low = name.lowercase()
            low.endsWith(".xhtml") || low.endsWith(".html") || low.endsWith(".htm") || low.endsWith(".opf")
        }
        val opfEntry = entries.entries.firstOrNull { it.key.lowercase().endsWith(".opf") }
        val orderedKeys = if (opfEntry != null) {
            extractEpubSpinePaths(opfEntry.key, opfEntry.value).filter { it in entries }
        } else {
            emptyList()
        }
        val htmlFiles: List<Map.Entry<String, ByteArray>> = if (orderedKeys.isNotEmpty()) {
            orderedKeys.mapNotNull { key -> entries.entries.firstOrNull { it.key == key } }
        } else {
            entries.entries
                .filter {
                    val low = it.key.lowercase()
                    low.endsWith(".xhtml") || low.endsWith(".html") || low.endsWith(".htm")
                }
                .sortedBy { it.key }
        }

        val sections = mutableListOf<DocSection>()
        var capIdx = 1
        for (entry in htmlFiles) {
            val html = entry.value.toString(Charsets.UTF_8)
            val plain = stripHtmlToText(html)
            if (plain.length < 40) continue
            val heading = plain.lineSequence().firstOrNull { it.trim().length > 3 }?.take(72) ?: "Capítulo $capIdx"
            sections.add(
                DocSection(
                    index = capIdx,
                    label = "Cap. $capIdx",
                    title = heading,
                    content = plain
                )
            )
            capIdx++
        }
        return sections
    }

    private fun extractEpubSpinePaths(opfPath: String, opfBytes: ByteArray): List<String> {
        val baseDir = opfPath.substringBeforeLast('/', "")
        val idToHref = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        runCatching {
            val p = newXmlPullParser()
            p.setInput(ByteArrayInputStream(opfBytes), "UTF-8")
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> {
                            val id = p.getAttributeValue(null, "id")
                            val href = p.getAttributeValue(null, "href")
                            if (id != null && href != null) idToHref[id] = href
                        }
                        "itemref" -> {
                            val idref = p.getAttributeValue(null, "idref")
                            if (idref != null) spineIds.add(idref)
                        }
                    }
                }
                ev = p.next()
            }
        }
        return spineIds.mapNotNull { id ->
            val href = idToHref[id]?.substringBefore('#') ?: return@mapNotNull null
            if (baseDir.isBlank()) href else "$baseDir/$href"
        }
    }

    internal fun parseOdfText(input: InputStream): List<DocSection> {
        val entries = readZipEntries(input, setOf("content.xml"))
        val xml = entries["content.xml"] ?: return emptyList()
        val text = extractXmlParagraphText(xml, paragraphTags = setOf("text:p", "text:h"), textTags = setOf("text:p", "text:h", "text:span"))
        val paragraphs = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return chunkParagraphsIntoSections(paragraphs, "Sección")
    }

    internal fun parseOdsSpreadsheet(input: InputStream): List<DocSection> {
        val entries = readZipEntries(input, setOf("content.xml"))
        val xml = entries["content.xml"] ?: return emptyList()
        val text = extractXmlParagraphText(xml, paragraphTags = setOf("text:p"), textTags = setOf("text:p"))
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return chunkParagraphsIntoSections(lines, "Hoja")
    }

    internal fun parseCsvOrTsv(input: InputStream, isTsv: Boolean): List<DocSection> {
        val raw = readTextWithCharsetFallback(input)
        val sep = if (isTsv) '\t' else detectCsvDelimiter(raw)
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first()
        val sections = mutableListOf<DocSection>()
        val chunkSize = 55
        var idx = 1
        var row = 0
        while (row < lines.size) {
            val end = min(row + chunkSize, lines.size)
            val sb = StringBuilder()
            if (row > 0) sb.appendLine("Encabezado: $header")
            for (i in row until end) {
                val cols = splitCsvLine(lines[i], sep)
                sb.appendLine("F${i + 1}: | ${cols.joinToString(" | ")} |")
            }
            sections.add(
                DocSection(
                    index = idx++,
                    label = "Tabla F${row + 1}-$end",
                    title = "Filas ${row + 1} a $end",
                    content = sb.toString().trim()
                )
            )
            row = end
        }
        return sections
    }

    internal fun parseRtf(input: InputStream): List<DocSection> {
        val raw = readTextWithCharsetFallback(input)
        val stripped = raw
            .replace(Regex("""\\'[0-9a-fA-F]{2}""")) { m ->
                val code = m.value.substring(2).toIntOrNull(16) ?: 32
                code.toChar().toString()
            }
            .replace(Regex("""\\par\b"""), "\n")
            .replace(Regex("""\\line\b"""), "\n")
            .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
            .replace("{", "")
            .replace("}", "")
        val paragraphs = cleanWhitespace(stripped).lines().map { it.trim() }.filter { it.isNotEmpty() }
        return chunkParagraphsIntoSections(paragraphs, "Sección")
    }

    private fun parseLegacyBinaryOffice(input: InputStream, kind: String): List<DocSection> {
        val bytes = input.readBytes()
        // Intenta extraer cadenas UTF-16LE legibles (formato Word 97-2003 / Excel BIFF)
        val runs = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            val ch = ((hi shl 8) or lo).toChar()
            if (ch == '\r' || ch == '\n' || ch == '\t' || (ch.code in 32..126) || (ch.code in 160..255)) {
                sb.append(if (ch == '\r') '\n' else ch)
            } else {
                if (sb.length >= 20) runs.add(sb.toString().trim())
                sb.setLength(0)
            }
            i += 2
        }
        if (sb.length >= 20) runs.add(sb.toString().trim())
        val extracted = runs.filter { run ->
            val letters = run.count { it.isLetter() }
            letters > run.length * 0.45
        }
        if (extracted.isEmpty()) {
            return listOf(
                DocSection(
                    index = 1,
                    label = "Sección 1",
                    title = "Archivo legado .$kind",
                    content = "[Archivo en formato binario antiguo .$kind con protección o estructura propietaria. Guárdalo como .docx / .xlsx / .pdf para máxima fidelidad.]"
                )
            )
        }
        return chunkParagraphsIntoSections(extracted, "Sección")
    }

    internal fun parsePlainOrHtml(input: InputStream, isHtml: Boolean): List<DocSection> {
        val raw = readTextWithCharsetFallback(input)
        val text = if (isHtml) stripHtmlToText(raw) else cleanWhitespace(raw)
        val paragraphs = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        return chunkParagraphsIntoSections(paragraphs, "Sección")
    }

    private fun chunkParagraphsIntoSections(paragraphs: List<String>, prefix: String): List<DocSection> {
        if (paragraphs.isEmpty()) return emptyList()
        val sections = mutableListOf<DocSection>()
        val current = StringBuilder()
        var currentTitle = paragraphs.first().removePrefix("## ").take(72)
        var secIdx = 1
        val targetChars = 2600

        for (p in paragraphs) {
            val isHeading = p.startsWith("## ") || p.startsWith("# ")
            if (isHeading && current.length > 600) {
                sections.add(
                    DocSection(
                        index = secIdx,
                        label = "$prefix $secIdx",
                        title = currentTitle,
                        content = current.toString().trim()
                    )
                )
                secIdx++
                current.setLength(0)
                currentTitle = p.removePrefix("## ").removePrefix("# ").take(72)
            }
            if (current.length + p.length > targetChars && current.isNotEmpty()) {
                sections.add(
                    DocSection(
                        index = secIdx,
                        label = "$prefix $secIdx",
                        title = currentTitle,
                        content = current.toString().trim()
                    )
                )
                secIdx++
                current.setLength(0)
                currentTitle = p.removePrefix("## ").removePrefix("# ").take(72)
            }
            current.appendLine(p)
        }
        if (current.isNotBlank()) {
            sections.add(
                DocSection(
                    index = secIdx,
                    label = "$prefix $secIdx",
                    title = currentTitle,
                    content = current.toString().trim()
                )
            )
        }
        return sections
    }

    private fun readZipEntries(input: InputStream, targets: Set<String>): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name in targets) {
                    map[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun readAllZipEntriesFiltered(input: InputStream, predicate: (String) -> Boolean): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name)) {
                    map[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun extractXmlParagraphText(
        xmlBytes: ByteArray,
        paragraphTags: Set<String>,
        textTags: Set<String>
    ): String {
        val sb = StringBuilder()
        runCatching {
            val parser = newXmlPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var textDepth = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name in textTags) textDepth++
                    }
                    XmlPullParser.TEXT -> if (textDepth > 0) {
                        val txt = parser.text ?: ""
                        if (txt.isNotEmpty()) sb.append(txt)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name in textTags) textDepth = max(0, textDepth - 1)
                        if (parser.name in paragraphTags) sb.append('\n')
                    }
                }
                event = parser.next()
            }
        }
        return cleanWhitespace(sb.toString())
    }

    private fun stripHtmlToText(html: String): String {
        val noScripts = html
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?i)<(br|p|div|li|tr|h[1-6])[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return cleanWhitespace(noScripts)
    }

    private fun readTextWithCharsetFallback(input: InputStream): String {
        val bytes = input.readBytes()
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.count { it == '\uFFFD' } > max(4, utf8.length / 80)) {
            bytes.toString(Charset.forName("ISO-8859-1"))
        } else {
            utf8
        }
    }

    private fun detectCsvDelimiter(sample: String): Char {
        val firstLine = sample.lineSequence().firstOrNull() ?: return ','
        val commas = firstLine.count { it == ',' }
        val semis = firstLine.count { it == ';' }
        return if (semis > commas) ';' else ','
    }

    private fun splitCsvLine(line: String, sep: Char): List<String> {
        val cols = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == sep && !inQuotes -> {
                    cols.add(cur.toString().trim())
                    cur.setLength(0)
                }
                else -> cur.append(c)
            }
        }
        cols.add(cur.toString().trim())
        return cols
    }

    private fun extractTrailingNumber(path: String): Int {
        val match = Regex("(\\d+)\\.xml$").find(path)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun cleanWhitespace(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("[ \t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Renderiza una página de PDF a Bitmap nítido para el Lector visual o para análisis multimodal (Gemini / NVIDIA VLM).
     */
    suspend fun renderPdfPageBitmap(
        context: Context,
        sourceUri: String,
        localPath: String?,
        pageIndexZeroBased: Int,
        targetWidthPx: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val pfd: ParcelFileDescriptor? = when {
                !localPath.isNullOrBlank() && File(localPath).exists() ->
                    ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
                sourceUri.startsWith("file://") ->
                    ParcelFileDescriptor.open(File(Uri.parse(sourceUri).path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                else ->
                    context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")
            }
            pfd?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) {
                        null
                    } else {
                        val safeIdx = pageIndexZeroBased.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safeIdx).use { page ->
                            val scale = targetWidthPx.toFloat() / max(1, page.width)
                            val w = targetWidthPx.coerceAtMost(1800)
                            val h = (page.height * scale).toInt().coerceIn(200, 2600)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun loadImageBitmap(
        context: Context,
        sourceUri: String,
        localPath: String?,
        maxSidePx: Int = 1400
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val stream: InputStream? = when {
                !localPath.isNullOrBlank() && File(localPath).exists() -> FileInputStream(File(localPath))
                sourceUri.startsWith("file://") -> FileInputStream(File(Uri.parse(sourceUri).path!!))
                else -> context.contentResolver.openInputStream(Uri.parse(sourceUri))
            }
            val raw = stream?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val maxDim = max(raw.width, raw.height)
            if (maxDim <= maxSidePx) raw
            else {
                val scale = maxSidePx.toFloat() / maxDim
                Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
            }
        }.getOrNull()
    }

    fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 82): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
