package com.catedra.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catedra.app.data.ai.DocenteAiEngine
import com.catedra.app.data.local.CatedraDatabase
import com.catedra.app.data.local.CompactKnowledgeStore
import com.catedra.app.data.local.SettingsStore
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.AppSettings
import com.catedra.app.data.model.ChatMessage
import com.catedra.app.data.model.DocSection
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.DriveFileItem
import com.catedra.app.data.model.MessageRole
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.model.PaperResult
import com.catedra.app.data.model.StorageStats
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.data.model.StudyNote
import com.catedra.app.data.parser.UniversalDocumentParser
import com.catedra.app.data.remote.AcademicPaperService
import com.catedra.app.data.remote.GoogleDriveService
import com.catedra.app.media.TtsUiState
import com.catedra.app.media.VoiceAndTtsManager
import com.catedra.app.media.VoiceInputState
import com.catedra.app.media.VoiceTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class CatedraScreen {
    object Home : CatedraScreen()
    data class Chat(val documentId: String) : CatedraScreen()
    data class Reader(val documentId: String, val initialSectionIndex: Int = 0) : CatedraScreen()
    data class Notes(val documentId: String) : CatedraScreen()
    data class Papers(val initialQuery: String = "", val contextDocId: String? = null) : CatedraScreen()
    object Drive : CatedraScreen()
    object Settings : CatedraScreen()
}

data class CatedraUiState(
    val currentScreen: CatedraScreen = CatedraScreen.Home,
    val documents: List<StudyDocument> = emptyList(),
    val selectedFolderTab: String = "Todos",
    val homeSearchQuery: String = "",
    val activeDocument: StudyDocument? = null,
    val activeSections: List<DocSection> = emptyList(),
    val activeMessages: List<ChatMessage> = emptyList(),
    val activeNotes: List<StudyNote> = emptyList(),
    val activeNoteFileBytes: Long = 0L,
    val isTeacherThinking: Boolean = false,
    val isImportingDocument: Boolean = false,
    val importStatusText: String = "",
    val continuousVoiceMode: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val storageStats: StorageStats = StorageStats(0, 0, 0, 0, 0),
    val snackbarMessage: String? = null,
    // Reader state
    val readerSectionIndex: Int = 0,
    val readerVisualBitmap: Bitmap? = null,
    val isLoadingVisualPage: Boolean = false,
    // Papers state
    val paperResults: List<PaperResult> = emptyList(),
    val isSearchingPapers: Boolean = false,
    val suggestedPaperQueries: List<String> = emptyList(),
    val downloadingPaperId: String? = null,
    // Drive state
    val driveFiles: List<DriveFileItem> = emptyList(),
    val isLoadingDrive: Boolean = false,
    // NVIDIA live catalog
    val nvidiaCatalogModels: List<String> = emptyList(),
    val isLoadingNvidiaCatalog: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null
)

class CatedraViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = CatedraDatabase(appContext)
    private val settingsStore = SettingsStore(appContext)
    val knowledgeStore = CompactKnowledgeStore(appContext)
    private val aiEngine = DocenteAiEngine()
    private val paperService = AcademicPaperService()
    private val driveService = GoogleDriveService()
    val voiceManager = VoiceAndTtsManager(appContext)

    private val _uiState = MutableStateFlow(CatedraUiState())
    val uiState: StateFlow<CatedraUiState> = _uiState.asStateFlow()

    val ttsState: StateFlow<TtsUiState> = voiceManager.ttsState
    val voiceState: StateFlow<VoiceInputState> = voiceManager.voiceState

    private val backStack = ArrayDeque<CatedraScreen>()

    init {
        val loadedSettings = settingsStore.load()
        voiceManager.setSpeechRate(loadedSettings.speechRate)
        _uiState.update {
            it.copy(
                settings = loadedSettings,
                documents = db.getAllDocuments(),
                storageStats = knowledgeStore.getStorageStats()
            )
        }
    }

    fun navigateTo(screen: CatedraScreen) {
        val current = _uiState.value.currentScreen
        if (current != screen) {
            backStack.addLast(current)
        }
        _uiState.update { it.copy(currentScreen = screen) }
        when (screen) {
            is CatedraScreen.Chat -> loadDocumentSession(screen.documentId)
            is CatedraScreen.Reader -> {
                loadDocumentSession(screen.documentId)
                jumpReaderToSection(screen.initialSectionIndex)
            }
            is CatedraScreen.Notes -> loadDocumentSession(screen.documentId)
            is CatedraScreen.Papers -> {
                if (screen.initialQuery.isNotBlank()) {
                    searchPapers(screen.initialQuery)
                }
            }
            is CatedraScreen.Drive -> {
                if (_uiState.value.settings.hasDriveConnected() && _uiState.value.driveFiles.isEmpty()) {
                    loadDriveFiles()
                }
            }
            is CatedraScreen.Home -> refreshHome()
            is CatedraScreen.Settings -> {
                _uiState.update { it.copy(storageStats = knowledgeStore.getStorageStats()) }
            }
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) {
            if (_uiState.value.currentScreen != CatedraScreen.Home) {
                _uiState.update { it.copy(currentScreen = CatedraScreen.Home, continuousVoiceMode = false) }
                refreshHome()
                return true
            }
            return false
        }
        val prev = backStack.removeLast()
        if (prev is CatedraScreen.Home) {
            _uiState.update { it.copy(currentScreen = prev, continuousVoiceMode = false) }
            refreshHome()
        } else {
            _uiState.update { it.copy(currentScreen = prev) }
        }
        return true
    }

    fun refreshHome() {
        _uiState.update {
            it.copy(
                documents = db.getAllDocuments(),
                storageStats = knowledgeStore.getStorageStats()
            )
        }
    }

    fun setHomeFolderTab(tab: String) {
        _uiState.update { it.copy(selectedFolderTab = tab) }
    }

    fun setHomeSearchQuery(q: String) {
        _uiState.update { it.copy(homeSearchQuery = q) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    private fun loadDocumentSession(docId: String) {
        val doc = db.getAllDocuments().firstOrNull { it.id == docId } ?: return
        val sections = knowledgeStore.loadSections(docId)
        val messages = db.getMessages(docId)
        val notes = db.getNotes(docId)
        val noteBytes = knowledgeStore.getNoteFileSizeBytes(doc)
        _uiState.update {
            it.copy(
                activeDocument = doc,
                activeSections = sections,
                activeMessages = messages,
                activeNotes = notes,
                activeNoteFileBytes = noteBytes
            )
        }
    }

    /**
     * Importa cualquier documento desde Android SAF (almacenamiento del celular o Google Drive),
     * foto de cámara, o intent externo (Abrir con / Compartir con Cátedra).
     */
    fun importDocumentFromUri(
        uri: Uri,
        forcedFileName: String? = null,
        forcedMime: String? = null,
        localCachedPath: String? = null,
        paperMeta: PaperResult? = null,
        driveFileId: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImportingDocument = true,
                    importStatusText = "Leyendo e indexando documento..."
                )
            }
            // Conservar permiso persistente SAF si es content://
            if (uri.scheme == "content") {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            val parsedResult = runCatching {
                UniversalDocumentParser.parseUri(appContext, uri, forcedFileName, forcedMime)
            }

            parsedResult.onSuccess { parsed ->
                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val docType = if (paperMeta != null) DocType.PAPER else parsed.docType
                val doc = StudyDocument(
                    id = docId,
                    title = paperMeta?.title ?: parsed.title,
                    fileName = parsed.fileName,
                    docType = docType,
                    sourceUri = uri.toString(),
                    localCachedPath = localCachedPath,
                    mimeType = parsed.mimeType,
                    totalSections = parsed.sections.size,
                    totalChars = parsed.totalChars,
                    createdAt = now,
                    updatedAt = now,
                    lastMessagePreview = "Documento listo (${parsed.sections.size} secciones). Conversa con el Docente.",
                    paperDoi = paperMeta?.doi,
                    paperAuthors = paperMeta?.authors,
                    paperYear = paperMeta?.year,
                    driveFileId = driveFileId
                )

                withContext(Dispatchers.IO) {
                    knowledgeStore.saveSectionsCompressed(docId, parsed.sections)
                    db.upsertDocument(doc)
                    knowledgeStore.writeMarkdownNotesFile(doc, emptyList())
                    val compressedKb = formatCompactSize(knowledgeStore.getCompressedIndexSizeBytes(docId))
                    val welcome = buildString {
                        append("He cargado **${doc.title}** (${parsed.sections.size} secciones · índice compacto de $compressedKb).\n\n")
                        if (parsed.sections.any { it.isVisualPage }) {
                            append("Detecté contenido visual o escaneado: puedes abrir el **Lector** arriba y tocar *Analizar visualmente* para que interprete gráficos, fórmulas o apuntes.\n\n")
                        }
                        append("¿Por dónde prefieres empezar? Puedo hacerte un **resumen citado**, plantearte **preguntas de comprensión**, escuchar tu explicación para detectar errores de entendimiento, o leerte el texto completo en voz alta.")
                    }
                    db.insertMessage(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            documentId = docId,
                            role = MessageRole.TEACHER,
                            content = welcome,
                            timestamp = now + 1,
                            engineBadge = "Cátedra"
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        isImportingDocument = false,
                        importStatusText = "",
                        documents = db.getAllDocuments(),
                        storageStats = knowledgeStore.getStorageStats()
                    )
                }
                navigateTo(CatedraScreen.Chat(docId))
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isImportingDocument = false,
                        importStatusText = "",
                        snackbarMessage = "No se pudo abrir el documento: ${err.message}"
                    )
                }
            }
        }
    }

    /**
     * Crea una lectura rápida desde texto pegado (apuntes, artículo copiado o resumen de paper).
     */
    fun createTextStudyDocument(
        title: String,
        bodyText: String,
        paperMeta: PaperResult? = null
    ) {
        viewModelScope.launch {
            val safeTitle = title.trim().ifBlank { "Lectura rápida" }
            val safeBody = bodyText.trim()
            if (safeBody.isBlank()) {
                showMessage("Escribe o pega algún texto para estudiar.")
                return@launch
            }
            val file = File(knowledgeStore.importsDir, "txt_${System.currentTimeMillis()}.txt")
            withContext(Dispatchers.IO) {
                file.writeText(safeBody, Charsets.UTF_8)
            }
            importDocumentFromUri(
                uri = Uri.fromFile(file),
                forcedFileName = "$safeTitle.txt",
                forcedMime = "text/plain",
                localCachedPath = file.absolutePath,
                paperMeta = paperMeta
            )
        }
    }

    fun togglePinDocument(doc: StudyDocument) {
        db.togglePinDocument(doc.id, !doc.isPinned)
        refreshHome()
    }

    fun renameDocument(docId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        db.renameDocument(docId, newTitle)
        refreshHome()
        if (_uiState.value.activeDocument?.id == docId) {
            loadDocumentSession(docId)
        }
    }

    fun deleteDocument(doc: StudyDocument) {
        voiceManager.stopSpeaking()
        knowledgeStore.deleteDocumentFiles(doc)
        db.deleteDocument(doc.id)
        if (_uiState.value.activeDocument?.id == doc.id) {
            backStack.clear()
            _uiState.update { it.copy(currentScreen = CatedraScreen.Home, activeDocument = null) }
        }
        refreshHome()
        showMessage("Lectura eliminada.")
    }

    fun sendChatMessage(
        rawText: String,
        isVoiceInput: Boolean = false,
        attachVisualSectionIndex: Int? = null
    ) {
        val doc = _uiState.value.activeDocument ?: return
        val prompt = rawText.trim()
        if (prompt.isBlank() || _uiState.value.isTeacherThinking) return

        // Comandos rápidos estilo Telegram
        val effectivePrompt = when (prompt.lowercase()) {
            "/resumen" -> "Hazme un resumen profundo, claro y estructurado del documento completo, citando las secciones/páginas exactas de cada idea clave."
            "/preguntas" -> "Hazme 3 preguntas progresivas de comprensión (una literal, una inferencial y una crítica) sobre el documento para verificar si realmente lo entendí. Espera mis respuestas antes de dar las soluciones."
            "/evaluar" -> "Quiero explicarte el documento con mis propias palabras para que detectes errores de comprensión o matices que se me escapen. Indícame qué punto clave debería explicarte primero."
            "/critica" -> "Ayúdame a hacer un análisis crítico riguroso de este texto: tesis central, calidad de la evidencia, supuestos implícitos, posibles sesgos o limitaciones, citando las páginas correspondientes."
            "/conceptos" -> "Extrae los conceptos fundamentales del documento y explica cómo se relacionan entre sí, siempre citando dónde aparecen."
            else -> prompt
        }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            documentId = doc.id,
            role = MessageRole.USER,
            content = prompt,
            timestamp = System.currentTimeMillis(),
            isVoiceInput = isVoiceInput,
            attachedSectionIndex = attachVisualSectionIndex
        )

        db.insertMessage(userMsg)
        val updatedMessages = db.getMessages(doc.id)
        _uiState.update {
            it.copy(
                activeMessages = updatedMessages,
                isTeacherThinking = true
            )
        }

        viewModelScope.launch {
            // Preparar imagen visual si el documento es imagen o el usuario adjuntó una página visual del PDF
            val imageB64: String? = when {
                doc.docType == DocType.IMAGE -> {
                    UniversalDocumentParser.loadImageBitmap(appContext, doc.sourceUri, doc.localCachedPath)
                        ?.let { UniversalDocumentParser.bitmapToBase64Jpeg(it) }
                }
                attachVisualSectionIndex != null && (doc.docType == DocType.PDF || doc.docType == DocType.PAPER) -> {
                    UniversalDocumentParser.renderPdfPageBitmap(
                        appContext,
                        doc.sourceUri,
                        doc.localCachedPath,
                        (attachVisualSectionIndex - 1).coerceAtLeast(0),
                        targetWidthPx = 1200
                    )?.let { UniversalDocumentParser.bitmapToBase64Jpeg(it) }
                }
                else -> null
            }

            val result = aiEngine.askTeacher(
                settings = _uiState.value.settings,
                document = doc,
                sections = _uiState.value.activeSections,
                history = updatedMessages.dropLast(1),
                notes = _uiState.value.activeNotes,
                userPrompt = effectivePrompt,
                attachedImageBase64 = imageB64,
                focusSectionIndex = attachVisualSectionIndex
            )

            result.onSuccess { reply ->
                val teacherMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    documentId = doc.id,
                    role = MessageRole.TEACHER,
                    content = reply.content,
                    reasoning = reply.reasoning,
                    timestamp = System.currentTimeMillis(),
                    citedLabels = reply.citedLabels,
                    engineBadge = reply.engineBadge
                )
                db.insertMessage(teacherMsg)
                _uiState.update {
                    it.copy(
                        activeMessages = db.getMessages(doc.id),
                        documents = db.getAllDocuments(),
                        isTeacherThinking = false
                    )
                }

                // Lectura en voz alta automática si está activo Modo Diálogo Oral o auto-lectura
                val shouldSpeak = _uiState.value.continuousVoiceMode || _uiState.value.settings.autoReadTeacherReplies
                if (shouldSpeak) {
                    voiceManager.speakSingleMessage(reply.content) {
                        if (_uiState.value.continuousVoiceMode) {
                            startVoiceDictation(VoiceTarget.CHAT)
                        }
                    }
                }
            }.onFailure { err ->
                val errMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    documentId = doc.id,
                    role = MessageRole.SYSTEM,
                    content = err.message ?: "Error al consultar al Docente.",
                    timestamp = System.currentTimeMillis()
                )
                db.insertMessage(errMsg)
                _uiState.update {
                    it.copy(
                        activeMessages = db.getMessages(doc.id),
                        isTeacherThinking = false,
                        continuousVoiceMode = false
                    )
                }
            }
        }
    }

    fun toggleContinuousVoiceMode() {
        val next = !_uiState.value.continuousVoiceMode
        _uiState.update { it.copy(continuousVoiceMode = next) }
        if (next) {
            showMessage("Modo Diálogo Oral activo: habla con tu Docente.")
            startVoiceDictation(VoiceTarget.CHAT)
        } else {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
        }
    }

    fun startVoiceDictation(target: VoiceTarget, onNoteTranscribed: ((String) -> Unit)? = null) {
        voiceManager.startListening(target) { text, resolvedTarget ->
            if (resolvedTarget == VoiceTarget.CHAT) {
                sendChatMessage(text, isVoiceInput = true)
            } else {
                onNoteTranscribed?.invoke(text)
            }
        }
    }

    fun stopVoiceDictation() {
        voiceManager.stopListening()
    }

    // ---------- LECTOR VISUAL Y DE TEXTO COMPLETO ----------

    fun openReaderAtCitation(citationLabel: String) {
        val doc = _uiState.value.activeDocument ?: return
        val sections = _uiState.value.activeSections
        var targetIdx = sections.indexOfFirst { it.label.equals(citationLabel, ignoreCase = true) }
        if (targetIdx < 0) {
            val num = Regex("""(\d+)""").find(citationLabel)?.groupValues?.get(1)?.toIntOrNull()
            if (num != null) {
                targetIdx = sections.indexOfFirst { it.index == num }.coerceAtLeast(0)
            }
        }
        navigateTo(CatedraScreen.Reader(doc.id, targetIdx.coerceAtLeast(0)))
    }

    fun jumpReaderToSection(zeroBasedIndex: Int) {
        val doc = _uiState.value.activeDocument ?: return
        val sections = _uiState.value.activeSections
        val safeIdx = zeroBasedIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(readerSectionIndex = safeIdx, isLoadingVisualPage = true) }

        if (doc.docType == DocType.PDF || doc.docType == DocType.PAPER || doc.docType == DocType.IMAGE) {
            viewModelScope.launch {
                val bmp = if (doc.docType == DocType.IMAGE) {
                    UniversalDocumentParser.loadImageBitmap(appContext, doc.sourceUri, doc.localCachedPath)
                } else {
                    UniversalDocumentParser.renderPdfPageBitmap(appContext, doc.sourceUri, doc.localCachedPath, safeIdx)
                }
                _uiState.update { it.copy(readerVisualBitmap = bmp, isLoadingVisualPage = false) }
            }
        } else {
            _uiState.update { it.copy(readerVisualBitmap = null, isLoadingVisualPage = false) }
        }
    }

    fun startReadingActiveDocumentAloud(fromSectionZeroBased: Int = 0) {
        val doc = _uiState.value.activeDocument ?: return
        val sections = _uiState.value.activeSections
        if (sections.isEmpty()) return
        voiceManager.startReadingDocument(doc.title, sections, fromSectionZeroBased)
    }

    fun askAboutCurrentReaderPage(visualAnalysis: Boolean) {
        val doc = _uiState.value.activeDocument ?: return
        val sections = _uiState.value.activeSections
        val idx = _uiState.value.readerSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        val sec = sections.getOrNull(idx) ?: return
        navigateTo(CatedraScreen.Chat(doc.id))
        val prompt = if (visualAnalysis) {
            "Analiza visualmente [${sec.label}] (gráficos, tablas, fórmulas, diagramas o texto escaneado) y explícame con claridad qué muestra."
        } else {
            "Explícame en detalle [${sec.label}] y dime cuál es la idea principal que no debo perder de vista."
        }
        sendChatMessage(
            rawText = prompt,
            attachVisualSectionIndex = if (visualAnalysis) sec.index else null
        )
    }

    // ---------- NOTAS COMPACTAS (.md / .txt) ----------

    fun addManualNote(
        category: NoteCategory,
        content: String,
        citationRef: String = "",
        isFromVoice: Boolean = false
    ) {
        val doc = _uiState.value.activeDocument ?: return
        val clean = content.trim()
        if (clean.isBlank()) return
        val note = StudyNote(
            id = UUID.randomUUID().toString(),
            documentId = doc.id,
            category = category,
            content = clean,
            citationRef = citationRef.trim(),
            isFromVoice = isFromVoice,
            createdAt = System.currentTimeMillis()
        )
        db.insertNote(note)
        val notes = db.getNotes(doc.id)
        val file = knowledgeStore.writeMarkdownNotesFile(doc, notes)
        _uiState.update {
            it.copy(
                activeNotes = notes,
                activeNoteFileBytes = file.length(),
                documents = db.getAllDocuments(),
                storageStats = knowledgeStore.getStorageStats(),
                snackbarMessage = "Guardado en notas compactas (${formatCompactSize(file.length())})"
            )
        }
    }

    fun saveMessageSnippetToNotes(message: ChatMessage, category: NoteCategory = NoteCategory.COMPRENDIDO) {
        val cite = message.citedLabels.firstOrNull().orEmpty()
        db.markMessageSaved(message.id)
        addManualNote(
            category = category,
            content = message.content.take(450),
            citationRef = cite,
            isFromVoice = message.isVoiceInput
        )
        _uiState.value.activeDocument?.let { doc ->
            _uiState.update { it.copy(activeMessages = db.getMessages(doc.id)) }
        }
    }

    fun deleteNote(noteId: String) {
        val doc = _uiState.value.activeDocument ?: return
        db.deleteNote(noteId)
        val notes = db.getNotes(doc.id)
        val file = knowledgeStore.writeMarkdownNotesFile(doc, notes)
        _uiState.update {
            it.copy(
                activeNotes = notes,
                activeNoteFileBytes = file.length(),
                documents = db.getAllDocuments(),
                storageStats = knowledgeStore.getStorageStats()
            )
        }
    }

    /**
     * Pide al Docente destilar la conversación reciente en conocimiento neto
     * (Comprendido, Errores corregidos, Crítica) y guardarlo en .md/.txt.
     */
    fun distillConversationIntoCompactNotes() {
        val doc = _uiState.value.activeDocument ?: return
        if (_uiState.value.isTeacherThinking) return
        _uiState.update { it.copy(isTeacherThinking = true) }

        viewModelScope.launch {
            val res = aiEngine.distillLearningsFromChat(
                settings = _uiState.value.settings,
                document = doc,
                history = _uiState.value.activeMessages,
                existingNotes = _uiState.value.activeNotes
            )
            res.onSuccess { drafts ->
                if (drafts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isTeacherThinking = false,
                            snackbarMessage = "Aún no hay nuevos hallazgos que destilar de la charla."
                        )
                    }
                    return@onSuccess
                }
                val now = System.currentTimeMillis()
                drafts.forEachIndexed { idx, d ->
                    db.insertNote(
                        StudyNote(
                            id = UUID.randomUUID().toString(),
                            documentId = doc.id,
                            category = d.category,
                            content = d.content,
                            citationRef = d.citationRef,
                            isFromVoice = false,
                            createdAt = now + idx
                        )
                    )
                }
                val notes = db.getNotes(doc.id)
                val file = knowledgeStore.writeMarkdownNotesFile(doc, notes)
                _uiState.update {
                    it.copy(
                        activeNotes = notes,
                        activeNoteFileBytes = file.length(),
                        documents = db.getAllDocuments(),
                        storageStats = knowledgeStore.getStorageStats(),
                        isTeacherThinking = false,
                        snackbarMessage = "Se sintetizaron ${drafts.size} aprendizajes (${formatCompactSize(file.length())} en .md)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTeacherThinking = false,
                        snackbarMessage = err.message ?: "Error sintetizando aprendizajes"
                    )
                }
            }
        }
    }

    fun buildShareNoteIntent(asTxt: Boolean = true): Intent? {
        val doc = _uiState.value.activeDocument ?: return null
        val notes = _uiState.value.activeNotes
        val tempFile = knowledgeStore.exportNoteToTxtTempFile(doc, notes, asTxt)
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", tempFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (asTxt) "text/plain" else "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, "Notas Cátedra - ${doc.title}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportActiveNoteToSafFolder(treeUri: Uri) {
        val doc = _uiState.value.activeDocument ?: return
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val updatedSettings = _uiState.value.settings.copy(exportFolderUri = treeUri.toString())
        saveSettings(updatedSettings)
        val res = knowledgeStore.exportNoteToSafTree(treeUri.toString(), doc, _uiState.value.activeNotes)
        res.onSuccess { name -> showMessage("Guardado $name en la carpeta elegida.") }
            .onFailure { err -> showMessage("Error al guardar: ${err.message}") }
    }

    fun syncActiveNoteToGoogleDrive() {
        val doc = _uiState.value.activeDocument ?: return
        val settings = _uiState.value.settings
        if (!settings.hasDriveConnected()) {
            showMessage("Conecta tu cuenta de Google Drive en la pantalla Google Drive o en Ajustes.")
            return
        }
        viewModelScope.launch {
            val tokenRes = driveService.ensureFreshToken(settings)
            val fresh = tokenRes.getOrElse { err ->
                showMessage(err.message ?: "Error renovando sesión de Google Drive")
                return@launch
            }
            if (fresh.accessToken != settings.driveAccessToken) {
                saveSettings(settings.copy(driveAccessToken = fresh.accessToken, driveTokenExpiresAt = fresh.expiresAtMs))
            }
            val md = knowledgeStore.buildCompactMarkdown(doc, _uiState.value.activeNotes)
            val fileName = knowledgeStore.noteFileFor(doc).name
            val res = driveService.uploadMarkdownNote(
                accessToken = fresh.accessToken,
                fileName = fileName,
                markdownContent = md,
                folderId = settings.driveNotesFolderId
            )
            res.onSuccess { folderId ->
                if (folderId.isNotBlank() && folderId != settings.driveNotesFolderId) {
                    saveSettings(_uiState.value.settings.copy(driveNotesFolderId = folderId))
                }
                showMessage("Nota sincronizada en Google Drive / Catedra_Notas ($fileName)")
            }.onFailure { err ->
                showMessage(err.message ?: "Error al subir a Drive")
            }
        }
    }

    fun syncAllNotesToGoogleDrive() {
        val settings = _uiState.value.settings
        if (!settings.hasDriveConnected()) {
            showMessage("Conecta Google Drive primero.")
            return
        }
        viewModelScope.launch {
            val tokenRes = driveService.ensureFreshToken(settings)
            val fresh = tokenRes.getOrElse { err ->
                showMessage(err.message ?: "No se pudo renovar Google Drive")
                return@launch
            }
            if (fresh.accessToken != settings.driveAccessToken) {
                saveSettings(settings.copy(driveAccessToken = fresh.accessToken, driveTokenExpiresAt = fresh.expiresAtMs))
            }
            val docs = db.getAllDocuments()
            var uploaded = 0
            var folderId = settings.driveNotesFolderId
            for (d in docs) {
                val notes = db.getNotes(d.id)
                if (notes.isEmpty()) continue
                val md = knowledgeStore.buildCompactMarkdown(d, notes)
                val fileName = knowledgeStore.noteFileFor(d).name
                val res = driveService.uploadMarkdownNote(fresh.accessToken, fileName, md, folderId)
                res.onSuccess { fid ->
                    if (fid.isNotBlank()) folderId = fid
                    uploaded++
                }
            }
            if (folderId.isNotBlank() && folderId != settings.driveNotesFolderId) {
                saveSettings(_uiState.value.settings.copy(driveNotesFolderId = folderId))
            }
            showMessage(
                if (uploaded > 0) "$uploaded archivo(s) de notas .md sincronizados en Google Drive / Catedra_Notas."
                else "No hay notas escritas todavía para respaldar en Drive."
            )
        }
    }

    fun buildGoogleDriveConnectUrl(): String? {
        val bridge = _uiState.value.settings.driveBridgeUrl.trim()
        if (!bridge.startsWith("http")) return null
        return driveService.buildOAuthStartUrl(bridge)
    }

    fun handleDriveOAuthCallback(callbackUri: Uri) {
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            showMessage("Google Drive cancelado o con error: $error")
            return
        }
        val accessToken = callbackUri.getQueryParameter("access_token").orEmpty()
        val refreshToken = callbackUri.getQueryParameter("refresh_token").orEmpty()
        val expiresIn = callbackUri.getQueryParameter("expires_in")?.toLongOrNull() ?: 3500L
        val emailParam = callbackUri.getQueryParameter("email").orEmpty()
        if (accessToken.isBlank()) {
            showMessage("No se recibió token de Google Drive.")
            return
        }
        val cur = _uiState.value.settings
        val updated = cur.copy(
            driveAccessToken = accessToken,
            driveRefreshToken = refreshToken.ifBlank { cur.driveRefreshToken },
            driveTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L,
            driveUserEmail = emailParam.ifBlank { cur.driveUserEmail }
        )
        saveSettings(updated)
        viewModelScope.launch {
            val resolvedEmail = emailParam.ifBlank { driveService.fetchUserEmail(accessToken) }
            if (resolvedEmail.isNotBlank()) {
                saveSettings(_uiState.value.settings.copy(driveUserEmail = resolvedEmail))
            }
            showMessage("Google Drive conectado${if (resolvedEmail.isNotBlank()) " ($resolvedEmail)" else ""}")
            navigateTo(CatedraScreen.Drive)
            loadDriveFiles()
        }
    }

    fun disconnectGoogleDrive() {
        val cur = _uiState.value.settings
        saveSettings(
            cur.copy(
                driveAccessToken = "",
                driveRefreshToken = "",
                driveTokenExpiresAt = 0L,
                driveUserEmail = "",
                driveNotesFolderId = ""
            )
        )
        _uiState.update { it.copy(driveFiles = emptyList()) }
        showMessage("Sesión de Google Drive desconectada.")
    }

    // ---------- PAPERS ACADÉMICOS ----------

    fun openRelatedPapersForActiveDocument() {
        val doc = _uiState.value.activeDocument ?: return
        val sections = _uiState.value.activeSections
        navigateTo(CatedraScreen.Papers(initialQuery = doc.title.take(80), contextDocId = doc.id))
        viewModelScope.launch {
            aiEngine.suggestRelatedPaperQueries(_uiState.value.settings, doc, sections)
                .onSuccess { queries ->
                    _uiState.update { it.copy(suggestedPaperQueries = queries) }
                }
        }
    }

    fun searchPapers(
        query: String,
        onlyOpenAccess: Boolean = false,
        sourceFilter: String = "ALL",
        sortByCitations: Boolean = false
    ) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingPapers = true) }
            paperService.searchPapers(query, onlyOpenAccess, sourceFilter, sortByCitations)
                .onSuccess { results ->
                    _uiState.update { it.copy(paperResults = results, isSearchingPapers = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSearchingPapers = false,
                            snackbarMessage = err.message ?: "Error buscando papers"
                        )
                    }
                }
        }
    }

    fun importPaperPdf(paper: PaperResult) {
        val pdfUrl = paper.openAccessPdfUrl
        if (pdfUrl.isNullOrBlank()) {
            studyPaperFromAbstract(paper)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingPaperId = paper.id) }
            val safeId = paper.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dest = File(knowledgeStore.papersDir, "$safeId.pdf")
            val downloadRes = paperService.downloadPdf(pdfUrl, dest)
            _uiState.update { it.copy(downloadingPaperId = null) }
            downloadRes.onSuccess { file ->
                importDocumentFromUri(
                    uri = Uri.fromFile(file),
                    forcedFileName = "${paper.title.take(60)}.pdf",
                    forcedMime = "application/pdf",
                    localCachedPath = file.absolutePath,
                    paperMeta = paper
                )
            }.onFailure { err ->
                showMessage("${err.message} Abriendo ficha desde el resumen...")
                studyPaperFromAbstract(paper)
            }
        }
    }

    fun studyPaperFromAbstract(paper: PaperResult) {
        val body = buildString {
            appendLine("# ${paper.title}")
            appendLine("Autores: ${paper.authors}")
            appendLine("Año: ${paper.year ?: "N/D"} | Fuente: ${paper.venue} (${paper.source}) | Citas: ${paper.citationCount}")
            if (!paper.doi.isNullOrBlank()) appendLine("DOI: ${paper.doi}")
            if (!paper.landingPageUrl.isNullOrBlank()) appendLine("Enlace: ${paper.landingPageUrl}")
            appendLine()
            appendLine("## Resumen / Abstract")
            appendLine(paper.abstractText.ifBlank { "Resumen no disponible en abierto; consulta el enlace DOI para texto completo." })
        }
        createTextStudyDocument(paper.title, body, paperMeta = paper)
    }

    fun comparePaperInCurrentChat(paper: PaperResult, contextDocId: String) {
        navigateTo(CatedraScreen.Chat(contextDocId))
        val comparePrompt = buildString {
            append("Compara nuestra lectura actual con este paper relacionado:\n")
            append("**${paper.title}** (${paper.authors}, ${paper.year ?: "s/f"}).\n")
            if (paper.abstractText.isNotBlank()) {
                append("Resumen del paper: ${paper.abstractText.take(900)}\n\n")
            }
            append("¿En qué coinciden, dónde se contradicen y qué aporta este otro paper a la comprensión crítica del tema?")
        }
        sendChatMessage(comparePrompt)
    }

    // ---------- GOOGLE DRIVE API ----------

    fun loadDriveFiles(searchQuery: String = "") {
        val settings = _uiState.value.settings
        if (!settings.hasDriveConnected()) {
            _uiState.update { it.copy(driveFiles = emptyList(), isLoadingDrive = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDrive = true) }
            val fresh = driveService.ensureFreshToken(settings).getOrElse { err ->
                _uiState.update { it.copy(isLoadingDrive = false, snackbarMessage = err.message) }
                return@launch
            }
            if (fresh.accessToken != settings.driveAccessToken) {
                saveSettings(settings.copy(driveAccessToken = fresh.accessToken, driveTokenExpiresAt = fresh.expiresAtMs))
            }
            driveService.listStudyFiles(fresh.accessToken, searchQuery)
                .onSuccess { files ->
                    _uiState.update { it.copy(driveFiles = files, isLoadingDrive = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingDrive = false,
                            snackbarMessage = err.message ?: "Error al listar Google Drive"
                        )
                    }
                }
        }
    }

    fun importDriveFile(item: DriveFileItem) {
        val settings = _uiState.value.settings
        if (!settings.hasDriveConnected()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingDocument = true, importStatusText = "Descargando de Google Drive...") }
            val fresh = driveService.ensureFreshToken(settings).getOrElse { err ->
                _uiState.update {
                    it.copy(isImportingDocument = false, importStatusText = "", snackbarMessage = err.message)
                }
                return@launch
            }
            if (fresh.accessToken != settings.driveAccessToken) {
                saveSettings(settings.copy(driveAccessToken = fresh.accessToken, driveTokenExpiresAt = fresh.expiresAtMs))
            }
            val res = driveService.downloadOrExportFile(fresh.accessToken, item, knowledgeStore.importsDir)
            res.onSuccess { (file, mime) ->
                importDocumentFromUri(
                    uri = Uri.fromFile(file),
                    forcedFileName = file.name.substringAfter('_'),
                    forcedMime = mime,
                    localCachedPath = file.absolutePath,
                    driveFileId = item.id
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isImportingDocument = false,
                        importStatusText = "",
                        snackbarMessage = err.message ?: "Error descargando archivo de Drive"
                    )
                }
            }
        }
    }

    // ---------- AJUSTES & NVIDIA SKILLS ----------

    fun saveSettings(newSettings: AppSettings) {
        settingsStore.save(newSettings)
        voiceManager.setSpeechRate(newSettings.speechRate)
        _uiState.update { it.copy(settings = newSettings) }
    }

    fun fetchNvidiaModelCatalog() {
        val key = _uiState.value.settings.nvidiaApiKey
        if (key.isBlank()) {
            showMessage("Ingresa primero tu NVIDIA API Key (nvapi-...) arriba.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNvidiaCatalog = true) }
            aiEngine.fetchNvidiaAvailableModels(key)
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            nvidiaCatalogModels = models,
                            isLoadingNvidiaCatalog = false,
                            snackbarMessage = "Catálogo NVIDIA cargado: ${models.size} modelos disponibles."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingNvidiaCatalog = false,
                            snackbarMessage = err.message ?: "No se pudo consultar el catálogo NVIDIA"
                        )
                    }
                }
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
            val res = aiEngine.testConnection(_uiState.value.settings)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestResult = res.fold(
                        onSuccess = { msg -> "OK · $msg" },
                        onFailure = { err -> "Error · ${err.message}" }
                    )
                )
            }
        }
    }

    fun compactAllChatsKeepingNotes() {
        val removed = db.compactMessages(keepLastPerDoc = 8)
        _uiState.update {
            it.copy(
                storageStats = knowledgeStore.getStorageStats(),
                snackbarMessage = "Historial compactado ($removed mensajes antiguos borrados; tus notas .md están 100% intactas)."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
