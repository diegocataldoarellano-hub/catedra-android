package com.catedra.app.data.model

enum class DocType(val shortBadge: String, val folderGroup: String) {
    PDF("PDF", "PDF"),
    DOCX("DOC", "Docs"),
    DOC("DOC", "Docs"),
    XLSX("XLS", "Hojas"),
    XLS("XLS", "Hojas"),
    CSV("CSV", "Hojas"),
    PPTX("PPT", "Docs"),
    EPUB("EPUB", "PDF"),
    IMAGE("IMG", "Visual"),
    TEXT("TXT", "Docs"),
    PAPER("PAPER", "Papers"),
    UNKNOWN("DOC", "Docs");

    companion object {
        fun fromFileNameAndMime(fileName: String, mimeType: String?): DocType {
            val lower = fileName.lowercase()
            val mime = (mimeType ?: "").lowercase()
            return when {
                lower.endsWith(".pdf") || mime.contains("pdf") -> PDF
                lower.endsWith(".docx") || lower.endsWith(".odt") || mime.contains("wordprocessingml") || mime.contains("opendocument.text") -> DOCX
                lower.endsWith(".doc") || lower.endsWith(".rtf") || mime == "application/msword" || mime.contains("rtf") -> DOC
                lower.endsWith(".xlsx") || lower.endsWith(".ods") || mime.contains("spreadsheetml") || mime.contains("opendocument.spreadsheet") -> XLSX
                lower.endsWith(".xls") || mime == "application/vnd.ms-excel" -> XLS
                lower.endsWith(".csv") || lower.endsWith(".tsv") || mime.contains("csv") -> CSV
                lower.endsWith(".pptx") || lower.endsWith(".odp") || mime.contains("presentationml") || mime.contains("opendocument.presentation") -> PPTX
                lower.endsWith(".epub") || mime.contains("epub") -> EPUB
                mime.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp") -> IMAGE
                lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".tex") || lower.endsWith(".json") || lower.endsWith(".xml") || mime.startsWith("text/") -> TEXT
                else -> UNKNOWN
            }
        }
    }
}

data class DocSection(
    val index: Int,          // 1-based section/page index
    val label: String,       // e.g. "Pág. 1", "Hoja: Ventas", "Diap. 3", "Cap. 2"
    val title: String,       // Short heading or snippet
    val content: String,     // Extracted clean text
    val isVisualPage: Boolean = false
)

data class StudyDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val docType: DocType,
    val sourceUri: String,           // SAF content:// URI or file:// path
    val localCachedPath: String?,    // Non-null only for downloaded papers/Drive exports/camera captures
    val mimeType: String,
    val totalSections: Int,
    val totalChars: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String = "",
    val noteCount: Int = 0,
    val isPinned: Boolean = false,
    val paperDoi: String? = null,
    val paperAuthors: String? = null,
    val paperYear: Int? = null,
    val driveFileId: String? = null
)

enum class MessageRole {
    USER, TEACHER, SYSTEM
}

data class ChatMessage(
    val id: String,
    val documentId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String? = null,       // Optional collapsed NVIDIA Nemotron / DeepSeek thinking
    val timestamp: Long,
    val citedLabels: List<String> = emptyList(),
    val isVoiceInput: Boolean = false,
    val attachedSectionIndex: Int? = null,
    val isSavedToNotes: Boolean = false,
    val engineBadge: String? = null      // e.g. "Nemotron + NeMo Rerank" or "Gemini 2.5 Flash"
)

enum class NoteCategory(val label: String, val mdHeader: String) {
    COMPRENDIDO("Comprendido", "Comprendido y Dominado"),
    ERROR_CORREGIDO("Error corregido", "Errores de Comprensión Corregidos"),
    ANALISIS_CRITICA("Análisis y crítica", "Análisis Crítico y Reflexiones"),
    CITA("Cita clave", "Citas Clave del Documento"),
    DUDA("Duda abierta", "Dudas e Indagaciones Pendientes"),
    NOTA_VOZ("Nota de voz", "Notas Rápidas (Texto / Voz)")
}

data class StudyNote(
    val id: String,
    val documentId: String,
    val category: NoteCategory,
    val content: String,
    val citationRef: String = "",
    val isFromVoice: Boolean = false,
    val createdAt: Long
)

data class PaperResult(
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val venue: String,
    val citationCount: Int,
    val abstractText: String,
    val openAccessPdfUrl: String?,
    val landingPageUrl: String?,
    val doi: String?,
    val source: String, // "DOAJ", "CrossRef", "OpenAlex", "Europe PMC", "arXiv", "Gutenberg", "Open Library", "Internet Archive"
    val isbn: String? = null,
    val openAccessEpubUrl: String? = null,
    val openAccessTxtUrl: String? = null,
    val isBook: Boolean = false,
    val verificationBadge: String = ""
) {
    val hasDirectDownload: Boolean
        get() = !openAccessPdfUrl.isNullOrBlank() || !openAccessEpubUrl.isNullOrBlank() || !openAccessTxtUrl.isNullOrBlank()

    val hasVerifiedDoi: Boolean
        get() = !doi.isNullOrBlank() && doi.contains("10.")
}

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val isFolder: Boolean
)

enum class AiProvider(val displayName: String, val subtitle: String) {
    GEMINI("Google Gemini", "Contexto 1M tokens + visión nativa de PDFs y gráficos"),
    NVIDIA_NIM("NVIDIA NIM (Nemotron + NeMo)", "Modelos Nemotron, DeepSeek-R1, Visión VLM y NeMo Rerank"),
    OPENAI("OpenAI", "GPT-4o / GPT-4o-mini"),
    OPENROUTER("OpenRouter / Compatible", "Acceso unificado o servidor propio compatible con OpenAI")
}

enum class TeacherTone(val label: String, val description: String) {
    SOCRATICO("Docente Socrático", "Conversacional, guía con preguntas y detecta errores de comprensión"),
    EXPERTO_DIRECTO("Cátedra Directa", "Claro, profundo y estructurado con citas exactas"),
    EXAMEN_RIGUROSO("Tutor de Examen", "Exigente: te desafía a explicar con tus palabras y critica argumentos")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Automático"),
    LIGHT("Claro Telegram"),
    DARK("Oscuro Noche"),
    AMOLED("Negro AMOLED")
}

data class AppSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val nvidiaApiKey: String = "",
    val nvidiaModel: String = "nvidia/nemotron-3.5-super-vl-120b-a12b",
    val nvidiaVisionModel: String = "nvidia/nemotron-3.5-super-vl-120b-a12b",
    val useNvidiaRerank: Boolean = true,            // Skill NVIDIA Nemotron-3-Embed-1B (2048-dim) para citas quirúrgicas
    val useNvidiaVisionSkill: Boolean = true,       // Skill Visión NVIDIA VLM para gráficos, tablas y escaneos
    val showReasoningSummary: Boolean = false,      // Razonamiento colapsable de Nemotron 3 / 3.5
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val teacherTone: TeacherTone = TeacherTone.SOCRATICO,
    val speechRate: Float = 1.05f,
    val autoReadTeacherReplies: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val driveBridgeUrl: String = DEFAULT_DRIVE_BRIDGE_URL,
    val driveAccessToken: String = "",
    val driveRefreshToken: String = "",
    val driveTokenExpiresAt: Long = 0L,
    val driveUserEmail: String = "",
    val driveNotesFolderId: String = "",
    val exportFolderUri: String = "",
    val whatsappTreeUri: String = ""
) {
    companion object {
        const val DEFAULT_DRIVE_BRIDGE_URL = "https://catedra-portal.onrender.com"
    }

    fun hasActiveKey(): Boolean = when (provider) {
        AiProvider.GEMINI -> geminiApiKey.isNotBlank()
        AiProvider.NVIDIA_NIM -> nvidiaApiKey.isNotBlank()
        AiProvider.OPENAI -> openAiApiKey.isNotBlank()
        AiProvider.OPENROUTER -> openAiApiKey.isNotBlank()
    }

    fun hasNvidiaSkillsReady(): Boolean = nvidiaApiKey.isNotBlank()
    fun hasDriveConnected(): Boolean = driveAccessToken.isNotBlank() || driveRefreshToken.isNotBlank()
}

data class StorageStats(
    val notesBytes: Long,
    val notesFileCount: Int,
    val compressedIndexBytes: Long,
    val databaseBytes: Long,
    val cachedPapersBytes: Long
) {
    val totalBytes: Long get() = notesBytes + compressedIndexBytes + databaseBytes + cachedPapersBytes
}

enum class QuizDifficulty(val label: String, val promptInstruction: String) {
    PROGRESIVO(
        "Fácil → Difícil",
        "ORDEN PROGRESIVO OBLIGATORIO: comienza con preguntas Fáciles (comprensión directa), avanza a Medias (inferencia y relación entre conceptos) y termina con Difíciles (análisis crítico, aplicación compleja y detección de falacias)."
    ),
    FACIL(
        "Fácil",
        "Todas las preguntas deben ser de nivel Fácil (comprensión clara de definiciones, hechos e ideas principales del texto)."
    ),
    MEDIO(
        "Medio",
        "Todas las preguntas deben ser de nivel Medio (inferencia, comparación de argumentos, causas y consecuencias)."
    ),
    DIFICIL(
        "Difícil",
        "Todas las preguntas deben ser de nivel Difícil (evaluación crítica, supuestos implícitos, contraargumentos y síntesis avanzada)."
    )
}

data class QuizQuestion(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val citationRef: String,
    val explanation: String,
    val commonMisconception: String,
    val hint: String = "",
    val difficultyLabel: String = "Medio"
)

data class FlashcardItem(
    val id: Int,
    val front: String,
    val hint: String,
    val back: String,
    val citationRef: String,
    val difficultyLabel: String = "Medio"
)

data class QuizSessionState(
    val documentId: String = "",
    val documentTitle: String = "",
    val questionCount: Int = 5,
    val difficulty: QuizDifficulty = QuizDifficulty.PROGRESIVO,
    val questions: List<QuizQuestion> = emptyList(),
    val selectedAnswers: Map<Int, Int> = emptyMap(), // questionId -> chosen option index
    val revealedHints: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Studio NotebookLM: Tarjetas (Flashcards) y Guía de Estudio
    val flashcards: List<FlashcardItem> = emptyList(),
    val isLoadingFlashcards: Boolean = false,
    val studyGuideMarkdown: String = "",
    val isLoadingStudyGuide: Boolean = false
)

data class LocalDocEntry(
    val uri: String,
    val name: String,
    val docType: DocType,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long
)
