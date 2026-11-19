package com.catedra.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.ChatMessage
import com.catedra.app.data.model.MessageRole
import com.catedra.app.media.TtsUiState
import com.catedra.app.media.VoiceInputState
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.DocAvatar
import com.catedra.app.ui.components.FormattedStudyText
import com.catedra.app.ui.components.TtsMiniPlayerBar
import com.catedra.app.ui.components.formatRelativeTime
import com.catedra.app.ui.theme.LocalCatedraColors

@Composable
fun ChatScreen(
    uiState: CatedraUiState,
    ttsState: TtsUiState,
    voiceState: VoiceInputState,
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenReaderAtCitation: (String) -> Unit,
    onOpenNotes: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStartVoiceChat: () -> Unit,
    onStopVoiceChat: () -> Unit,
    onToggleContinuousVoice: () -> Unit,
    onReadDocumentAloud: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onSaveMessageToNotes: (ChatMessage) -> Unit,
    onDistillNotes: () -> Unit,
    onOpenQuiz: () -> Unit,
    onPlayAudioOverview: () -> Unit,
    onSendToNotebookLm: () -> Unit,
    onFindRelatedPapers: () -> Unit,
    onShareNotesTxt: () -> Unit,
    onShowMessage: (String) -> Unit,
    onToggleTtsPlayPause: () -> Unit,
    onPrevTtsSection: () -> Unit,
    onNextTtsSection: () -> Unit,
    onCycleTtsSpeed: () -> Unit,
    onStopTts: () -> Unit
) {
    val colors = LocalCatedraColors.current
    val context = LocalContext.current
    val doc = uiState.activeDocument ?: return
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.activeMessages.size, uiState.isTeacherThinking) {
        if (uiState.activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.activeMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.chatCanvas)
            .imePadding()
    ) {
        // Top Bar minimalista idéntica a un chat de Telegram
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás", tint = colors.textPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenReader)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocAvatar(docType = doc.docType, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.totalSections} secciones · ${formatCompactSize(uiState.activeNoteFileBytes)} notas · Toca para leer",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Modo Diálogo Oral continuo
            IconButton(onClick = onToggleContinuousVoice) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = "Modo diálogo oral",
                    tint = if (uiState.continuousVoiceMode) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Notas compactas (.md / .txt)
            IconButton(onClick = onOpenNotes) {
                Box {
                    Icon(
                        imageVector = Icons.Rounded.NoteAlt,
                        contentDescription = "Notas compactas",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (uiState.activeNotes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más", tint = colors.textSecondary)
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Abrir lector completo", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onOpenReader() }
                    )
                    DropdownMenuItem(
                        text = { Text("Escuchar documento en voz alta", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onReadDocumentAloud() }
                    )
                    DropdownMenuItem(
                        text = { Text("Audio-Resumen (estilo NotebookLM)", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onPlayAudioOverview() }
                    )
                    DropdownMenuItem(
                        text = { Text("Quiz diagnóstico / Google Forms", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onOpenQuiz() }
                    )
                    DropdownMenuItem(
                        text = { Text("Enviar a NotebookLM / Forms / Scholar", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onSendToNotebookLm() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sintetizar lo aprendido (.md)", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onDistillNotes() }
                    )
                    DropdownMenuItem(
                        text = { Text("Buscar papers relacionados", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onFindRelatedPapers() }
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar notas (.txt)", color = colors.textPrimary) },
                        onClick = { showOverflowMenu = false; onShareNotesTxt() }
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Barra fija de lectura en voz alta (estilo reproductor de audio Telegram)
        TtsMiniPlayerBar(
            ttsState = ttsState,
            onTogglePlayPause = onToggleTtsPlayPause,
            onPrevSection = onPrevTtsSection,
            onNextSection = onNextTtsSection,
            onCycleSpeed = onCycleTtsSpeed,
            onStop = onStopTts
        )

        // Mensajes del diálogo con el Docente
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.activeMessages, key = { it.id }) { msg ->
                TelegramMessageBubble(
                    message = msg,
                    showReasoningByDefault = uiState.settings.showReasoningSummary,
                    onCitationClick = onOpenReaderAtCitation,
                    onSpeak = { onSpeakMessage(msg.content) },
                    onSaveToNotes = { onSaveMessageToNotes(msg) },
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Cátedra", msg.content))
                        onShowMessage("Mensaje copiado al portapapeles.")
                    }
                )
            }

            if (uiState.isTeacherThinking) {
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                            .background(colors.bubbleIncoming)
                            .border(0.5.dp, colors.bubbleBorder, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                        Spacer(Modifier.width(10.dp))
                        val thinkingLabel = if (uiState.settings.hasNvidiaSkillsReady() && uiState.settings.useNvidiaRerank) {
                            "El Docente consulta pasajes (NVIDIA NeMo)..."
                        } else {
                            "El Docente está revisando el texto..."
                        }
                        Text(thinkingLabel, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
            }
        }

        // Indicador de dictado de voz en vivo
        if (voiceState.isListening) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentSoft)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = voiceState.partialText.ifBlank { "Escuchando... habla con naturalidad" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onStopVoiceChat, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Detener voz", tint = colors.danger, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Acciones pedagógicas rápidas ultra-discretas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            QuickTutorChip("Resumen") { onSendMessage("/resumen") }
            QuickTutorChip("Quiz / Forms") { onOpenQuiz() }
            QuickTutorChip("Preguntas") { onSendMessage("/preguntas") }
            QuickTutorChip("Evaluar comprensión") { onSendMessage("/evaluar") }
            QuickTutorChip("Crítica") { onSendMessage("/critica") }
            QuickTutorChip("Audio-Resumen") { onPlayAudioOverview() }
            QuickTutorChip("Guardar aprendido") { onDistillNotes() }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Barra de escritura estilo Telegram (Adjunto/Lector + Campo limpio + Mic/Enviar dinámico)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onOpenReader, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = "Abrir lector",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Conversa con el docente...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(6.dp))

            val hasText = inputText.isNotBlank()
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (hasText || voiceState.isListening) colors.accent else colors.accentSoft)
                    .clickable {
                        if (hasText) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        } else if (voiceState.isListening) {
                            onStopVoiceChat()
                        } else {
                            onStartVoiceChat()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        hasText -> Icons.AutoMirrored.Rounded.Send
                        voiceState.isListening -> Icons.Rounded.Stop
                        else -> Icons.Rounded.Mic
                    },
                    contentDescription = if (hasText) "Enviar" else "Nota de voz",
                    tint = if (hasText || voiceState.isListening) Color.White else colors.accent,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelegramMessageBubble(
    message: ChatMessage,
    showReasoningByDefault: Boolean,
    onCitationClick: (String) -> Unit,
    onSpeak: () -> Unit,
    onSaveToNotes: () -> Unit,
    onCopy: () -> Unit
) {
    val colors = LocalCatedraColors.current
    var showReasoning by remember(message.id) { mutableStateOf(showReasoningByDefault) }

    if (message.role == MessageRole.SYSTEM) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        return
    }

    val isUser = message.role == MessageRole.USER
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 315.dp)
                .clip(shape)
                .background(if (isUser) colors.bubbleOutgoing else colors.bubbleIncoming)
                .border(0.5.dp, if (isUser) Color.Transparent else colors.bubbleBorder, shape)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            // Indicador discreto de nota de voz o análisis visual adjunto
            if (isUser && (message.isVoiceInput || message.attachedSectionIndex != null)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    if (message.isVoiceInput) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nota de voz transcrita", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                    if (message.attachedSectionIndex != null) {
                        if (message.isVoiceInput) Spacer(Modifier.width(8.dp))
                        Text("Visual Pág. ${message.attachedSectionIndex}", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                    }
                }
            }

            // Razonamiento colapsable (cuando Nemotron / DeepSeek-R1 emite pensamiento)
            if (!isUser && !message.reasoning.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated)
                        .clickable { showReasoning = !showReasoning }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Psychology, contentDescription = null, tint = colors.nvidiaGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (showReasoning) "Ocultar razonamiento" else "Ver razonamiento del Docente",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                if (showReasoning) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            FormattedStudyText(
                text = message.content,
                color = colors.textPrimary
            )

            // Chips clickeables de citas exactas -> saltan directo a esa página en el Lector!
            if (!isUser && message.citedLabels.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    message.citedLabels.forEach { cite ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.citationBg)
                                .clickable { onCitationClick(cite) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "↗ $cite",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.citationText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Pie sutil de burbuja: acciones rápidas + hora + motor
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isUser) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Escuchar",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onSpeak)
                        )
                    }
                    Icon(
                        imageVector = if (message.isSavedToNotes) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Guardar en notas",
                        tint = if (message.isSavedToNotes) colors.accent else colors.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onSaveToNotes)
                    )
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onCopy)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isUser && !message.engineBadge.isNullOrBlank()) {
                        val isNv = message.engineBadge.contains("NVIDIA", true) || message.engineBadge.contains("NeMo", true)
                        Text(
                            text = message.engineBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isNv) colors.nvidiaGreen else colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 135.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = formatRelativeTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTutorChip(label: String, onClick: () -> Unit) {
    val colors = LocalCatedraColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}
