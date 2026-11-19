package com.catedra.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.catedra.app.media.VoiceTarget
import com.catedra.app.ui.CatedraScreen
import com.catedra.app.ui.CatedraViewModel
import com.catedra.app.ui.screens.ChatScreen
import com.catedra.app.ui.screens.DocumentReaderScreen
import com.catedra.app.ui.screens.DriveBrowserScreen
import com.catedra.app.ui.screens.EcosystemHubScreen
import com.catedra.app.ui.screens.HomeScreen
import com.catedra.app.ui.screens.NotesScreen
import com.catedra.app.ui.screens.PaperSearchScreen
import com.catedra.app.ui.screens.QuizScreen
import com.catedra.app.ui.screens.SettingsScreen
import com.catedra.app.ui.theme.CatedraTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: CatedraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            CatedraRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    if (uri.scheme == "catedra" && uri.host == "oauth" && uri.path?.startsWith("/drive") == true) {
                        viewModel.handleDriveOAuthCallback(uri)
                    } else {
                        viewModel.importDocumentFromUri(uri)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) {
                    viewModel.importDocumentFromUri(streamUri)
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                    if (!sharedText.isNullOrBlank()) {
                        // Si comparten un enlace de Google Scholar, DOI, arXiv o PDF -> resolver/descargar paper!
                        val looksLikePaperLink = sharedText.contains("arxiv.org", true) ||
                            sharedText.contains("doi.org", true) ||
                            sharedText.contains("scholar.google", true) ||
                            sharedText.lowercase().endsWith(".pdf") ||
                            Regex("""10\.\d{4,9}/\S+""").containsMatchIn(sharedText)
                        if (looksLikePaperLink) {
                            viewModel.importPaperFromUrlOrDoi(sharedText)
                        } else {
                            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Texto compartido"
                            viewModel.createTextStudyDocument(subject, sharedText)
                        }
                    }
                }
            }
        }
    }
}

private fun openInCustomTab(context: Context, rawUrl: String) {
    val uri = Uri.parse(rawUrl)
    runCatching {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.parseColor("#2AABEE"))
            .build()
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

@Composable
private fun CatedraRoot(viewModel: CatedraViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDocumentFromUri(uri)
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.exportActiveNoteToSafFolder(treeUri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            viewModel.importDocumentFromUri(
                uri = Uri.fromFile(file),
                forcedFileName = "Apunte_${System.currentTimeMillis() % 10000}.jpg",
                forcedMime = "image/jpeg",
                localCachedPath = file.absolutePath
            )
        }
        pendingCameraFile = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingVoiceAction?.invoke()
        } else {
            viewModel.showMessage("Se necesita permiso de micrófono para usar notas de voz y diálogo oral.")
        }
        pendingVoiceAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingVoiceAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun launchCameraScan() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    fun launchAnyDocumentPicker() {
        openDocumentLauncher.launch(arrayOf("*/*"))
    }

    fun shareNotes(asTxt: Boolean = true) {
        val shareIntent = viewModel.buildShareNoteIntent(asTxt) ?: return
        context.startActivity(Intent.createChooser(shareIntent, "Exportar notas compactas"))
    }

    fun cycleSpeechRate() {
        val rates = listOf(0.85f, 1.0f, 1.25f, 1.5f, 1.85f)
        val cur = ttsState.speechRate
        val next = rates.firstOrNull { it > cur + 0.05f } ?: rates.first()
        viewModel.saveSettings(uiState.settings.copy(speechRate = next))
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(voiceState.error) {
        voiceState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.voiceManager.clearVoiceError()
        }
    }

    BackHandler(enabled = uiState.currentScreen != CatedraScreen.Home) {
        viewModel.navigateBack()
    }

    CatedraTheme(themeMode = uiState.settings.themeMode) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (val screen = uiState.currentScreen) {
                    is CatedraScreen.Home -> {
                        HomeScreen(
                            uiState = uiState,
                            onSelectDocument = { doc -> viewModel.navigateTo(CatedraScreen.Chat(doc.id)) },
                            onFolderTabChange = viewModel::setHomeFolderTab,
                            onSearchQueryChange = viewModel::setHomeSearchQuery,
                            onPickFile = ::launchAnyDocumentPicker,
                            onPickCameraPhoto = ::launchCameraScan,
                            onCreatePastedText = { title, body -> viewModel.createTextStudyDocument(title, body) },
                            onOpenPapers = { viewModel.navigateTo(CatedraScreen.Papers()) },
                            onOpenEcosystem = { viewModel.navigateTo(CatedraScreen.Ecosystem) },
                            onOpenDrive = { viewModel.navigateTo(CatedraScreen.Drive) },
                            onOpenSettings = { viewModel.navigateTo(CatedraScreen.Settings) },
                            onTogglePin = viewModel::togglePinDocument,
                            onRenameDoc = viewModel::renameDocument,
                            onDeleteDoc = viewModel::deleteDocument
                        )
                    }

                    is CatedraScreen.Chat -> {
                        ChatScreen(
                            uiState = uiState,
                            ttsState = ttsState,
                            voiceState = voiceState,
                            onBack = { viewModel.navigateBack() },
                            onOpenReader = { viewModel.navigateTo(CatedraScreen.Reader(screen.documentId, 0)) },
                            onOpenReaderAtCitation = viewModel::openReaderAtCitation,
                            onOpenNotes = { viewModel.navigateTo(CatedraScreen.Notes(screen.documentId)) },
                            onSendMessage = { txt -> viewModel.sendChatMessage(txt) },
                            onStartVoiceChat = {
                                runWithMicPermission {
                                    viewModel.startVoiceDictation(VoiceTarget.CHAT)
                                }
                            },
                            onStopVoiceChat = viewModel::stopVoiceDictation,
                            onToggleContinuousVoice = {
                                runWithMicPermission {
                                    viewModel.toggleContinuousVoiceMode()
                                }
                            },
                            onReadDocumentAloud = { viewModel.startReadingActiveDocumentAloud(0) },
                            onSpeakMessage = { txt -> viewModel.voiceManager.speakSingleMessage(txt) },
                            onSaveMessageToNotes = { msg -> viewModel.saveMessageSnippetToNotes(msg) },
                            onDistillNotes = viewModel::distillConversationIntoCompactNotes,
                            onOpenQuiz = { viewModel.navigateTo(CatedraScreen.Quiz(screen.documentId)) },
                            onPlayAudioOverview = {
                                uiState.activeDocument?.let { viewModel.generateAndPlayAudioOverview(it) }
                            },
                            onSendToNotebookLm = { viewModel.navigateTo(CatedraScreen.Ecosystem) },
                            onFindRelatedPapers = viewModel::openRelatedPapersForActiveDocument,
                            onShareNotesTxt = { shareNotes(asTxt = true) },
                            onToggleTtsPlayPause = viewModel.voiceManager::togglePlayPauseDocument,
                            onPrevTtsSection = { viewModel.voiceManager.skipSection(-1) },
                            onNextTtsSection = { viewModel.voiceManager.skipSection(1) },
                            onCycleTtsSpeed = ::cycleSpeechRate,
                            onStopTts = viewModel.voiceManager::stopSpeaking
                        )
                    }

                    is CatedraScreen.Reader -> {
                        DocumentReaderScreen(
                            uiState = uiState,
                            ttsState = ttsState,
                            onBack = { viewModel.navigateBack() },
                            onJumpToSection = viewModel::jumpReaderToSection,
                            onReadAloudFromHere = viewModel::startReadingActiveDocumentAloud,
                            onTranslateSection = viewModel::translateCurrentReaderSection,
                            onAskTeacherPage = viewModel::askAboutCurrentReaderPage,
                            onAddPageNote = { cat, content, cite ->
                                viewModel.addManualNote(cat, content, cite, isFromVoice = false)
                            },
                            onDictateNote = { onSpoken ->
                                runWithMicPermission {
                                    viewModel.startVoiceDictation(VoiceTarget.NOTE, onNoteTranscribed = onSpoken)
                                }
                            },
                            onToggleTtsPlayPause = viewModel.voiceManager::togglePlayPauseDocument,
                            onPrevTtsSection = { viewModel.voiceManager.skipSection(-1) },
                            onNextTtsSection = { viewModel.voiceManager.skipSection(1) },
                            onCycleTtsSpeed = ::cycleSpeechRate,
                            onStopTts = viewModel.voiceManager::stopSpeaking
                        )
                    }

                    is CatedraScreen.Notes -> {
                        val doc = uiState.activeDocument
                        val mdPreview = remember(doc, uiState.activeNotes) {
                            if (doc != null) viewModel.knowledgeStore.buildCompactMarkdown(doc, uiState.activeNotes)
                            else ""
                        }
                        NotesScreen(
                            uiState = uiState,
                            voiceState = voiceState,
                            markdownPreview = mdPreview,
                            onBack = { viewModel.navigateBack() },
                            onAddNote = viewModel::addManualNote,
                            onDeleteNote = viewModel::deleteNote,
                            onDistillFromChat = viewModel::distillConversationIntoCompactNotes,
                            onStartVoiceNote = { onSpoken ->
                                runWithMicPermission {
                                    viewModel.startVoiceDictation(VoiceTarget.NOTE, onNoteTranscribed = onSpoken)
                                }
                            },
                            onStopVoice = viewModel::stopVoiceDictation,
                            onShareTxt = { shareNotes(asTxt = true) },
                            onSaveToFolder = {
                                val savedTree = uiState.settings.exportFolderUri
                                if (savedTree.isNotBlank()) {
                                    viewModel.exportActiveNoteToSafFolder(Uri.parse(savedTree))
                                } else {
                                    openTreeLauncher.launch(null)
                                }
                            },
                            onUploadToDrive = viewModel::syncActiveNoteToGoogleDrive
                        )
                    }

                    is CatedraScreen.Papers -> {
                        PaperSearchScreen(
                            uiState = uiState,
                            initialQuery = screen.initialQuery,
                            contextDocId = screen.contextDocId,
                            onBack = { viewModel.navigateBack() },
                            onSearch = viewModel::searchPapers,
                            onOpenGoogleScholar = { q -> openInCustomTab(context, viewModel.buildGoogleScholarUrl(q)) },
                            onImportUrlOrDoi = viewModel::importPaperFromUrlOrDoi,
                            onOpenWebUrl = { url -> openInCustomTab(context, url) },
                            onFormatApa = viewModel::formatApaCitation,
                            onFormatBibTex = viewModel::formatBibTexCitation,
                            onShowMessage = viewModel::showMessage,
                            onImportPdf = viewModel::importPaperPdf,
                            onStudyAbstract = viewModel::studyPaperFromAbstract,
                            onCompareInChat = viewModel::comparePaperInCurrentChat
                        )
                    }

                    is CatedraScreen.Quiz -> {
                        QuizScreen(
                            quizState = uiState.quizState,
                            onBack = { viewModel.navigateBack() },
                            onRegenerateQuiz = viewModel::generateQuizForActiveDocument,
                            onSelectOption = viewModel::selectQuizOption,
                            onCitationClick = viewModel::openReaderAtCitation,
                            onSaveMistakesToNotes = viewModel::saveQuizMistakesToNotes,
                            onBuildFormsAppsScript = viewModel::buildGoogleFormsAppsScript,
                            onOpenUrlInBrowser = { url -> openInCustomTab(context, url) },
                            onShowMessage = viewModel::showMessage
                        )
                    }

                    is CatedraScreen.Ecosystem -> {
                        EcosystemHubScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateBack() },
                            onOpenUrl = { url -> openInCustomTab(context, url) },
                            onBuildNotebookLmBundle = viewModel::buildNotebookLmBundle,
                            onUploadNotebookLmToDrive = viewModel::uploadNotebookLmBundleToDrive,
                            onPlayAudioOverview = viewModel::generateAndPlayAudioOverview,
                            onLaunchQuizForDocument = { doc -> viewModel.navigateTo(CatedraScreen.Quiz(doc.id)) },
                            onOpenDriveForForms = { viewModel.navigateTo(CatedraScreen.Drive) },
                            onSearchGoogleScholar = { q -> openInCustomTab(context, viewModel.buildGoogleScholarUrl(q)) },
                            onImportPaperLinkOrDoi = viewModel::importPaperFromUrlOrDoi,
                            onShowMessage = viewModel::showMessage
                        )
                    }

                    is CatedraScreen.Drive -> {
                        DriveBrowserScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateBack() },
                            onOpenInstantSafPicker = ::launchAnyDocumentPicker,
                            onConnectDriveOAuth = {
                                val url = viewModel.buildGoogleDriveConnectUrl()
                                if (url != null) {
                                    openInCustomTab(context, url)
                                } else {
                                    viewModel.showMessage("Primero pega la URL de tu puente Render en Ajustes → Google Drive.")
                                    viewModel.navigateTo(CatedraScreen.Settings)
                                }
                            },
                            onSyncAllNotesToDrive = viewModel::syncAllNotesToGoogleDrive,
                            onDisconnectDrive = viewModel::disconnectGoogleDrive,
                            onRefreshDrive = viewModel::loadDriveFiles,
                            onImportDriveFile = viewModel::importDriveFile,
                            onOpenGoogleForm = { form ->
                                openInCustomTab(context, "https://docs.google.com/forms/d/${form.id}/edit")
                            },
                            onOpenSettings = { viewModel.navigateTo(CatedraScreen.Settings) }
                        )
                    }

                    is CatedraScreen.Settings -> {
                        SettingsScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateBack() },
                            onSaveSettings = viewModel::saveSettings,
                            onFetchNvidiaCatalog = viewModel::fetchNvidiaModelCatalog,
                            onTestConnection = viewModel::testAiConnection,
                            onCompactHistory = viewModel::compactAllChatsKeepingNotes
                        )
                    }
                }
            }
        }
    }
}
