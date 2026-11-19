package com.catedra.app.data.remote

import com.catedra.app.data.model.PaperResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Motor académico y bibliográfico de FUENTES CORROBORABLES (100% gratuitas y verificables):
 * - DOAJ: revistas científicas revisadas por pares 100% Open Access con DOI (excelente en español e inglés)
 * - CrossRef: 150M+ registros DOI oficiales + búsqueda dedicada por autor
 * - Europe PMC: artículos revisados por pares con DOI, PMCID y PDFs abiertos
 * - OpenAlex: 250M+ trabajos multidisciplinarios con DOI y citas
 * - arXiv: preprints oficiales con PDF directo y búsqueda por autor
 * - Unpaywall: localiza PDFs legales de acceso abierto a partir de cualquier DOI
 * - Gutendex (Project Gutenberg): 70.000+ libros clásicos gratuitos en EPUB/TXT directo
 * - Open Library & Internet Archive: libros académicos y de dominio público con ISBN / ID verificable
 */
class AcademicPaperService {

    companion object {
        const val MODE_ALL = "ALL"
        const val MODE_DOI_ONLY = "DOI_ONLY"
        const val MODE_FREE_BOOKS = "FREE_BOOKS"
        const val MODE_AUTHOR = "AUTHOR"
        const val MODE_ARXIV = "ARXIV"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun JSONObject.optCleanString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key, "").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    }

    suspend fun searchPapers(
        query: String,
        onlyOpenAccess: Boolean = false,
        sourceFilter: String = MODE_ALL,
        sortByCitations: Boolean = false
    ): Result<List<PaperResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            require(clean.isNotBlank()) { "Escribe un tema, autor, título, libro o DOI para buscar." }

            val isAuthorMode = sourceFilter == MODE_AUTHOR

            val combined: List<PaperResult> = coroutineScope {
                val tasks = when (sourceFilter) {
                    MODE_FREE_BOOKS -> listOf(
                        async { runCatching { searchGutendexBooks(clean) }.getOrDefault(emptyList()) },
                        async { runCatching { searchOpenLibraryBooks(clean, byAuthor = false) }.getOrDefault(emptyList()) },
                        async { runCatching { searchInternetArchiveBooks(clean) }.getOrDefault(emptyList()) }
                    )
                    MODE_DOI_ONLY -> listOf(
                        async { runCatching { searchDoaj(clean, byAuthor = false) }.getOrDefault(emptyList()) },
                        async { runCatching { searchCrossRef(clean, byAuthor = false, onlyWithDoi = true) }.getOrDefault(emptyList()) },
                        async { runCatching { searchEuropePmc(clean, onlyOpenAccess) }.getOrDefault(emptyList()) },
                        async { runCatching { searchOpenAlex(clean, onlyOpenAccess, sortByCitations) }.getOrDefault(emptyList()) }
                    )
                    MODE_ARXIV -> listOf(
                        async { runCatching { searchArxiv(clean, byAuthor = false) }.getOrDefault(emptyList()) }
                    )
                    MODE_AUTHOR -> listOf(
                        async { runCatching { searchCrossRef(clean, byAuthor = true, onlyWithDoi = true) }.getOrDefault(emptyList()) },
                        async { runCatching { searchDoaj(clean, byAuthor = true) }.getOrDefault(emptyList()) },
                        async { runCatching { searchGutendexBooks(clean) }.getOrDefault(emptyList()) },
                        async { runCatching { searchOpenLibraryBooks(clean, byAuthor = true) }.getOrDefault(emptyList()) },
                        async { runCatching { searchArxiv(clean, byAuthor = true) }.getOrDefault(emptyList()) }
                    )
                    else -> listOf(
                        async { runCatching { searchDoaj(clean, byAuthor = false) }.getOrDefault(emptyList()) },
                        async { runCatching { searchCrossRef(clean, byAuthor = false, onlyWithDoi = true) }.getOrDefault(emptyList()) },
                        async { runCatching { searchEuropePmc(clean, onlyOpenAccess) }.getOrDefault(emptyList()) },
                        async { runCatching { searchOpenAlex(clean, onlyOpenAccess, sortByCitations) }.getOrDefault(emptyList()) },
                        async { runCatching { searchArxiv(clean, byAuthor = false) }.getOrDefault(emptyList()) },
                        async { runCatching { searchGutendexBooks(clean) }.getOrDefault(emptyList()) },
                        async { runCatching { searchOpenLibraryBooks(clean, byAuthor = false) }.getOrDefault(emptyList()) }
                    )
                }
                tasks.awaitAll().flatten()
            }

            // Deduplicar conservando la versión mejor corroborada (con descarga directa o DOI)
            val bestByKey = linkedMapOf<String, PaperResult>()
            for (item in combined) {
                val key = item.title.lowercase().replace(Regex("[^a-z0-9áéíóúñ]"), "").take(64)
                if (key.isBlank()) continue
                val existing = bestByKey[key]
                if (existing == null || corroborabilityScore(item) > corroborabilityScore(existing)) {
                    bestByKey[key] = item
                }
            }

            var list = bestByKey.values.toList()
            if (sourceFilter == MODE_DOI_ONLY) {
                list = list.filter { it.hasVerifiedDoi }
            }
            if (onlyOpenAccess) {
                list = list.filter { it.hasDirectDownload }
            }

            when {
                sortByCitations -> list.sortedByDescending { it.citationCount }
                isAuthorMode -> list.sortedByDescending { corroborabilityScore(it) }
                else -> list.sortedByDescending { corroborabilityScore(it) }
            }
        }
    }

    private fun corroborabilityScore(p: PaperResult): Int {
        var score = 0
        if (p.hasVerifiedDoi) score += 30
        if (p.hasDirectDownload) score += 25
        if (p.abstractText.length > 80) score += 12
        if (!p.isbn.isNullOrBlank()) score += 10
        if (p.citationCount > 0) score += minOf(20, p.citationCount / 5 + 3)
        if (p.source == "DOAJ" || p.source == "Europe PMC" || p.source == "Gutenberg") score += 8
        return score
    }

    /**
     * DOAJ (Directory of Open Access Journals): 100% revistas arbitradas, 100% acceso abierto, gran cobertura en español.
     */
    private fun searchDoaj(query: String, byAuthor: Boolean): List<PaperResult> {
        val qExpr = if (byAuthor) "bibjson.author.name:\"$query\"" else query
        val encoded = URLEncoder.encode(qExpr, "UTF-8").replace("+", "%20")
        val url = "https://doaj.org/api/search/articles/$encoded?pageSize=10"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<PaperResult>()
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                val bib = row.optJSONObject("bibjson") ?: continue
                val title = bib.optCleanString("title")
                if (title.isBlank()) continue

                val id = row.optCleanString("id").ifBlank { "doaj_$i" }
                val year = bib.optCleanString("year").toIntOrNull()
                val journal = bib.optJSONObject("journal")?.optCleanString("title").orEmpty()
                val abstractClean = bib.optCleanString("abstract").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

                val authorsArr = bib.optJSONArray("author")
                val authors = mutableListOf<String>()
                if (authorsArr != null) {
                    for (a in 0 until minOf(authorsArr.length(), 4)) {
                        val n = authorsArr.getJSONObject(a).optCleanString("name")
                        if (n.isNotBlank()) authors.add(n)
                    }
                    if (authorsArr.length() > 4) authors.add("et al.")
                }

                var doiRaw = ""
                val idsArr = bib.optJSONArray("identifier")
                if (idsArr != null) {
                    for (k in 0 until idsArr.length()) {
                        val io = idsArr.getJSONObject(k)
                        if (io.optCleanString("type").equals("doi", ignoreCase = true)) {
                            doiRaw = io.optCleanString("id")
                            break
                        }
                    }
                }
                val doiUrl = if (doiRaw.isNotBlank()) {
                    if (doiRaw.startsWith("http")) doiRaw else "https://doi.org/$doiRaw"
                } else null

                var fullTextUrl: String? = null
                var pdfUrl: String? = null
                val linksArr = bib.optJSONArray("link")
                if (linksArr != null) {
                    for (k in 0 until linksArr.length()) {
                        val lo = linksArr.getJSONObject(k)
                        val u = lo.optCleanString("url")
                        val ct = lo.optCleanString("content_type").lowercase()
                        if (u.startsWith("http")) {
                            if (fullTextUrl == null) fullTextUrl = u
                            if (ct.contains("pdf") || u.lowercase().endsWith(".pdf")) {
                                pdfUrl = u
                            }
                        }
                    }
                }

                out.add(
                    PaperResult(
                        id = "doaj_$id",
                        title = title,
                        authors = authors.joinToString(", ").ifBlank { "Autores no especificados" },
                        year = year,
                        venue = journal.ifBlank { "DOAJ" },
                        citationCount = 0,
                        abstractText = abstractClean,
                        openAccessPdfUrl = pdfUrl,
                        landingPageUrl = fullTextUrl ?: doiUrl,
                        doi = doiUrl,
                        source = "DOAJ",
                        verificationBadge = if (doiUrl != null) "DOI · DOAJ Arbitrado" else "DOAJ Open Access"
                    )
                )
            }
            return out
        }
    }

    /**
     * Gutendex (Project Gutenberg): 70.000+ libros clásicos gratuitos descargables en EPUB y TXT directo.
     */
    private fun searchGutendexBooks(query: String): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://gutendex.com/books/?search=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<PaperResult>()
            for (i in 0 until minOf(arr.length(), 8)) {
                val b = arr.getJSONObject(i)
                val title = b.optCleanString("title")
                if (title.isBlank()) continue
                val id = b.optInt("id", i)
                val authorsArr = b.optJSONArray("authors")
                val authors = mutableListOf<String>()
                var birthYear: Int? = null
                if (authorsArr != null) {
                    for (a in 0 until minOf(authorsArr.length(), 3)) {
                        val ao = authorsArr.getJSONObject(a)
                        val name = ao.optCleanString("name")
                        if (name.isNotBlank()) authors.add(name)
                        if (birthYear == null && !ao.isNull("death_year")) {
                            birthYear = ao.optInt("death_year", 0).takeIf { it > 0 }
                        }
                    }
                }
                val subjectsArr = b.optJSONArray("subjects")
                val subjects = mutableListOf<String>()
                if (subjectsArr != null) {
                    for (s in 0 until minOf(subjectsArr.length(), 4)) {
                        val st = subjectsArr.optString(s, "").trim()
                        if (st.isNotBlank()) subjects.add(st)
                    }
                }
                val langsArr = b.optJSONArray("languages")
                val langStr = if (langsArr != null && langsArr.length() > 0) langsArr.optString(0, "").uppercase() else ""

                val formats = b.optJSONObject("formats")
                val epubUrl = formats?.optCleanString("application/epub+zip")?.takeIf { it.startsWith("http") }
                val txtUrl = formats?.optCleanString("text/plain; charset=utf-8")?.takeIf { it.startsWith("http") }
                    ?: formats?.optCleanString("text/plain; charset=us-ascii")?.takeIf { it.startsWith("http") }
                    ?: formats?.optCleanString("text/plain")?.takeIf { it.startsWith("http") }

                out.add(
                    PaperResult(
                        id = "gutenberg_$id",
                        title = title,
                        authors = authors.joinToString("; ").ifBlank { "Autor clásico" },
                        year = birthYear,
                        venue = "Project Gutenberg${if (langStr.isNotBlank()) " ($langStr)" else ""}",
                        citationCount = b.optInt("download_count", 0),
                        abstractText = if (subjects.isNotEmpty()) "Temas: ${subjects.joinToString(" · ")}" else "Libro completo gratuito de dominio público (EPUB/TXT).",
                        openAccessPdfUrl = null,
                        landingPageUrl = "https://www.gutenberg.org/ebooks/$id",
                        doi = null,
                        source = "Gutenberg",
                        openAccessEpubUrl = epubUrl,
                        openAccessTxtUrl = txtUrl,
                        isBook = true,
                        verificationBadge = "Libro Gratis · EPUB/TXT"
                    )
                )
            }
            return out
        }
    }

    /**
     * Open Library: catálogo abierto de millones de libros académicos y clásicos con ISBN verificable y lectura en Internet Archive.
     */
    private fun searchOpenLibraryBooks(query: String, byAuthor: Boolean): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val param = if (byAuthor) "author=$encoded" else "q=$encoded"
        val url = "https://openlibrary.org/search.json?$param&limit=8&fields=key,title,author_name,first_publish_year,isbn,ia,publisher,subject"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("docs") ?: return emptyList()
            val out = mutableListOf<PaperResult>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val title = d.optCleanString("title")
                if (title.isBlank()) continue
                val key = d.optCleanString("key")
                val authorsArr = d.optJSONArray("author_name")
                val authors = mutableListOf<String>()
                if (authorsArr != null) {
                    for (a in 0 until minOf(authorsArr.length(), 3)) {
                        val n = authorsArr.optString(a, "").trim()
                        if (n.isNotBlank()) authors.add(n)
                    }
                }
                val year = d.optInt("first_publish_year", 0).takeIf { it > 0 }
                val isbn = d.optJSONArray("isbn")?.optString(0, "")?.trim()?.takeIf { it.isNotBlank() }
                val publisher = d.optJSONArray("publisher")?.optString(0, "")?.trim().orEmpty()
                val iaId = d.optJSONArray("ia")?.optString(0, "")?.trim()?.takeIf { it.isNotBlank() }
                val subjectsArr = d.optJSONArray("subject")
                val subjects = mutableListOf<String>()
                if (subjectsArr != null) {
                    for (s in 0 until minOf(subjectsArr.length(), 4)) {
                        val st = subjectsArr.optString(s, "").trim()
                        if (st.isNotBlank()) subjects.add(st)
                    }
                }

                val landing = if (iaId != null) "https://archive.org/details/$iaId" else "https://openlibrary.org$key"
                out.add(
                    PaperResult(
                        id = "ol_${key.substringAfterLast('/').ifBlank { i.toString() }}",
                        title = title,
                        authors = authors.joinToString(", ").ifBlank { "Autor no especificado" },
                        year = year,
                        venue = publisher.ifBlank { "Open Library" },
                        citationCount = 0,
                        abstractText = buildString {
                            if (isbn != null) append("ISBN: $isbn. ")
                            if (subjects.isNotEmpty()) append("Materias: ${subjects.joinToString(" · ")}.")
                            if (iaId != null) append(" Disponible para lectura digital en Internet Archive.")
                        }.trim(),
                        openAccessPdfUrl = null,
                        landingPageUrl = landing,
                        doi = null,
                        source = "Open Library",
                        isbn = isbn,
                        isBook = true,
                        verificationBadge = if (isbn != null) "Libro · ISBN $isbn" else "Open Library"
                    )
                )
            }
            return out
        }
    }

    /**
     * Internet Archive: libros y textos académicos de acceso abierto (con enlace directo de lectura y descarga).
     */
    private fun searchInternetArchiveBooks(query: String): List<PaperResult> {
        val qExpr = "($query) AND mediatype:(texts) AND NOT access-restricted-item:true"
        val encoded = URLEncoder.encode(qExpr, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&fl[]=description&rows=8&output=json"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty())
                .optJSONObject("response")
                ?.optJSONArray("docs") ?: return emptyList()
            val out = mutableListOf<PaperResult>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val id = d.optCleanString("identifier")
                val title = d.optCleanString("title")
                if (id.isBlank() || title.isBlank()) continue
                val creator = when {
                    d.optJSONArray("creator") != null -> d.optJSONArray("creator")!!.optString(0, "")
                    else -> d.optCleanString("creator")
                }.ifBlank { "Internet Archive" }
                val year = d.optCleanString("year").take(4).toIntOrNull()
                val desc = when {
                    d.optJSONArray("description") != null -> d.optJSONArray("description")!!.optString(0, "")
                    else -> d.optCleanString("description")
                }.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(500)

                out.add(
                    PaperResult(
                        id = "ia_$id",
                        title = title,
                        authors = creator,
                        year = year,
                        venue = "Internet Archive",
                        citationCount = 0,
                        abstractText = desc.ifBlank { "Texto de acceso abierto en Internet Archive." },
                        openAccessPdfUrl = "https://archive.org/download/$id/$id.pdf",
                        landingPageUrl = "https://archive.org/details/$id",
                        doi = null,
                        source = "Internet Archive",
                        isBook = true,
                        verificationBadge = "Libro Abierto · Archive.org"
                    )
                )
            }
            return out
        }
    }

    private fun searchOpenAlex(query: String, onlyOpenAccess: Boolean, sortByCitations: Boolean): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val filterParam = if (onlyOpenAccess) "&filter=open_access.is_oa:true" else ""
        val sortParam = if (sortByCitations) "&sort=cited_by_count:desc" else ""
        val url = "https://api.openalex.org/works?search=$encoded&per-page=10$filterParam$sortParam&mailto=catedra.app@gmail.com"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3 (mailto:catedra.app@gmail.com)")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<PaperResult>()
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                val title = w.optCleanString("title")
                if (title.isBlank()) continue
                val id = w.optCleanString("id").substringAfterLast('/').ifBlank { "oa_$i" }
                val year = w.optInt("publication_year", 0).takeIf { it > 0 }
                val cited = w.optInt("cited_by_count", 0)
                val doi = w.optCleanString("doi").takeIf { it.isNotBlank() }

                val authorsArr = w.optJSONArray("authorships")
                val authorsList = mutableListOf<String>()
                if (authorsArr != null) {
                    for (a in 0 until minOf(authorsArr.length(), 4)) {
                        val name = authorsArr.getJSONObject(a)
                            .optJSONObject("author")
                            ?.optCleanString("display_name")
                            .orEmpty()
                        if (name.isNotBlank()) authorsList.add(name)
                    }
                    if (authorsArr.length() > 4) authorsList.add("et al.")
                }

                val venue = w.optJSONObject("primary_location")
                    ?.optJSONObject("source")
                    ?.optCleanString("display_name")
                    .orEmpty()

                val bestOa = w.optJSONObject("best_oa_location")
                val pdfUrl = bestOa?.optCleanString("pdf_url")?.takeIf { it.startsWith("http") }
                    ?: w.optJSONObject("open_access")?.optCleanString("oa_url")?.takeIf { it.endsWith(".pdf", true) }
                val landing = bestOa?.optCleanString("landing_page_url")?.takeIf { it.startsWith("http") }
                    ?: doi

                val abstractText = reconstructOpenAlexAbstract(w.optJSONObject("abstract_inverted_index"))

                list.add(
                    PaperResult(
                        id = "oa_$id",
                        title = title,
                        authors = authorsList.joinToString(", ").ifBlank { "Autores no especificados" },
                        year = year,
                        venue = venue.ifBlank { "OpenAlex" },
                        citationCount = cited,
                        abstractText = abstractText,
                        openAccessPdfUrl = pdfUrl,
                        landingPageUrl = landing,
                        doi = doi,
                        source = "OpenAlex",
                        verificationBadge = if (doi != null) "DOI · OpenAlex" else "OpenAlex"
                    )
                )
            }
            return list
        }
    }

    private fun reconstructOpenAlexAbstract(inverted: JSONObject?): String {
        if (inverted == null) return ""
        val words = mutableListOf<Pair<Int, String>>()
        val keys = inverted.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val positions = inverted.optJSONArray(word) ?: continue
            for (i in 0 until positions.length()) {
                words.add(positions.getInt(i) to word)
            }
        }
        return words.sortedBy { it.first }.joinToString(" ") { it.second }.trim()
    }

    private fun searchEuropePmc(query: String, onlyOpenAccess: Boolean): List<PaperResult> {
        val fullQuery = if (onlyOpenAccess) "($query) AND OPEN_ACCESS:Y" else query
        val encoded = URLEncoder.encode(fullQuery, "UTF-8")
        val url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=$encoded&format=json&pageSize=8&resultType=core"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val arr = JSONObject(body).optJSONObject("resultList")?.optJSONArray("result") ?: return emptyList()
            val list = mutableListOf<PaperResult>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val title = item.optCleanString("title").replace(Regex("<[^>]+>"), "")
                if (title.isBlank()) continue
                val id = item.optCleanString("id").ifBlank { "epmc_$i" }
                val pmcid = item.optCleanString("pmcid")
                val isOa = item.optCleanString("isOpenAccess").equals("Y", ignoreCase = true)
                val doiRaw = item.optCleanString("doi")
                val doiUrl = if (doiRaw.isNotBlank()) "https://doi.org/$doiRaw" else null
                val pdfUrl = if (isOa && pmcid.isNotBlank()) {
                    "https://europepmc.org/backend/ptpmcrender.fcgi?accid=$pmcid&blobtype=pdf"
                } else null
                val abstractClean = item.optCleanString("abstractText").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                val year = item.optCleanString("pubYear").toIntOrNull()
                val venue = item.optJSONObject("journalInfo")
                    ?.optJSONObject("journal")
                    ?.optCleanString("title")
                    ?.ifBlank { "Europe PMC" } ?: "Europe PMC"

                list.add(
                    PaperResult(
                        id = "epmc_$id",
                        title = title,
                        authors = item.optCleanString("authorString").ifBlank { "Autores no especificados" },
                        year = year,
                        venue = venue,
                        citationCount = item.optInt("citedByCount", 0),
                        abstractText = abstractClean,
                        openAccessPdfUrl = pdfUrl,
                        landingPageUrl = if (pmcid.isNotBlank()) "https://europepmc.org/article/PMC/$pmcid" else doiUrl,
                        doi = doiUrl,
                        source = "Europe PMC",
                        verificationBadge = if (doiUrl != null) "DOI · Europe PMC" else "PMCID Verificado"
                    )
                )
            }
            return list
        }
    }

    private fun searchCrossRef(query: String, byAuthor: Boolean, onlyWithDoi: Boolean): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val qParam = if (byAuthor) "query.author=$encoded" else "query=$encoded"
        val url = "https://api.crossref.org/works?$qParam&rows=10&sort=relevance"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3 (mailto:catedra.app@gmail.com)")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val items = JSONObject(body).optJSONObject("message")?.optJSONArray("items") ?: return emptyList()
            val list = mutableListOf<PaperResult>()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val title = it.optJSONArray("title")?.optString(0)?.trim().orEmpty()
                if (title.isBlank()) continue
                val doi = it.optCleanString("DOI")
                if (onlyWithDoi && doi.isBlank()) continue
                val doiUrl = if (doi.isNotBlank()) "https://doi.org/$doi" else null
                val authorsArr = it.optJSONArray("author")
                val authorsList = mutableListOf<String>()
                if (authorsArr != null) {
                    for (a in 0 until minOf(authorsArr.length(), 4)) {
                        val ao = authorsArr.getJSONObject(a)
                        val full = listOf(ao.optCleanString("given"), ao.optCleanString("family")).filter { s -> s.isNotBlank() }.joinToString(" ")
                        if (full.isNotBlank()) authorsList.add(full)
                    }
                    if (authorsArr.length() > 4) authorsList.add("et al.")
                }
                val year = it.optJSONObject("published-print")
                    ?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0, 0)?.takeIf { y -> y > 0 }
                    ?: it.optJSONObject("published-online")
                        ?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0, 0)?.takeIf { y -> y > 0 }
                val venue = it.optJSONArray("container-title")?.optString(0)?.trim().orEmpty()
                val abstractClean = it.optCleanString("abstract").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                val workType = it.optCleanString("type")
                val isBook = workType.contains("book", ignoreCase = true) || workType.contains("monograph", ignoreCase = true)

                list.add(
                    PaperResult(
                        id = "cr_${doi.ifBlank { i.toString() }}",
                        title = title,
                        authors = authorsList.joinToString(", ").ifBlank { "Autores no especificados" },
                        year = year,
                        venue = venue.ifBlank { "CrossRef" },
                        citationCount = it.optInt("is-referenced-by-count", 0),
                        abstractText = abstractClean,
                        openAccessPdfUrl = null,
                        landingPageUrl = doiUrl,
                        doi = doiUrl,
                        source = "CrossRef",
                        isBook = isBook,
                        verificationBadge = "DOI Oficial Verificado"
                    )
                )
            }
            return list
        }
    }

    private fun searchArxiv(query: String, byAuthor: Boolean): List<PaperResult> {
        val prefix = if (byAuthor) "au:" else "all:"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://export.arxiv.org/api/query?search_query=$prefix$encoded&start=0&max_results=8"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.3")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val xml = resp.body?.bytes() ?: return emptyList()
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xml), "UTF-8")

            val results = mutableListOf<PaperResult>()
            var inEntry = false
            var currentTag = ""
            var idUrl = ""
            var title = ""
            var summary = ""
            var published = ""
            var arxivDoi = ""
            val authors = mutableListOf<String>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "entry") {
                            inEntry = true
                            idUrl = ""
                            title = ""
                            summary = ""
                            published = ""
                            arxivDoi = ""
                            authors.clear()
                        }
                    }
                    XmlPullParser.TEXT -> if (inEntry) {
                        val txt = parser.text ?: ""
                        when (currentTag) {
                            "id" -> idUrl += txt
                            "title" -> title += txt
                            "summary" -> summary += txt
                            "published" -> published += txt
                            "arxiv:doi" -> arxivDoi += txt
                            "name" -> if (txt.isNotBlank()) authors.add(txt.trim())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "entry" && inEntry) {
                            val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
                            val cleanSummary = summary.replace(Regex("\\s+"), " ").trim()
                            val arxivId = idUrl.trim().substringAfterLast("/abs/").substringBefore('v')
                            val pdfUrl = if (arxivId.isNotBlank()) "https://arxiv.org/pdf/$arxivId.pdf" else null
                            val year = published.trim().take(4).toIntOrNull()
                            val doiUrl = arxivDoi.trim().takeIf { it.isNotBlank() }?.let { "https://doi.org/$it" }
                            if (cleanTitle.isNotBlank()) {
                                results.add(
                                    PaperResult(
                                        id = "arxiv_$arxivId",
                                        title = cleanTitle,
                                        authors = authors.take(4).joinToString(", ") + if (authors.size > 4) ", et al." else "",
                                        year = year,
                                        venue = "arXiv:$arxivId",
                                        citationCount = 0,
                                        abstractText = cleanSummary,
                                        openAccessPdfUrl = pdfUrl,
                                        landingPageUrl = idUrl.trim(),
                                        doi = doiUrl,
                                        source = "arXiv",
                                        verificationBadge = if (doiUrl != null) "DOI · arXiv PDF" else "arXiv Oficial · PDF"
                                    )
                                )
                            }
                            inEntry = false
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
            return results
        }
    }

    /**
     * Unpaywall API oficial: a partir de cualquier DOI verificado localiza legalmente
     * el PDF gratuito de acceso abierto en repositorios universitarios o editoriales OA.
     */
    suspend fun resolveUnpaywallPdf(rawDoi: String): String? = withContext(Dispatchers.IO) {
        val cleanDoi = rawDoi.trim()
            .removePrefix("https://doi.org/")
            .removePrefix("http://doi.org/")
            .removePrefix("https://dx.doi.org/")
            .trim()
        if (!cleanDoi.startsWith("10.")) return@withContext null
        runCatching {
            val encoded = URLEncoder.encode(cleanDoi, "UTF-8")
            val url = "https://api.unpaywall.org/v2/$encoded?email=catedra.app@gmail.com"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CatedraStudyApp/1.3")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val j = JSONObject(resp.body?.string().orEmpty())
                val best = j.optJSONObject("best_oa_location")
                best?.optCleanString("url_for_pdf")?.takeIf { it.startsWith("http") }
            }
        }.getOrNull()
    }

    suspend fun downloadFile(url: String, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; CatedraStudyApp/1.3)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("No se pudo descargar el archivo (HTTP ${resp.code})")
                val body = resp.body ?: error("Respuesta vacía al descargar documento")
                FileOutputStream(destFile).use { out ->
                    body.byteStream().copyTo(out)
                }
                if (destFile.length() < 512L) {
                    destFile.delete()
                    error("El servidor no entregó un archivo válido (puede requerir acceso institucional).")
                }
                destFile
            }
        }
    }

    suspend fun downloadPdf(url: String, destFile: File): Result<File> = downloadFile(url, destFile)

    /**
     * Resuelve un enlace compartido o copiado desde Google Scholar, Chrome, DOI o arXiv.
     * Si detecta un DOI, consulta CrossRef + Unpaywall para intentar traer automáticamente el PDF abierto legal.
     */
    suspend fun resolvePaperUrlOrDoi(rawInput: String): Result<PaperResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawInput.trim()
            require(trimmed.isNotBlank()) { "Pega un enlace de Google Scholar, DOI, arXiv o URL de PDF." }

            val arxivMatch = Regex("""arxiv\.org/(?:abs|pdf)/([0-9]{4}\.[0-9]{4,5})""").find(trimmed)
            if (arxivMatch != null) {
                val arxivId = arxivMatch.groupValues[1]
                val found = runCatching { searchArxiv(arxivId, byAuthor = false).firstOrNull() }.getOrNull()
                if (found != null) return@runCatching found
                return@runCatching PaperResult(
                    id = "arxiv_$arxivId",
                    title = "arXiv:$arxivId",
                    authors = "arXiv",
                    year = null,
                    venue = "arXiv",
                    citationCount = 0,
                    abstractText = "",
                    openAccessPdfUrl = "https://arxiv.org/pdf/$arxivId.pdf",
                    landingPageUrl = "https://arxiv.org/abs/$arxivId",
                    doi = null,
                    source = "arXiv",
                    verificationBadge = "arXiv Oficial · PDF"
                )
            }

            val doiMatch = Regex("""(10\.\d{4,9}/[-._;()/:A-Za-z0-9]+)""").find(trimmed)
            if (doiMatch != null) {
                val doi = doiMatch.groupValues[1].trimEnd('.', ',', ';', ')')
                val encodedDoi = URLEncoder.encode(doi, "UTF-8")
                val unpaywallPdf = resolveUnpaywallPdf(doi)
                val req = Request.Builder()
                    .url("https://api.crossref.org/works/$encodedDoi")
                    .header("User-Agent", "CatedraStudyApp/1.3 (mailto:catedra.app@gmail.com)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val msg = JSONObject(resp.body?.string().orEmpty()).optJSONObject("message")
                        if (msg != null) {
                            val title = msg.optJSONArray("title")?.optString(0)?.trim().orEmpty().ifBlank { doi }
                            val authorsArr = msg.optJSONArray("author")
                            val authorsList = mutableListOf<String>()
                            if (authorsArr != null) {
                                for (a in 0 until minOf(authorsArr.length(), 4)) {
                                    val ao = authorsArr.getJSONObject(a)
                                    val full = listOf(ao.optCleanString("given"), ao.optCleanString("family"))
                                        .filter { it.isNotBlank() }.joinToString(" ")
                                    if (full.isNotBlank()) authorsList.add(full)
                                }
                                if (authorsArr.length() > 4) authorsList.add("et al.")
                            }
                            val year = msg.optJSONObject("published-print")
                                ?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0, 0)?.takeIf { it > 0 }
                                ?: msg.optJSONObject("published-online")
                                    ?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0, 0)?.takeIf { it > 0 }
                            val venue = msg.optJSONArray("container-title")?.optString(0)?.trim().orEmpty()
                            val absClean = msg.optCleanString("abstract").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                            return@runCatching PaperResult(
                                id = "doi_${doi.hashCode()}",
                                title = title,
                                authors = authorsList.joinToString(", ").ifBlank { "Autores no especificados" },
                                year = year,
                                venue = venue.ifBlank { "CrossRef / DOI" },
                                citationCount = msg.optInt("is-referenced-by-count", 0),
                                abstractText = absClean,
                                openAccessPdfUrl = unpaywallPdf ?: if (trimmed.lowercase().endsWith(".pdf")) trimmed else null,
                                landingPageUrl = "https://doi.org/$doi",
                                doi = "https://doi.org/$doi",
                                source = "CrossRef",
                                verificationBadge = if (unpaywallPdf != null) "DOI · PDF Abierto (Unpaywall)" else "DOI Oficial Verificado"
                            )
                        }
                    }
                }
            }

            if (trimmed.startsWith("http", ignoreCase = true) && trimmed.lowercase().contains(".pdf")) {
                val guessedName = trimmed.substringAfterLast('/').substringBefore('?').removeSuffix(".pdf").ifBlank { "Paper PDF" }
                return@runCatching PaperResult(
                    id = "pdf_${trimmed.hashCode()}",
                    title = guessedName,
                    authors = "Enlace directo PDF",
                    year = null,
                    venue = "Web / Google Scholar",
                    citationCount = 0,
                    abstractText = "",
                    openAccessPdfUrl = trimmed,
                    landingPageUrl = trimmed,
                    doi = null,
                    source = "PDF Web",
                    verificationBadge = "PDF Directo"
                )
            }

            val found = searchPapers(trimmed).getOrDefault(emptyList()).firstOrNull()
                ?: error("No se encontró ese DOI o enlace. Prueba buscando por palabras clave.")
            found
        }
    }

    fun buildGoogleScholarUrl(query: String): String {
        val clean = query.trim()
        return if (clean.isBlank()) {
            "https://scholar.google.com/?hl=es"
        } else {
            "https://scholar.google.com/scholar?hl=es&q=${URLEncoder.encode(clean, "UTF-8")}"
        }
    }

    fun formatApaCitation(paper: PaperResult): String {
        val yearStr = paper.year?.toString() ?: "s. f."
        val idStr = when {
            !paper.doi.isNullOrBlank() -> " ${paper.doi}"
            !paper.isbn.isNullOrBlank() -> " ISBN ${paper.isbn}."
            !paper.landingPageUrl.isNullOrBlank() -> " ${paper.landingPageUrl}"
            else -> ""
        }
        return "${paper.authors} ($yearStr). ${paper.title}. ${paper.venue}.$idStr"
    }

    fun formatBibTexCitation(paper: PaperResult): String {
        val firstAuthor = paper.authors.substringBefore(',').substringBefore(' ').lowercase().replace(Regex("[^a-z]"), "").ifBlank { "fuente" }
        val yearStr = paper.year?.toString() ?: "nd"
        val key = "${firstAuthor}$yearStr"
        val entryType = if (paper.isBook) "book" else "article"
        return buildString {
            appendLine("@$entryType{$key,")
            appendLine("  title = {${paper.title}},")
            appendLine("  author = {${paper.authors}},")
            if (paper.year != null) appendLine("  year = {${paper.year}},")
            if (paper.isBook) appendLine("  publisher = {${paper.venue}},") else appendLine("  journal = {${paper.venue}},")
            if (!paper.doi.isNullOrBlank()) appendLine("  doi = {${paper.doi.removePrefix("https://doi.org/")}},")
            if (!paper.isbn.isNullOrBlank()) appendLine("  isbn = {${paper.isbn}},")
            append("}")
        }
    }
}
