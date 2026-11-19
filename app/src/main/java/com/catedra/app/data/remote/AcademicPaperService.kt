package com.catedra.app.data.remote

import com.catedra.app.data.model.PaperResult
import kotlinx.coroutines.Dispatchers
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
 * Buscador académico abierto multi-fuente (sin API Keys obligatorias):
 * - OpenAlex (250M+ trabajos multidisciplinarios)
 * - arXiv (preprints con acceso directo a PDF)
 * - Europe PMC (resúmenes completos, citas y PDFs Open Access directos vía PMCID)
 * - CrossRef (respaldo universal para todas las disciplinas si OpenAlex limita tráfico anónimo)
 */
class AcademicPaperService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun JSONObject.optCleanString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key, "").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    }

    suspend fun searchPapers(
        query: String,
        onlyOpenAccess: Boolean = false,
        sourceFilter: String = "ALL", // "ALL", "OPENALEX", "ARXIV"
        sortByCitations: Boolean = false
    ): Result<List<PaperResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            require(clean.isNotBlank()) { "Escribe un tema, autor o título para buscar papers." }

            val results = mutableListOf<PaperResult>()
            var openAlexCount = 0

            if (sourceFilter == "ALL" || sourceFilter == "OPENALEX") {
                val oa = runCatching { searchOpenAlex(clean, onlyOpenAccess, sortByCitations) }.getOrDefault(emptyList())
                openAlexCount = oa.size
                results.addAll(oa)
            }

            if (sourceFilter == "ALL" || sourceFilter == "ARXIV") {
                runCatching { results.addAll(searchArxiv(clean)) }
            }

            // Europe PMC (excelentes resúmenes completos + PDFs abiertos confiables sin límites estrictos)
            if (sourceFilter == "ALL" || (sourceFilter == "OPENALEX" && openAlexCount == 0)) {
                runCatching { results.addAll(searchEuropePmc(clean, onlyOpenAccess)) }
            }

            // Respaldo CrossRef si OpenAlex devolvió vacío (p. ej. rate-limit 429 anónimo)
            if (results.size < 4 && sourceFilter != "ARXIV") {
                runCatching { results.addAll(searchCrossRef(clean)) }
            }

            // Deduplicar por título normalizado
            val seenTitles = HashSet<String>()
            val deduped = results.filter { p ->
                val key = p.title.lowercase().replace(Regex("[^a-z0-9]"), "").take(64)
                key.isNotBlank() && seenTitles.add(key)
            }

            val filtered = if (onlyOpenAccess) {
                deduped.filter { !it.openAccessPdfUrl.isNullOrBlank() }
            } else {
                deduped
            }

            if (sortByCitations) {
                filtered.sortedByDescending { it.citationCount }
            } else {
                filtered
            }
        }
    }

    private fun searchOpenAlex(query: String, onlyOpenAccess: Boolean, sortByCitations: Boolean): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val filterParam = if (onlyOpenAccess) "&filter=open_access.is_oa:true" else ""
        val sortParam = if (sortByCitations) "&sort=cited_by_count:desc" else ""
        val url = "https://api.openalex.org/works?search=$encoded&per-page=12$filterParam$sortParam&mailto=catedra.study@example.com"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.0 (mailto:catedra.study@example.com)")
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
                        source = "OpenAlex"
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
        val url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=$encoded&format=json&pageSize=10&resultType=core"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.0")
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
                        source = "Europe PMC"
                    )
                )
            }
            return list
        }
    }

    private fun searchCrossRef(query: String): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.crossref.org/works?query=$encoded&rows=8"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.0 (mailto:catedra.study@example.com)")
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
                        source = "CrossRef"
                    )
                )
            }
            return list
        }
    }

    private fun searchArxiv(query: String): List<PaperResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://export.arxiv.org/api/query?search_query=all:$encoded&start=0&max_results=10"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CatedraStudyApp/1.0")
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
                            if (cleanTitle.isNotBlank()) {
                                results.add(
                                    PaperResult(
                                        id = "arxiv_$arxivId",
                                        title = cleanTitle,
                                        authors = authors.take(4).joinToString(", ") + if (authors.size > 4) ", et al." else "",
                                        year = year,
                                        venue = "arXiv",
                                        citationCount = 0,
                                        abstractText = cleanSummary,
                                        openAccessPdfUrl = pdfUrl,
                                        landingPageUrl = idUrl.trim(),
                                        doi = null,
                                        source = "arXiv"
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

    suspend fun downloadPdf(url: String, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; CatedraStudyApp/1.0)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("No se pudo descargar el PDF (HTTP ${resp.code})")
                val body = resp.body ?: error("Respuesta vacía al descargar PDF")
                FileOutputStream(destFile).use { out ->
                    body.byteStream().copyTo(out)
                }
                if (destFile.length() < 1024L) {
                    destFile.delete()
                    error("El servidor no entregó un PDF válido (puede requerir acceso institucional).")
                }
                destFile
            }
        }
    }
}
