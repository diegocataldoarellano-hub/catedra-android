package com.catedra.app.data.ai

import com.catedra.app.data.model.AiProvider
import com.catedra.app.data.model.AppSettings
import com.catedra.app.data.model.ChatMessage
import com.catedra.app.data.model.DocSection
import com.catedra.app.data.model.MessageRole
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.data.model.StudyNote
import com.catedra.app.data.model.TeacherTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.min

data class TeacherReply(
    val content: String,
    val reasoning: String? = null,
    val citedLabels: List<String> = emptyList(),
    val engineBadge: String
)

data class DistilledNoteDraft(
    val category: NoteCategory,
    val content: String,
    val citationRef: String
)

/**
 * Motor Docente Conversacional con soporte completo para:
 * - NVIDIA NIM (Nemotron Super/Ultra, DeepSeek-R1, Llama 3.3, Visión VLM y NeMo Retriever Rerank)
 * - Google Gemini API (1M tokens de contexto + visión nativa)
 * - OpenAI / OpenRouter / Compatible
 */
class DocenteAiEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val NVIDIA_EMBED_MODEL = "nvidia/nemotron-3-embed-1b"
        const val NVIDIA_TRANSLATE_MODEL = "nvidia/riva-translate-4b-instruct-v2"

        val CURATED_NVIDIA_CHAT_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl-120b-a12b",
            "nvidia/nemotron-3-ultra-550b-a55b",
            "google/gemma-4-31b-it",
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning",
            "mistralai/mistral-large-4-1t-a52b",
            "moonshotai/kimi-k3.1-pro"
        )

        val CURATED_NVIDIA_VISION_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl-120b-a12b",
            "google/gemma-4-31b-it"
        )

        val CURATED_GEMINI_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
    }

    suspend fun askTeacher(
        settings: AppSettings,
        document: StudyDocument,
        sections: List<DocSection>,
        history: List<ChatMessage>,
        notes: List<StudyNote>,
        userPrompt: String,
        attachedImageBase64: String? = null,
        focusSectionIndex: Int? = null
    ): Result<TeacherReply> = withContext(Dispatchers.IO) {
        runCatching {
            val activeProvider = resolveEffectiveProvider(settings)
            val useRerank = settings.useNvidiaRerank &&
                settings.nvidiaApiKey.isNotBlank() &&
                sections.size > 4

            // 1. Selección inteligente de pasajes del documento (Full-text en Gemini o BM25 + NVIDIA NeMo Rerank)
            val contextBuild = buildDocumentContext(
                provider = activeProvider,
                settings = settings,
                document = document,
                sections = sections,
                userPrompt = userPrompt,
                focusSectionIndex = focusSectionIndex,
                useNvidiaRerank = useRerank
            )

            // 2. Si hay imagen/página visual y el usuario tiene NVIDIA Vision Skill activa, o si usa Gemini Vision
            val useNvidiaVision = attachedImageBase64 != null &&
                (activeProvider == AiProvider.NVIDIA_NIM ||
                    (settings.useNvidiaVisionSkill && settings.nvidiaApiKey.isNotBlank() && activeProvider != AiProvider.GEMINI))

            val systemPrompt = buildDocenteSystemPrompt(
                document = document,
                tone = settings.teacherTone,
                documentContext = contextBuild.contextText,
                notes = notes
            )

            val recentHistory = history
                .filter { it.role != MessageRole.SYSTEM }
                .takeLast(14)

            val rawResponse: Pair<String, String?>
            val badge: String

            when {
                useNvidiaVision -> {
                    val primaryVlm = settings.nvidiaVisionModel.ifBlank { CURATED_NVIDIA_VISION_MODELS.first() }
                    val fallbackVlm = "google/gemma-4-31b-it"
                    rawResponse = runCatching {
                        callOpenAiCompatible(
                            baseUrl = NVIDIA_BASE_URL,
                            apiKey = settings.nvidiaApiKey,
                            model = primaryVlm,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            userPrompt = userPrompt,
                            imageBase64 = attachedImageBase64
                        )
                    }.getOrElse { firstErr ->
                        if (primaryVlm != fallbackVlm) {
                            callOpenAiCompatible(
                                baseUrl = NVIDIA_BASE_URL,
                                apiKey = settings.nvidiaApiKey,
                                model = fallbackVlm,
                                systemPrompt = systemPrompt,
                                history = recentHistory,
                                userPrompt = userPrompt,
                                imageBase64 = attachedImageBase64
                            )
                        } else throw firstErr
                    }
                    badge = buildBadge("NVIDIA VLM", contextBuild.usedNvidiaRerank)
                }

                activeProvider == AiProvider.NVIDIA_NIM -> {
                    val primaryModel = settings.nvidiaModel.ifBlank { CURATED_NVIDIA_CHAT_MODELS.first() }
                    val fallbackModel = "nvidia/nemotron-3-ultra-550b-a55b"
                    rawResponse = runCatching {
                        callOpenAiCompatible(
                            baseUrl = NVIDIA_BASE_URL,
                            apiKey = settings.nvidiaApiKey,
                            model = primaryModel,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            userPrompt = userPrompt,
                            imageBase64 = attachedImageBase64
                        )
                    }.getOrElse { firstErr ->
                        if (primaryModel != fallbackModel && (firstErr.message?.contains("404") == true || firstErr.message?.contains("400") == true)) {
                            callOpenAiCompatible(
                                baseUrl = NVIDIA_BASE_URL,
                                apiKey = settings.nvidiaApiKey,
                                model = fallbackModel,
                                systemPrompt = systemPrompt,
                                history = recentHistory,
                                userPrompt = userPrompt,
                                imageBase64 = attachedImageBase64
                            )
                        } else throw firstErr
                    }
                    val shortModel = primaryModel.substringAfterLast('/').take(26)
                    badge = buildBadge("NVIDIA $shortModel", contextBuild.usedNvidiaRerank)
                }

                activeProvider == AiProvider.GEMINI -> {
                    val model = settings.geminiModel.ifBlank { "gemini-2.5-flash" }
                    rawResponse = callGemini(
                        apiKey = settings.geminiApiKey,
                        model = model,
                        systemPrompt = systemPrompt,
                        history = recentHistory,
                        userPrompt = userPrompt,
                        imageBase64 = attachedImageBase64
                    )
                    badge = buildBadge(model, contextBuild.usedNvidiaRerank)
                }

                else -> {
                    val base = if (activeProvider == AiProvider.OPENROUTER && settings.openAiBaseUrl.contains("openai.com")) {
                        "https://openrouter.ai/api/v1"
                    } else {
                        settings.openAiBaseUrl.ifBlank { "https://api.openai.com/v1" }
                    }
                    rawResponse = callOpenAiCompatible(
                        baseUrl = base,
                        apiKey = settings.openAiApiKey,
                        model = settings.openAiModel.ifBlank { "gpt-4o-mini" },
                        systemPrompt = systemPrompt,
                        history = recentHistory,
                        userPrompt = userPrompt,
                        imageBase64 = attachedImageBase64
                    )
                    badge = buildBadge(settings.openAiModel, contextBuild.usedNvidiaRerank)
                }
            }

            val citations = extractCitationLabels(rawResponse.first, sections)
            TeacherReply(
                content = rawResponse.first.trim(),
                reasoning = rawResponse.second?.trim()?.takeIf { it.isNotBlank() },
                citedLabels = citations,
                engineBadge = badge
            )
        }
    }

    /**
     * Extrae de la conversación ÚNICAMENTE lo que el estudiante comprendió, los errores
     * que se corrigieron, el análisis crítico y citas clave, para guardarlo en .md/.txt ultraligero.
     */
    suspend fun distillLearningsFromChat(
        settings: AppSettings,
        document: StudyDocument,
        history: List<ChatMessage>,
        existingNotes: List<StudyNote>
    ): Result<List<DistilledNoteDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val activeProvider = resolveEffectiveProvider(settings)
            val transcript = history
                .filter { it.role != MessageRole.SYSTEM }
                .takeLast(18)
                .joinToString("\n") { msg ->
                    val speaker = if (msg.role == MessageRole.USER) "ESTUDIANTE" else "DOCENTE"
                    "$speaker: ${msg.content.take(900)}"
                }
            if (transcript.isBlank()) error("No hay conversación suficiente para sintetizar aún.")

            val alreadySaved = existingNotes.take(12).joinToString("\n") { "- [${it.category.name}] ${it.content}" }

            val sys = """
                Eres un sintetizador pedagógico ultra-compacto. Analiza el diálogo de estudio sobre "${document.title}"
                y extrae SOLO hallazgos nuevos que valga la pena guardar a largo plazo en un archivo .txt/.md liviano:
                - COMPRENDIDO: conceptos que el estudiante realmente entendió o consolidó.
                - ERROR_CORREGIDO: confusiones o errores de interpretación que se aclararon ("Antes pensaba X -> En realidad Y").
                - ANALISIS_CRITICA: reflexiones críticas, cuestionamientos metodológicos, comparaciones o posturas propias.
                - CITA: citas textuales o datos clave con su referencia exacta.
                - DUDA: preguntas profundas que quedaron abiertas.

                NO repitas lo que ya está guardado:
                $alreadySaved

                Responde ÚNICAMENTE con un JSON válido (sin texto adicional ni bloques markdown):
                [{"category":"COMPRENDIDO|ERROR_CORREGIDO|ANALISIS_CRITICA|CITA|DUDA","citation":"Pág. X o vacío","content":"síntesis breve, clara y densa en conocimiento (máx 240 caracteres)"}]
            """.trimIndent()

            val raw = callSimpleCompletion(activeProvider, settings, sys, "Diálogo reciente:\n$transcript")
            parseDistilledJson(raw)
        }
    }

    /**
     * Sugiere 3 consultas académicas precisas para buscar papers relacionados en OpenAlex / arXiv.
     */
    suspend fun suggestRelatedPaperQueries(
        settings: AppSettings,
        document: StudyDocument,
        sections: List<DocSection>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val sample = sections.take(3).joinToString("\n") { it.content.take(700) }
            if (!settings.hasActiveKey() && !settings.hasNvidiaSkillsReady()) {
                return@runCatching listOf(document.title)
            }
            val activeProvider = resolveEffectiveProvider(settings)
            val sys = """
                A partir del título y fragmento de un documento de estudio, devuelve exactamente 3 consultas académicas breves
                (preferiblemente en inglés para máxima cobertura en OpenAlex/arXiv, o español si es tema local) separadas por salto de línea, sin numeración ni explicaciones.
            """.trimIndent()
            val raw = callSimpleCompletion(
                activeProvider,
                settings,
                sys,
                "Título: ${document.title}\nExtracto:\n$sample"
            )
            raw.lines()
                .map { it.trim().removePrefix("-").removePrefix("*").replace(Regex("^\\d+[.)]\\s*"), "").trim('"', ' ') }
                .filter { it.length > 3 }
                .take(3)
                .ifEmpty { listOf(document.title) }
        }
    }

    /**
     * NVIDIA Skill: Consulta el catálogo en vivo de modelos disponibles en NVIDIA NIM (build.nvidia.com).
     */
    suspend fun fetchNvidiaAvailableModels(nvidiaApiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(nvidiaApiKey.isNotBlank()) { "Ingresa primero tu NVIDIA API Key (nvapi-...)" }
            val req = Request.Builder()
                .url("$NVIDIA_BASE_URL/models")
                .header("Authorization", "Bearer ${nvidiaApiKey.trim()}")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("NVIDIA NIM respondió HTTP ${resp.code}: ${body.take(180)}")
                val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val ids = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.getJSONObject(i).optString("id")
                    if (id.isNotBlank()) ids.add(id)
                }
                ids.sortedWith(
                    compareByDescending<String> { it.contains("nemotron", true) }
                        .thenByDescending { it.contains("deepseek", true) || it.contains("llama-3.3", true) }
                        .thenBy { it }
                )
            }
        }
    }

    /**
     * Skill NVIDIA Riva Translate (`nvidia/riva-translate-4b-instruct-v2`):
     * Traduce pasajes o páginas de papers académicos en inglés u otros idiomas al español natural
     * manteniendo rigor terminológico. Si no hay clave NVIDIA, usa el proveedor activo.
     */
    suspend fun translateAcademicText(
        settings: AppSettings,
        sourceText: String,
        targetLanguage: String = "español académico claro y natural"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = sourceText.trim().take(6000)
            require(clean.isNotBlank()) { "No hay texto para traducir en esta sección." }
            if (settings.nvidiaApiKey.isNotBlank()) {
                val prompt = "Translate the following academic study text faithfully into natural Spanish, preserving technical terms and structure:\n\n$clean"
                return@runCatching callOpenAiCompatible(
                    baseUrl = NVIDIA_BASE_URL,
                    apiKey = settings.nvidiaApiKey,
                    model = NVIDIA_TRANSLATE_MODEL,
                    systemPrompt = "",
                    history = emptyList(),
                    userPrompt = prompt,
                    imageBase64 = null
                ).first
            }
            val active = resolveEffectiveProvider(settings)
            callSimpleCompletion(
                active,
                settings,
                "Traduce fielmente el siguiente texto de estudio a $targetLanguage sin omitir detalles ni agregar comentarios externos.",
                clean
            )
        }
    }

    suspend fun testConnection(settings: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val active = resolveEffectiveProvider(settings)
            val reply = callSimpleCompletion(
                active,
                settings,
                "Responde brevemente en español confirmando que estás listo como docente.",
                "Hola, ¿estás conectado?"
            )
            var extra = ""
            if (settings.nvidiaApiKey.isNotBlank() && settings.useNvidiaRerank) {
                val embedOk = runCatching {
                    rerankWithNvidiaNeMo(
                        apiKey = settings.nvidiaApiKey,
                        query = "comprensión lectora",
                        passages = listOf("La comprensión lectora profunda requiere análisis crítico.", "Receta de cocina.")
                    ).firstOrNull() == 0
                }.getOrDefault(false)
                extra = if (embedOk) " · NVIDIA Embed 2048d OK" else " · Embed fallback BM25"
            }
            "Conectado (${active.displayName}$extra): ${reply.take(80)}"
        }
    }

    private fun resolveEffectiveProvider(settings: AppSettings): AiProvider {
        if (settings.hasActiveKey()) return settings.provider
        if (settings.nvidiaApiKey.isNotBlank()) return AiProvider.NVIDIA_NIM
        if (settings.geminiApiKey.isNotBlank()) return AiProvider.GEMINI
        if (settings.openAiApiKey.isNotBlank()) return AiProvider.OPENAI
        error("Configura tu API Key (Gemini o NVIDIA NIM nvapi-...) en Ajustes para conversar con el Docente.")
    }

    private data class ContextBuildResult(
        val contextText: String,
        val usedNvidiaRerank: Boolean
    )

    private fun buildDocumentContext(
        provider: AiProvider,
        settings: AppSettings,
        document: StudyDocument,
        sections: List<DocSection>,
        userPrompt: String,
        focusSectionIndex: Int?,
        useNvidiaRerank: Boolean
    ): ContextBuildResult {
        if (sections.isEmpty()) return ContextBuildResult("[Documento sin texto extraído]", false)

        val maxBudgetChars = when (provider) {
            AiProvider.GEMINI -> 280_000        // Gemini soporta 1M tokens -> documento completo en contexto!
            AiProvider.NVIDIA_NIM -> 72_000     // Nemotron 128k context -> amplio y rápido
            AiProvider.OPENAI, AiProvider.OPENROUTER -> 48_000
        }

        val totalDocChars = sections.sumOf { it.content.length }
        if (totalDocChars <= maxBudgetChars) {
            // Cabe el documento COMPLETO con todas sus etiquetas exactas para citar sin perder nada!
            var priorityHeader = ""
            var usedRerankOnFull = false
            if (useNvidiaRerank && userPrompt.length >= 6) {
                val pool = sections.take(64)
                val passages = pool.map { s -> "[${s.label}] ${s.title}\n${s.content.take(1200)}" }
                val reranked = runCatching {
                    rerankWithNvidiaNeMo(settings.nvidiaApiKey, userPrompt, passages)
                }.getOrNull()
                if (!reranked.isNullOrEmpty()) {
                    usedRerankOnFull = true
                    val topLabels = reranked.take(4).mapNotNull { pool.getOrNull(it)?.label }
                    if (topLabels.isNotEmpty()) {
                        priorityHeader = "=== SECCIONES PRIORITARIAS PARA ESTA CONSULTA (NVIDIA NeMo Rerank): ${topLabels.joinToString(", ")} ===\n\n"
                    }
                }
            }
            val fullText = priorityHeader + sections.joinToString("\n\n") { s ->
                "=== [${s.label}] ${s.title} ===\n${s.content}"
            }
            return ContextBuildResult(fullText, usedRerankOnFull)
        }

        // Para libros muy extensos: TOC completo + páginas citadas explícitamente + BM25 + NVIDIA NeMo Retriever Rerank!
        val toc = buildString {
            appendLine("=== ÍNDICE COMPLETO DE \"${document.title}\" (${sections.size} secciones) ===")
            for (s in sections) {
                appendLine("- [${s.label}]: ${s.title}")
            }
        }

        val mandatoryIndices = linkedSetOf<Int>()
        mandatoryIndices.add(0) // Inicio / portada / abstract
        if (sections.size > 1) mandatoryIndices.add(1)
        if (sections.size > 2) mandatoryIndices.add(sections.lastIndex) // Conclusiones
        if (focusSectionIndex != null) {
            val z = (focusSectionIndex - 1).coerceIn(0, sections.lastIndex)
            mandatoryIndices.add(z)
            if (z - 1 >= 0) mandatoryIndices.add(z - 1)
            if (z + 1 <= sections.lastIndex) mandatoryIndices.add(z + 1)
        }

        // Detectar números de página/sección mencionados por el usuario (ej. "pág 14", "página 5", "diap 3")
        val refRegex = Regex("""(?i)(?:p[aá]g(?:ina)?\.?|p\.|secci[oó]n|diap(?:ositiva)?\.?|cap(?:[ií]tulo)?\.?|hoja)\s*(\d+)""")
        for (match in refRegex.findAll(userPrompt)) {
            val num = match.groupValues[1].toIntOrNull() ?: continue
            val idx = sections.indexOfFirst { it.index == num }
            if (idx >= 0) {
                mandatoryIndices.add(idx)
                if (idx + 1 <= sections.lastIndex) mandatoryIndices.add(idx + 1)
            }
        }

        // Candidatos rankeados por BM25 local
        val bm25Ranked = rankSectionsBm25(sections, userPrompt)
        var usedRerank = false
        val orderedCandidates: List<Int> = if (useNvidiaRerank && userPrompt.length >= 6) {
            val poolIndices = bm25Ranked.take(28)
            val passages = poolIndices.map { idx ->
                val s = sections[idx]
                "[${s.label}] ${s.title}\n${s.content.take(1400)}"
            }
            val reranked = runCatching {
                rerankWithNvidiaNeMo(settings.nvidiaApiKey, userPrompt, passages)
            }.getOrNull()

            if (!reranked.isNullOrEmpty()) {
                usedRerank = true
                reranked.mapNotNull { poolIndices.getOrNull(it) }
            } else {
                bm25Ranked
            }
        } else {
            bm25Ranked
        }

        val selected = linkedSetOf<Int>()
        selected.addAll(mandatoryIndices)
        var budgetLeft = maxBudgetChars - toc.length
        for (idx in selected) {
            budgetLeft -= sections[idx].content.length + 40
        }
        for (idx in orderedCandidates) {
            if (budgetLeft <= 1200) break
            if (selected.add(idx)) {
                budgetLeft -= sections[idx].content.length + 40
            }
        }

        val sortedSelected = selected.sorted()
        val body = sortedSelected.joinToString("\n\n") { idx ->
            val s = sections[idx]
            "=== [${s.label}] ${s.title} ===\n${s.content}"
        }
        val full = "$toc\n\n=== PASAJES SELECCIONADOS (en orden del documento) ===\n$body"
        return ContextBuildResult(full, usedRerank)
    }

    /**
     * Skill NVIDIA Nemotron 3 Semantic Embeddings (`nvidia/nemotron-3-embed-1b`, 2048 dimensiones):
     * Obtiene vectores semánticos para la pregunta (`input_type="query"`) y los pasajes candidatos
     * (`input_type="passage"`) y los ordena por similitud coseno exacta para lograr citas quirúrgicas.
     */
    private fun rerankWithNvidiaNeMo(
        apiKey: String,
        query: String,
        passages: List<String>
    ): List<Int> {
        if (passages.isEmpty()) return emptyList()
        val capped = passages.take(24)
        val qVec = fetchNvidiaEmbeddings(apiKey, listOf(query.take(600)), "query").firstOrNull()
            ?: return emptyList()
        val pVecs = fetchNvidiaEmbeddings(apiKey, capped.map { it.take(1200) }, "passage")
        if (pVecs.size != capped.size) return emptyList()

        return pVecs.indices
            .map { idx -> idx to cosineSimilarity(qVec, pVecs[idx]) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun fetchNvidiaEmbeddings(
        apiKey: String,
        inputs: List<String>,
        inputType: String
    ): List<DoubleArray> {
        val payload = JSONObject().apply {
            put("model", NVIDIA_EMBED_MODEL)
            put("input", JSONArray().apply { inputs.forEach { put(it) } })
            put("input_type", inputType)
            put("encoding_format", "float")
            put("truncate", "END")
        }
        val req = Request.Builder()
            .url("$NVIDIA_BASE_URL/embeddings")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
            val out = arrayOfNulls<DoubleArray>(data.length())
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val idx = item.optInt("index", i).coerceIn(0, out.lastIndex)
                val emb = item.getJSONArray("embedding")
                val vec = DoubleArray(emb.length()) { k -> emb.getDouble(k) }
                out[idx] = vec
            }
            return out.filterNotNull()
        }
    }

    internal fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val len = min(a.size, b.size)
        if (len == 0) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until len) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom > 0.0) dot / denom else 0.0
    }

    internal fun rankSectionsBm25(sections: List<DocSection>, query: String): List<Int> {
        val stopWords = setOf(
            "el", "la", "los", "las", "un", "una", "de", "del", "al", "en", "y", "o", "que",
            "es", "por", "para", "con", "sin", "sobre", "como", "qué", "cómo", "cual", "cuál",
            "este", "esta", "estos", "estas", "ese", "esa", "me", "te", "se", "lo", "le", "su",
            "the", "and", "of", "to", "in", "is", "for", "that", "on", "with", "as", "by"
        )
        val qTokens = tokenize(query).filter { it.length > 2 && it !in stopWords }.distinct()
        if (qTokens.isEmpty()) return sections.indices.toList()

        val docTokens = sections.map { tokenize("${it.title} ${it.content}") }
        val avgDl = docTokens.map { it.size }.average().coerceAtLeast(1.0)
        val n = sections.size.toDouble()
        val k1 = 1.5
        val b = 0.75

        val df = qTokens.associateWith { term ->
            docTokens.count { tokens -> term in tokens }
        }

        val scores = sections.indices.map { idx ->
            val tokens = docTokens[idx]
            val dl = tokens.size.toDouble()
            val freqs = tokens.groupingBy { it }.eachCount()
            var score = 0.0
            for (term in qTokens) {
                val f = (freqs[term] ?: 0).toDouble()
                if (f <= 0) continue
                val nq = (df[term] ?: 0).toDouble()
                val idf = ln((n - nq + 0.5) / (nq + 0.5) + 1.0)
                score += idf * ((f * (k1 + 1.0)) / (f + k1 * (1.0 - b + b * (dl / avgDl))))
            }
            idx to score
        }
        return scores.sortedByDescending { it.second }.map { it.first }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
            .split(Regex("[^a-z0-9ñ]+"))
            .filter { it.isNotBlank() }
    }

    private fun buildDocenteSystemPrompt(
        document: StudyDocument,
        tone: TeacherTone,
        documentContext: String,
        notes: List<StudyNote>
    ): String {
        val toneInstruction = when (tone) {
            TeacherTone.SOCRATICO ->
                "Habla con calidez, naturalidad y rigor como un docente universitario experto conversando mano a mano con su estudiante. " +
                    "Fluye en el diálogo (evita respuestas robóticas tipo plantilla rígida salvo cuando te pidan un resumen estructurado). " +
                    "Haz preguntas socráticas breves cuando ayuden a verificar si el concepto se entendió de verdad."
            TeacherTone.EXPERTO_DIRECTO ->
                "Habla como un profesor titular claro, preciso y articulado. Ve directo al núcleo conceptual, conecta ideas entre distintas partes del texto y ejemplifica con claridad."
            TeacherTone.EXAMEN_RIGUROSO ->
                "Actúa como un docente exigente preparando al estudiante para una defensa oral o examen. Desafía afirmaciones vagas, pide precisión conceptual y señala cualquier salto lógico."
        }

        val notesDigest = if (notes.isEmpty()) {
            "(Aún no hay notas previas registradas sobre esta lectura.)"
        } else {
            notes.take(16).joinToString("\n") { n ->
                "- [${n.category.label}] ${if (n.citationRef.isNotBlank()) "(${n.citationRef}) " else ""}${n.content}"
            }
        }

        return """
            Eres el Docente titular y especialista absoluto en el documento "${document.title}" (${document.fileName}).
            $toneInstruction

            REGLAS PEDAGÓGICAS OBLIGATORIAS:
            1. CITAS EXACTAS Y TRAZABLES: Cada vez que afirmes algo basado en el documento, cita explícitamente entre corchetes la etiqueta exacta de la sección tal como aparece en el texto, por ejemplo [Pág. 4], [Sección 2], [Hoja: Costos], [Diap. 5] o [Cap. 3]. Cuando refuerce la claridad, incluye una breve cita textual entre comillas "...".
            2. DETECCIÓN DE ERRORES DE COMPRENSIÓN: Si el estudiante explica algo con sus palabras, interpreta mal un pasaje o confunde causa/efecto o conceptos:
               - Reconoce primero qué parte SÍ captó bien.
               - Señala con tacto y exactitud dónde está el error o sesgo de comprensión, contrastándolo con el pasaje exacto [Pág. X].
               - Ayúdalo a reconstruir la idea correcta con una analogía o contraejemplo claro.
            3. HONESTIDAD EPISTÉMICA: Nunca inventes datos que no estén en el documento. Si algo no figura en el texto, dilo claramente y distingue qué proviene del documento vs. tu conocimiento académico complementario.
            4. CLARIDAD VISUAL Y FLUIDEZ: Usa párrafos breves y respirables (estilo conversación real en Telegram). Responde en el mismo idioma en que te escriba el estudiante (por defecto español).

            CONOCIMIENTO PREVIO Y NOTAS DEL ESTUDIANTE SOBRE ESTE DOCUMENTO:
            $notesDigest

            CONTENIDO DEL DOCUMENTO DE ESTUDIO:
            $documentContext
        """.trimIndent()
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        imageBase64: String?
    ): Pair<String, String?> {
        val contents = JSONArray()
        for (msg in history) {
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            contents.put(
                JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                }
            )
        }
        val userParts = JSONArray()
        userParts.put(JSONObject().put("text", userPrompt))
        if (!imageBase64.isNullOrBlank()) {
            userParts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", imageBase64)
                    }
                )
            )
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", userParts)
            }
        )

        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            put("contents", contents)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.45)
                    put("maxOutputTokens", 2048)
                }
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}"
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault(body.take(220))
                error("Error Gemini (HTTP ${resp.code}): $errMsg")
            }
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
                ?: error("Gemini no devolvió candidatos de respuesta.")
            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("text") && !p.optBoolean("thought", false)) {
                        append(p.getString("text"))
                    }
                }
            }
            val thought = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.optBoolean("thought", false) && p.has("text")) {
                        append(p.getString("text"))
                    }
                }
            }.takeIf { it.isNotBlank() }

            return splitThinkTags(text, thought)
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        imageBase64: String?
    ): Pair<String, String?> {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }

        for (msg in history) {
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", msg.content))
        }

        if (!imageBase64.isNullOrBlank()) {
            val contentArr = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", userPrompt))
                put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")
                    )
                )
            }
            messages.put(JSONObject().put("role", "user").put("content", contentArr))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.45)
            put("max_tokens", 4096)
            put("stream", false)
        }

        val cleanBase = baseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$cleanBase/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = runCatching {
                    val j = JSONObject(body)
                    j.optJSONObject("error")?.optString("message")
                        ?: j.optString("detail")
                        ?: body.take(220)
                }.getOrDefault(body.take(220))
                error("Error API (HTTP ${resp.code}): $errMsg")
            }
            val json = JSONObject(body)
            val msgObj = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = if (msgObj.isNull("content")) "" else msgObj.optString("content", "")
            val reasoningField = if (msgObj.isNull("reasoning_content")) null
            else msgObj.optString("reasoning_content", "").takeIf { it.isNotBlank() && it != "null" }
            val resolvedContent = content.ifBlank { reasoningField.orEmpty() }
            return splitThinkTags(resolvedContent, reasoningField)
        }
    }

    private fun callSimpleCompletion(
        provider: AiProvider,
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String
    ): String {
        return when (provider) {
            AiProvider.GEMINI -> callGemini(
                apiKey = settings.geminiApiKey,
                model = settings.geminiModel.ifBlank { "gemini-2.5-flash" },
                systemPrompt = systemPrompt,
                history = emptyList(),
                userPrompt = userPrompt,
                imageBase64 = null
            ).first
            AiProvider.NVIDIA_NIM -> callOpenAiCompatible(
                baseUrl = NVIDIA_BASE_URL,
                apiKey = settings.nvidiaApiKey,
                model = settings.nvidiaModel.ifBlank { CURATED_NVIDIA_CHAT_MODELS.first() },
                systemPrompt = systemPrompt,
                history = emptyList(),
                userPrompt = userPrompt,
                imageBase64 = null
            ).first
            else -> {
                val base = if (provider == AiProvider.OPENROUTER && settings.openAiBaseUrl.contains("openai.com")) {
                    "https://openrouter.ai/api/v1"
                } else {
                    settings.openAiBaseUrl.ifBlank { "https://api.openai.com/v1" }
                }
                callOpenAiCompatible(
                    baseUrl = base,
                    apiKey = settings.openAiApiKey,
                    model = settings.openAiModel.ifBlank { "gpt-4o-mini" },
                    systemPrompt = systemPrompt,
                    history = emptyList(),
                    userPrompt = userPrompt,
                    imageBase64 = null
                ).first
            }
        }
    }

    internal fun splitThinkTags(rawText: String, existingReasoning: String?): Pair<String, String?> {
        val regex = Regex("(?is)<think>(.*?)</think>")
        val match = regex.find(rawText)
        return if (match != null) {
            val thinkText = match.groupValues[1].trim()
            val clean = rawText.replace(regex, "").trim()
            val mergedReasoning = listOfNotNull(existingReasoning, thinkText.takeIf { it.isNotBlank() })
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
            clean to mergedReasoning
        } else {
            rawText.trim() to existingReasoning
        }
    }

    private fun buildBadge(base: String, usedRerank: Boolean): String {
        return if (usedRerank) "$base + Nemotron Embed" else base
    }

    internal fun extractCitationLabels(text: String, sections: List<DocSection>): List<String> {
        val found = linkedSetOf<String>()
        val knownLabels = sections.map { it.label }
        val bracketRegex = Regex("""\[([^\[\]]{2,36})\]""")
        for (m in bracketRegex.findAll(text)) {
            val inner = m.groupValues[1].trim()
            val exact = knownLabels.firstOrNull { it.equals(inner, ignoreCase = true) }
            if (exact != null) {
                found.add(exact)
                continue
            }
            val numMatch = Regex("""(?i)(?:p[aá]g\.?|secci[oó]n|diap\.?|cap\.?)\s*(\d+)""").find(inner)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].toIntOrNull()
                val sec = sections.firstOrNull { it.index == num }
                if (sec != null) found.add(sec.label)
            }
        }
        return found.take(8)
    }

    internal fun parseDistilledJson(raw: String): List<DistilledNoteDraft> {
        val cleaned = raw
            .replace(Regex("(?s)^```(?:json)?\\s*"), "")
            .replace(Regex("(?s)\\s*```$"), "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = JSONArray(cleaned.substring(start, end + 1))
        val out = mutableListOf<DistilledNoteDraft>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val catName = obj.optString("category", "COMPRENDIDO").uppercase()
            val cat = runCatching { NoteCategory.valueOf(catName) }.getOrDefault(NoteCategory.COMPRENDIDO)
            val content = obj.optString("content", "").trim()
            val cite = obj.optString("citation", "").trim()
            if (content.isNotBlank()) {
                out.add(DistilledNoteDraft(cat, content, cite))
            }
        }
        return out
    }
}
