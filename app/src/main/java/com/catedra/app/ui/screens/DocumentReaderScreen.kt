package com.catedra.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.media.TtsUiState
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.TtsMiniPlayerBar
import com.catedra.app.ui.theme.LocalCatedraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    uiState: CatedraUiState,
    ttsState: TtsUiState,
    onBack: () -> Unit,
    onJumpToSection: (Int) -> Unit,
    onReadAloudFromHere: (Int) -> Unit,
    onTranslateSection: () -> Unit,
    onAskTeacherPage: (Boolean) -> Unit,
    onAddPageNote: (NoteCategory, String, String) -> Unit,
    onDictateNote: ((String) -> Unit) -> Unit,
    onToggleTtsPlayPause: () -> Unit,
    onPrevTtsSection: () -> Unit,
    onNextTtsSection: () -> Unit,
    onCycleTtsSpeed: () -> Unit,
    onStopTts: () -> Unit
) {
    val colors = LocalCatedraColors.current
    val doc = uiState.activeDocument ?: return
    val sections = uiState.activeSections
    val curIdx = uiState.readerSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val currentSection = sections.getOrNull(curIdx)

    val supportsVisual = doc.docType == DocType.PDF || doc.docType == DocType.PAPER || doc.docType == DocType.IMAGE
    var showVisualMode by remember(doc.id) { mutableStateOf(supportsVisual) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top Bar minimalista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSection?.let { "${it.label} · ${curIdx + 1} / ${sections.size}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            if (supportsVisual) {
                IconButton(onClick = { showVisualMode = !showVisualMode }) {
                    Icon(
                        imageVector = if (showVisualMode) Icons.Rounded.TextFields else Icons.Rounded.Image,
                        contentDescription = "Alternar vista visual/texto",
                        tint = colors.accent
                    )
                }
            }
            IconButton(
                onClick = {
                    showVisualMode = false
                    onTranslateSection()
                },
                enabled = !uiState.isTranslatingSection
            ) {
                if (uiState.isTranslatingSection) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.nvidiaGreen)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Traducir con NVIDIA Riva",
                        tint = if (uiState.readerTranslatedText != null) colors.nvidiaGreen else colors.textSecondary
                    )
                }
            }
            IconButton(onClick = { showTocSheet = true }) {
                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = "Índice", tint = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        TtsMiniPlayerBar(
            ttsState = ttsState,
            onTogglePlayPause = onToggleTtsPlayPause,
            onPrevSection = onPrevTtsSection,
            onNextSection = onNextTtsSection,
            onCycleSpeed = onCycleTtsSpeed,
            onStop = onStopTts
        )

        // Contenido principal del lector (Visual zoomable o Texto completo limpio)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showVisualMode && supportsVisual) {
                if (uiState.isLoadingVisualPage) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = colors.accent
                    )
                } else if (uiState.readerVisualBitmap != null) {
                    var scale by remember(curIdx) { mutableFloatStateOf(1f) }
                    var offset by remember(curIdx) { mutableStateOf(Offset.Zero) }
                    val state = rememberTransformableState { zoomChange, panChange, _ ->
                        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                        scale = nextScale
                        offset = if (nextScale > 1f) offset + panChange else Offset.Zero
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (colors.isDark) Color(0xFF0A0E14) else Color(0xFFECEFF3))
                            .transformable(state),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = uiState.readerVisualBitmap.asImageBitmap(),
                            contentDescription = currentSection?.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No se pudo renderizar la vista visual (cambia a texto completo arriba).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        if (currentSection != null) {
                            Text(
                                text = currentSection.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (currentSection.title.isNotBlank() && currentSection.title != currentSection.label) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = currentSection.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            if (!uiState.readerTranslatedText.isNullOrBlank()) {
                                Text(
                                    text = "Traducción al español (NVIDIA Riva Translate) · Toca el ícono Traducir arriba para volver al original",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.nvidiaGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                text = uiState.readerTranslatedText ?: currentSection.content,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(40.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Barra inferior compacta de lectura y acciones con el Docente
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (sections.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onJumpToSection(curIdx - 1) },
                        enabled = curIdx > 0,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Anterior",
                            tint = if (curIdx > 0) colors.textPrimary else colors.textMuted
                        )
                    }
                    Text(
                        text = "${currentSection?.label ?: ""}  (${curIdx + 1}/${sections.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.clickable { showTocSheet = true }
                    )
                    IconButton(
                        onClick = { onJumpToSection(curIdx + 1) },
                        enabled = curIdx < sections.lastIndex,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Siguiente",
                            tint = if (curIdx < sections.lastIndex) colors.textPrimary else colors.textMuted
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReaderActionPill(Icons.AutoMirrored.Rounded.VolumeUp, "Escuchar") { onReadAloudFromHere(curIdx) }
                if (supportsVisual) {
                    ReaderActionPill(Icons.Rounded.Visibility, "Ver con IA") { onAskTeacherPage(true) }
                }
                ReaderActionPill(Icons.AutoMirrored.Rounded.Chat, "Preguntar") { onAskTeacherPage(false) }
                ReaderActionPill(Icons.AutoMirrored.Rounded.NoteAdd, "+ Nota") { showQuickNoteDialog = true }
            }
        }
    }

    // Índice completo de secciones
    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            containerColor = colors.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Índice (${sections.size} secciones)",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    itemsIndexed(sections) { idx, sec ->
                        val isCurrent = idx == curIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) colors.accentSoft else Color.Transparent)
                                .clickable {
                                    showTocSheet = false
                                    onJumpToSection(idx)
                                }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sec.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(74.dp)
                            )
                            Text(
                                text = sec.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                    }
                }
            }
        }
    }

    // Diálogo rápido para agregar nota (escrita o dictada) anclada a esta página
    if (showQuickNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(NoteCategory.COMPRENDIDO) }
        AlertDialog(
            onDismissRequest = { showQuickNoteDialog = false },
            containerColor = colors.surface,
            title = { Text("Nota en ${currentSection?.label ?: ""}", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(NoteCategory.COMPRENDIDO, NoteCategory.ANALISIS_CRITICA, NoteCategory.DUDA).forEach { cat ->
                            val sel = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) colors.accent else colors.surfaceElevated)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) Color.White else colors.textSecondary
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Lo comprendido, análisis o crítica") },
                        minLines = 3,
                        trailingIcon = {
                            IconButton(onClick = {
                                onDictateNote { spoken ->
                                    noteText = if (noteText.isBlank()) spoken else "$noteText $spoken"
                                }
                            }) {
                                Icon(Icons.Rounded.Mic, contentDescription = "Dictar nota", tint = colors.accent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddPageNote(selectedCategory, noteText, currentSection?.label ?: "")
                    showQuickNoteDialog = false
                }) { Text("Guardar (.md)", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickNoteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ReaderActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalCatedraColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, fontSize = 11.5.sp)
    }
}
