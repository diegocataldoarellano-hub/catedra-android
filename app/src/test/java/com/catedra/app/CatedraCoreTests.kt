package com.catedra.app

import com.catedra.app.data.ai.DocenteAiEngine
import com.catedra.app.data.local.WhatsAppDocsScanner
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.DocSection
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.parser.UniversalDocumentParser
import com.catedra.app.data.remote.AcademicPaperService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CatedraCoreTests {

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun testDocxParserExtractsHeadingsTablesAndIgnoresFieldCodes() {
        val docXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Introducción a la Epistemología</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:instrText>HYPERLINK "http://ignore.me"</w:instrText></w:r>
                  <w:r><w:t>Karl Popper propuso la falsabilidad como criterio de demarcación.</w:t></w:r>
                  <w:del><w:r><w:delText>TEXTO BORRADO QUE NO DEBE SALIR</w:delText></w:r></w:del>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Autor</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Concepto</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Kuhn</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Paradigma</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val zipBytes = buildZip(mapOf("word/document.xml" to docXml))
        val sections = UniversalDocumentParser.parseDocx(ByteArrayInputStream(zipBytes))
        assertTrue(sections.isNotEmpty())
        val content = sections.joinToString("\n") { it.content }
        assertTrue(content.contains("Introducción a la Epistemología"))
        assertTrue(content.contains("Karl Popper propuso la falsabilidad"))
        assertTrue(content.contains("| Kuhn | Paradigma |"))
        assertFalse("Debe ignorar instrText", content.contains("HYPERLINK"))
        assertFalse("Debe ignorar delText", content.contains("TEXTO BORRADO"))
    }

    @Test
    fun testXlsxParserExtractsSheetNamesSharedStringsAndNumbers() {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Presupuesto2026" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships>
              <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst>
              <si><t>Partida</t></si>
              <si><t>Monto</t></si>
              <si><t>Investigación</t></si>
            </sst>
        """.trimIndent()
        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet>
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>2</v></c>
                  <c r="B2"><v>45000</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val zipBytes = buildZip(
            mapOf(
                "xl/workbook.xml" to workbookXml,
                "xl/_rels/workbook.xml.rels" to relsXml,
                "xl/sharedStrings.xml" to sharedStringsXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml
            )
        )
        val sections = UniversalDocumentParser.parseXlsx(ByteArrayInputStream(zipBytes))
        assertEquals(1, sections.size)
        assertEquals("Hoja: Presupuesto2026", sections[0].label)
        assertTrue(sections[0].content.contains("A2=Investigación"))
        assertTrue(sections[0].content.contains("B2=45000"))
    }

    @Test
    fun testPptxParserPreservesSentencesAndSpeakerNotes() {
        val slide1 = """
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree><p:sp><p:txBody>
                <a:p><a:r><a:t>Neuroplasticidad </a:t></a:r><a:r><a:t>sináptica</a:t></a:r></a:p>
                <a:p><a:r><a:t>Potenciación a largo plazo.</a:t></a:r></a:p>
              </p:txBody></p:sp></p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()
        val notes1 = """
            <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <a:p><a:r><a:t>Recordar citar el experimento de Bliss y Lomo.</a:t></a:r></a:p>
            </p:notes>
        """.trimIndent()

        val zipBytes = buildZip(
            mapOf(
                "ppt/slides/slide1.xml" to slide1,
                "ppt/notesSlides/notesSlide1.xml" to notes1
            )
        )
        val sections = UniversalDocumentParser.parsePptx(ByteArrayInputStream(zipBytes))
        assertEquals(1, sections.size)
        assertEquals("Diap. 1", sections[0].label)
        assertTrue(sections[0].content.contains("Neuroplasticidad sináptica"))
        assertTrue(sections[0].content.contains("Bliss y Lomo"))
    }

    @Test
    fun testEpubParserFollowsOpfSpineOrder() {
        val opf = """
            <package>
              <manifest>
                <item id="c1" href="ch_intro.xhtml"/>
                <item id="c2" href="ch_final.xhtml"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine>
            </package>
        """.trimIndent()
        val ch1 = "<html><body><h1>Primero</h1><p>Este es el primer capítulo de prueba con suficiente longitud para ser indexado.</p></body></html>"
        val ch2 = "<html><body><h1>Segundo</h1><p>Este es el segundo capítulo de conclusiones con suficiente longitud para ser indexado.</p></body></html>"
        val zipBytes = buildZip(
            mapOf(
                "OEBPS/content.opf" to opf,
                "OEBPS/ch_final.xhtml" to ch2,
                "OEBPS/ch_intro.xhtml" to ch1
            )
        )
        val sections = UniversalDocumentParser.parseEpub(ByteArrayInputStream(zipBytes))
        assertEquals(2, sections.size)
        assertTrue(sections[0].content.contains("Primero"))
        assertTrue(sections[1].content.contains("Segundo"))
    }

    @Test
    fun testCsvAndRtfAndDocTypeDetection() {
        val csv = "Nombre;Nota;Observacion\nAna;6.8;Excelente analisis\nLuis;5.9;Revisar citas"
        val csvSections = UniversalDocumentParser.parseCsvOrTsv(ByteArrayInputStream(csv.toByteArray()), false)
        assertEquals(1, csvSections.size)
        assertTrue(csvSections[0].content.contains("Ana | 6.8 | Excelente analisis"))

        val rtf = """{\rtf1\ansi\deff0 Hola mundo\par Segundo p\'e1rrafo de estudio.}"""
        val rtfSections = UniversalDocumentParser.parseRtf(ByteArrayInputStream(rtf.toByteArray()))
        assertTrue(rtfSections.first().content.contains("Segundo párrafo de estudio"))

        assertEquals(DocType.PDF, DocType.fromFileNameAndMime("paper.pdf", "application/pdf"))
        assertEquals(DocType.XLSX, DocType.fromFileNameAndMime("datos.xlsx", null))
        assertEquals(DocType.IMAGE, DocType.fromFileNameAndMime("pizarra.jpg", "image/jpeg"))
        assertEquals("3.5 KB", formatCompactSize(3584L))
    }

    @Test
    fun testDocenteBm25CitationsThinkTagsAndNoteDistillation() {
        val engine = DocenteAiEngine()
        val sections = listOf(
            DocSection(1, "Pág. 1", "Introducción", "Historia general de la estadística clásica y muestreo."),
            DocSection(2, "Pág. 2", "Teorema de Bayes", "La probabilidad posterior combina el prior con la verosimilitud observada."),
            DocSection(3, "Pág. 3", "Conclusiones", "Resumen final y referencias bibliográficas.")
        )

        val ranked = engine.rankSectionsBm25(sections, "como funciona la probabilidad posterior y verosimilitud de Bayes")
        assertEquals(1, ranked.first()) // índice 1 = Pág. 2

        val replyWithThink = "<think>El estudiante confunde correlación con causalidad.</think>Muy buena observación, pero mira [Pág. 2] y [Pág. 3]."
        val (clean, reasoning) = engine.splitThinkTags(replyWithThink, null)
        assertEquals("Muy buena observación, pero mira [Pág. 2] y [Pág. 3].", clean)
        assertTrue(reasoning!!.contains("correlación"))

        val cites = engine.extractCitationLabels(clean, sections)
        assertEquals(listOf("Pág. 2", "Pág. 3"), cites)

        val distilledJson = """
            ```json
            [
              {"category":"COMPRENDIDO","citation":"Pág. 2","content":"El teorema de Bayes actualiza creencias previas con nueva evidencia."},
              {"category":"ERROR_CORREGIDO","citation":"Pág. 2","content":"Antes confundía prior con verosimilitud; son factores distintos."}
            ]
            ```
        """.trimIndent()
        val drafts = engine.parseDistilledJson(distilledJson)
        assertEquals(2, drafts.size)
        assertEquals(NoteCategory.COMPRENDIDO, drafts[0].category)
        assertEquals(NoteCategory.ERROR_CORREGIDO, drafts[1].category)
    }

    @Test
    fun testAcademicPaperServiceLiveSmoke() = runBlocking {
        val service = AcademicPaperService()
        val res = service.searchPapers("spaced repetition memory learning", onlyOpenAccess = true, sourceFilter = AcademicPaperService.MODE_ALL)
        assertTrue("La consulta académica debe responder sin excepción: ${res.exceptionOrNull()}", res.isSuccess)
        val papers = res.getOrDefault(emptyList())
        assertTrue("Debe retornar resultados descargables corroborables", papers.isNotEmpty())
        assertTrue(papers.first().title.isNotBlank())
        assertTrue(papers.first().hasDirectDownload)

        // Verificar modo Solo DOI (CrossRef + DOAJ + Europe PMC)
        val doiRes = service.searchPapers("epistemologia", onlyOpenAccess = false, sourceFilter = AcademicPaperService.MODE_DOI_ONLY)
        assertTrue(doiRes.isSuccess)
        val doiList = doiRes.getOrDefault(emptyList())
        assertTrue("Debe retornar documentos con DOI verificado", doiList.isNotEmpty() && doiList.all { it.hasVerifiedDoi })

        // Verificar modo Libros Gratuitos (Gutendex EPUB/TXT + Open Library)
        val bookRes = service.searchPapers("kant", onlyOpenAccess = false, sourceFilter = AcademicPaperService.MODE_FREE_BOOKS)
        assertTrue(bookRes.isSuccess)
        val books = bookRes.getOrDefault(emptyList())
        assertTrue("Debe encontrar libros gratuitos descargables", books.any { it.isBook && it.hasDirectDownload })

        // Verificar Unpaywall legal PDF a partir de un DOI conocido
        val pdfFromDoi = service.resolveUnpaywallPdf("10.1038/nature12373")
        assertTrue("Unpaywall debe resolver PDF legal para DOI abierto", !pdfFromDoi.isNullOrBlank())

        // Verificar Google Scholar URL + Citas APA 7 y BibTeX
        val scholarUrl = service.buildGoogleScholarUrl("epistemología crítica")
        assertTrue(scholarUrl.startsWith("https://scholar.google.com/scholar?hl=es&q="))
        val apa = service.formatApaCitation(papers.first())
        val bib = service.formatBibTexCitation(papers.first())
        assertTrue(apa.contains(papers.first().title))
        assertTrue(bib.startsWith("@article{") || bib.startsWith("@book{"))
    }

    @Test
    fun testQuizParserGoogleFormsScriptAndCosineSimilarity() {
        val engine = DocenteAiEngine()
        val rawQuiz = """
            ```json
            [
              {
                "difficulty": "Fácil",
                "question": "¿Qué distingue la falsabilidad de Popper?",
                "hint": "Piensa en qué hace que una hipótesis pueda ponerse a prueba para descartarse.",
                "options": ["Confirmación inductiva", "Posibilidad de refutación empírica", "Consenso social", "Autoridad académica"],
                "correctIndex": 1,
                "citation": "Pág. 4",
                "explanation": "Popper sostiene que una teoría es científica si puede ser refutada.",
                "misconception": "Confundir verificación repetida con falsabilidad."
              }
            ]
            ```
        """.trimIndent()
        val questions = engine.parseQuizJson(rawQuiz)
        assertEquals(1, questions.size)
        assertEquals(1, questions[0].correctIndex)
        assertEquals("Pág. 4", questions[0].citationRef)
        assertEquals("Fácil", questions[0].difficultyLabel)
        assertTrue(questions[0].hint.contains("hipótesis"))

        val rawCards = """
            [{"difficulty":"Difícil","front":"¿Qué es un paradigma según Kuhn?","hint":"Relaciónalo con la ciencia normal","back":"Marco compartido de supuestos y métodos de una comunidad científica.","citation":"Pág. 12"}]
        """.trimIndent()
        val cards = engine.parseFlashcardsJson(rawCards)
        assertEquals(1, cards.size)
        assertEquals("Difícil", cards[0].difficultyLabel)
        assertEquals("Pág. 12", cards[0].citationRef)

        val script = engine.buildGoogleFormsAppsScript("Epistemología", questions)
        assertTrue(script.contains("FormApp.create"))
        assertTrue(script.contains("form.setIsQuiz(true)"))
        assertTrue(script.contains("Posibilidad de refutación empírica"))

        val simSame = engine.cosineSimilarity(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(2.0, 4.0, 6.0))
        val simOrtho = engine.cosineSimilarity(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        assertTrue(simSame > 0.999)
        assertTrue(simOrtho < 0.001)
    }

    @Test
    fun testWhatsAppScannerStrictDocumentFilter() {
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("clase_semana3.pdf"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("Resumen_Final.DOCX"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("planilla_notas.xlsx"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("presentacion.pptx"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("libro.epub"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("datos.csv"))
        assertTrue(WhatsAppDocsScanner.isAllowedDocumentName("apunte.txt"))
        // Estrictamente excluidos: fotos, videos, audios, APKs y comprimidos
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("IMG-20261119-WA0001.jpg"))
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("VID-20261119-WA0002.mp4"))
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("PTT-20261119-WA0003.opus"))
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("foto.png"))
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("sticker.webp"))
        assertFalse(WhatsAppDocsScanner.isAllowedDocumentName("app.apk"))
    }

    @Test
    fun testTruncatedQuizJsonRecoveryAndEpubUrlDecodedSpine() {
        val engine = DocenteAiEngine()
        // Simula una salida de 15 preguntas donde el modelo se truncó a mitad de la pregunta 3 (sin cerrar ']')
        val truncatedQuiz = """
            Aquí tienes el cuestionario:
            [
              {
                "difficulty": "Fácil",
                "question": "¿Cuál es la tesis central del capítulo 1?",
                "hint": "Revisa el primer párrafo del capítulo.",
                "options": ["Opción 1", "Opción 2", "Opción 3", "Opción 4"],
                "correctIndex": 0,
                "citation": "Pág. 1",
                "explanation": "El autor afirma explícitamente la opción 1.",
                "misconception": "Confundir ejemplo con tesis."
              },
              {
                "difficulty": "Difícil",
                "question": "¿Qué supuesto implícito subyace al argumento?",
                "hint": "Fíjate en la premisa sobre causalidad.",
                "options": ["A", "B", "C", "D"],
                "correctIndex": 2,
                "citation": "Pág. 9",
                "explanation": "C sintetiza la premisa oculta.",
                "misconception": "Ignorar el alcance del método."
              },
              {
                "difficulty": "Difícil",
                "question": "Pregunta truncada sin cierre
        """.trimIndent()
        val recovered = engine.parseQuizJson(truncatedQuiz)
        assertEquals("Debe recuperar las 2 preguntas completas previas al truncado", 2, recovered.size)
        assertEquals(1, recovered[0].id)
        assertEquals(2, recovered[1].id)
        assertEquals("Difícil", recovered[1].difficultyLabel)

        // Verifica EPUB con href codificado con %20 (espacios) en content.opf
        val opf = """
            <package>
              <manifest>
                <item id="c1" href="chapter%201.xhtml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
        """.trimIndent()
        val ch1 = "<html><body><h1>Capítulo Uno</h1><p>Contenido suficientemente largo para pasar el umbral mínimo de indexación de EPUB.</p></body></html>"
        val zipBytes = buildZip(mapOf("OEBPS/content.opf" to opf, "OEBPS/chapter 1.xhtml" to ch1))
        val sections = UniversalDocumentParser.parseEpub(ByteArrayInputStream(zipBytes))
        assertEquals(1, sections.size)
        assertTrue(sections[0].content.contains("Capítulo Uno"))
    }
}
