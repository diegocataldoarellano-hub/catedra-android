package com.catedra.app.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.catedra.app.data.model.DocSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isDocumentReading: Boolean = false,
    val documentId: String = "",
    val currentTitle: String = "",
    val currentSectionIndex: Int = 0, // 0-based
    val totalSections: Int = 0,
    val currentChunkIndex: Int = 0,
    val totalChunksInSection: Int = 0,
    val speechRate: Float = 1.05f
)

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsLevel: Float = 0f,
    val target: VoiceTarget = VoiceTarget.CHAT,
    val error: String? = null
)

enum class VoiceTarget {
    CHAT,
    NOTE
}

class VoiceAndTtsManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _ttsState = MutableStateFlow(TtsUiState())
    val ttsState: StateFlow<TtsUiState> = _ttsState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceInputState())
    val voiceState: StateFlow<VoiceInputState> = _voiceState.asStateFlow()

    private var readingSections: List<DocSection> = emptyList()
    private var readingDocId: String = ""
    private var readingDocTitle: String = ""
    private var currentChunks: List<String> = emptyList()
    private var sectionCursor: Int = 0
    private var chunkCursor: Int = 0
    private var onSingleSpeechDone: (() -> Unit)? = null

    private var onFinalVoiceResult: ((String, VoiceTarget) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val esLocale = Locale("es", "ES")
                val res = tts?.setLanguage(esLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(_ttsState.value.speechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            handleUtteranceCompleted(utteranceId)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            handleUtteranceCompleted(utteranceId)
                        }
                    }
                })
                _ttsState.value = _ttsState.value.copy(isReady = true)
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 2.2f)
        tts?.setSpeechRate(clamped)
        _ttsState.value = _ttsState.value.copy(speechRate = clamped)
    }

    fun speakSingleMessage(text: String, onFinished: (() -> Unit)? = null) {
        stopSpeaking()
        onSingleSpeechDone = onFinished
        val chunks = splitForSpeech(cleanForSpeech(text))
        if (chunks.isEmpty()) {
            onFinished?.invoke()
            return
        }
        readingSections = listOf(DocSection(1, "Docente", "Respuesta", chunks.joinToString("\n")))
        readingDocId = ""
        readingDocTitle = "Docente"
        currentChunks = chunks
        sectionCursor = 0
        chunkCursor = 0
        _ttsState.value = _ttsState.value.copy(
            isPlaying = true,
            isDocumentReading = false,
            documentId = "",
            currentTitle = "Escuchando al Docente",
            currentSectionIndex = 0,
            totalSections = 1,
            currentChunkIndex = 0,
            totalChunksInSection = chunks.size
        )
        speakCurrentChunk(isSingleMessage = true)
    }

    fun startReadingDocument(
        docId: String,
        docTitle: String,
        sections: List<DocSection>,
        startSectionZeroBased: Int = 0
    ) {
        if (sections.isEmpty()) return
        stopSpeaking()
        onSingleSpeechDone = null
        readingSections = sections
        readingDocId = docId
        readingDocTitle = docTitle
        sectionCursor = startSectionZeroBased.coerceIn(0, sections.lastIndex)
        loadSectionChunks(sectionCursor)
        speakCurrentChunk(isSingleMessage = false)
    }

    private fun loadSectionChunks(secIdx: Int) {
        val sec = readingSections[secIdx]
        val spoken = cleanForSpeech("${sec.label}. ${sec.content}")
        currentChunks = splitForSpeech(spoken).ifEmpty { listOf("${sec.label}. Página visual sin texto plano.") }
        chunkCursor = 0
        _ttsState.value = _ttsState.value.copy(
            isPlaying = true,
            isDocumentReading = true,
            documentId = readingDocId,
            currentTitle = "$readingDocTitle · ${sec.label}",
            currentSectionIndex = secIdx,
            totalSections = readingSections.size,
            currentChunkIndex = 0,
            totalChunksInSection = currentChunks.size
        )
    }

    private fun speakCurrentChunk(isSingleMessage: Boolean) {
        val text = currentChunks.getOrNull(chunkCursor) ?: return
        val uid = if (isSingleMessage) "msg_${chunkCursor}" else "doc_${sectionCursor}_${chunkCursor}"
        tts?.setSpeechRate(_ttsState.value.speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    private fun handleUtteranceCompleted(utteranceId: String?) {
        if (!_ttsState.value.isPlaying) return
        val isSingle = utteranceId?.startsWith("msg_") == true
        chunkCursor++
        if (chunkCursor < currentChunks.size) {
            _ttsState.value = _ttsState.value.copy(currentChunkIndex = chunkCursor)
            speakCurrentChunk(isSingle)
        } else if (!isSingle && sectionCursor + 1 < readingSections.size) {
            sectionCursor++
            loadSectionChunks(sectionCursor)
            speakCurrentChunk(false)
        } else {
            val callback = onSingleSpeechDone
            onSingleSpeechDone = null
            _ttsState.value = _ttsState.value.copy(
                isPlaying = false,
                isDocumentReading = false,
                documentId = "",
                currentTitle = "",
                currentChunkIndex = 0,
                totalChunksInSection = 0
            )
            callback?.invoke()
        }
    }

    fun togglePlayPauseDocument() {
        val st = _ttsState.value
        if (st.isPlaying) {
            tts?.stop()
            _ttsState.value = st.copy(isPlaying = false)
        } else if (readingSections.isNotEmpty() && currentChunks.isNotEmpty()) {
            _ttsState.value = st.copy(isPlaying = true)
            speakCurrentChunk(!st.isDocumentReading)
        }
    }

    fun jumpToSection(zeroBasedIndex: Int) {
        if (!_ttsState.value.isDocumentReading || readingSections.isEmpty()) return
        val next = zeroBasedIndex.coerceIn(0, readingSections.lastIndex)
        tts?.stop()
        sectionCursor = next
        loadSectionChunks(sectionCursor)
        speakCurrentChunk(false)
    }

    fun skipSection(delta: Int) {
        if (!_ttsState.value.isDocumentReading || readingSections.isEmpty()) return
        jumpToSection(sectionCursor + delta)
    }

    fun stopSpeaking() {
        onSingleSpeechDone = null
        tts?.stop()
        _ttsState.value = _ttsState.value.copy(
            isPlaying = false,
            isDocumentReading = false,
            documentId = "",
            currentTitle = "",
            currentChunkIndex = 0,
            totalChunksInSection = 0
        )
    }

    fun startListening(target: VoiceTarget, onFinalResult: (String, VoiceTarget) -> Unit) {
        stopSpeaking()
        onFinalVoiceResult = onFinalResult
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _voiceState.value = VoiceInputState(
                    isListening = false,
                    target = target,
                    error = "El reconocimiento de voz no está disponible en este dispositivo."
                )
                return@post
            }
            runCatching { speechRecognizer?.destroy() }
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceInputState(isListening = true, target = target)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _voiceState.value = _voiceState.value.copy(rmsLevel = norm)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio del micrófono."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se escuchó voz clara. Intenta hablar más cerca."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo agotado sin voz detectada."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para dictado."
                        else -> null
                    }
                    _voiceState.value = VoiceInputState(isListening = false, target = target, error = msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    _voiceState.value = VoiceInputState(isListening = false, target = target)
                    if (text.isNotBlank()) {
                        onFinalVoiceResult?.invoke(text, target)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        _voiceState.value = _voiceState.value.copy(partialText = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _voiceState.value = VoiceInputState(isListening = true, target = target)
            sr.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _voiceState.value = _voiceState.value.copy(isListening = false)
        }
    }

    fun clearVoiceError() {
        _voiceState.value = _voiceState.value.copy(error = null)
    }

    fun release() {
        stopSpeaking()
        tts?.shutdown()
        tts = null
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun cleanForSpeech(raw: String): String {
        return raw
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
            .replace(Regex("""\*(.*?)\*"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""\[Pág\.\s*(\d+)\]"""), "página $1")
            .replace(Regex("""\[Diap\.\s*(\d+)\]"""), "diapositiva $1")
            .replace(Regex("""\[Cap\.\s*(\d+)\]"""), "capítulo $1")
            .replace("|", ", ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun splitForSpeech(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("""(?<=[.!?;\n])\s+"""))
        val cur = StringBuilder()
        for (s in sentences) {
            val trimmed = s.trim()
            if (trimmed.isEmpty()) continue
            if (cur.length + trimmed.length > 360 && cur.isNotEmpty()) {
                chunks.add(cur.toString().trim())
                cur.setLength(0)
            }
            if (trimmed.length > 420) {
                if (cur.isNotEmpty()) {
                    chunks.add(cur.toString().trim())
                    cur.setLength(0)
                }
                var start = 0
                while (start < trimmed.length) {
                    val end = minOf(start + 360, trimmed.length)
                    chunks.add(trimmed.substring(start, end))
                    start = end
                }
            } else {
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(trimmed)
            }
        }
        if (cur.isNotBlank()) chunks.add(cur.toString().trim())
        return chunks
    }
}
