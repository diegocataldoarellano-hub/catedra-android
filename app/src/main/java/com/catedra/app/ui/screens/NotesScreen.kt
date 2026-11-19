package com.catedra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.model.StudyNote
import com.catedra.app.media.VoiceInputState
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.formatRelativeTime
import com.catedra.app.ui.theme.LocalCatedraColors

@Composable
fun NotesScreen(
    uiState: CatedraUiState,
    voiceState: VoiceInputState,
    markdownPreview: String,
    onBack: () -> Unit,
    onAddNote: (NoteCategory, String, String, Boolean) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDistillFromChat: () -> Unit,
    onStartVoiceNote: ((String) -> Unit) -> Unit,
    onStopVoice: () -> Unit,
    onShareTxt: () -> Unit,
    onSaveToFolder: () -> Unit,
    onUploadToDrive: () -> Unit
) {
    val colors = LocalCatedraColors.current
    val doc = uiState.activeDocument ?: return

    var showRawMarkdown by remember { mutableStateOf(false) }
    var filterCat by remember { mutableStateOf<NoteCategory?>(null) }
    var draftCategory by remember { mutableStateOf(NoteCategory.COMPRENDIDO) }
    var draftCitation by remember { mutableStateOf("") }
    var draftText by remember { mutableStateOf("") }

    val filteredNotes = remember(uiState.activeNotes, filterCat) {
        if (filterCat == null) uiState.activeNotes
        else uiState.activeNotes.filter { it.category == filterCat }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // Top Bar
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
                    text = "Conocimiento Compacto",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "${doc.title} · ${formatCompactSize(uiState.activeNoteFileBytes)} en .md/.txt",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showRawMarkdown = !showRawMarkdown }) {
                Icon(
                    imageVector = if (showRawMarkdown) Icons.Rounded.ViewAgenda else Icons.Rounded.Code,
                    contentDescription = "Ver archivo .txt/.md",
                    tint = colors.accent
                )
            }
            IconButton(onClick = onShareTxt) {
                Icon(Icons.Rounded.Share, contentDescription = "Compartir .txt", tint = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Barra de acciones rápidas compactas (Destilar IA, Guardar carpeta, Subir a Drive)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accentSoft)
                    .clickable(enabled = !uiState.isTeacherThinking, onClick = onDistillFromChat)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isTeacherThinking) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Sintetizar chat", style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onSaveToFolder)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FolderCopy, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar .txt local", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onUploadToDrive)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Subir a Drive", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
            }
        }

        if (!showRawMarkdown) {
            // Filtros por categoría de aprendizaje
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterPill("Todo (${uiState.activeNotes.size})", filterCat == null) { filterCat = null }
                NoteCategory.entries.forEach { cat ->
                    val count = uiState.activeNotes.count { it.category == cat }
                    if (count > 0 || filterCat == cat) {
                        FilterPill("${cat.label} ($count)", filterCat == cat) { filterCat = cat }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Guarda aquí solo lo que realmente comprendiste, los errores aclarados y tu análisis crítico.\nSe almacena en texto plano .md/.txt (~3 KB por libro).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteRowItem(note = note, onDelete = { onDeleteNote(note.id) })
                        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                    }
                }
            }
        } else {
            // Vista previa del archivo plano .md / .txt real
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
            SelectionContainer {
                Text(
                    text = markdownPreview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 19.sp
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Compositor inferior para agregar nota escrita o nota de voz (transcrita al instante -> 0 KB audio!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NoteCategory.entries.forEach { cat ->
                    val sel = cat == draftCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) colors.accent else colors.surfaceElevated)
                            .clickable { draftCategory = cat }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sel) Color.White else colors.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 9.dp, vertical = 9.dp)
                ) {
                    if (draftCitation.isEmpty()) {
                        Text("Pág.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    BasicTextField(
                        value = draftCitation,
                        onValueChange = { draftCitation = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = if (voiceState.isListening) voiceState.partialText.ifBlank { "Dictando nota de voz..." } else "Escribe o dicta tu nota...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(6.dp))
                val hasDraft = draftText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (hasDraft || voiceState.isListening) colors.accent else colors.accentSoft)
                        .clickable {
                            if (hasDraft) {
                                onAddNote(draftCategory, draftText, draftCitation, false)
                                draftText = ""
                                draftCitation = ""
                            } else if (voiceState.isListening) {
                                onStopVoice()
                            } else {
                                onStartVoiceNote { spoken ->
                                    onAddNote(draftCategory, spoken, draftCitation, true)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            hasDraft -> Icons.AutoMirrored.Rounded.Send
                            voiceState.isListening -> Icons.Rounded.Stop
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = "Guardar o dictar nota",
                        tint = if (hasDraft || voiceState.isListening) Color.White else colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalCatedraColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .border(0.5.dp, if (selected) colors.accent else colors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}

@Composable
private fun NoteRowItem(note: StudyNote, onDelete: () -> Unit) {
    val colors = LocalCatedraColors.current
    val badgeColor = when (note.category) {
        NoteCategory.COMPRENDIDO -> colors.success
        NoteCategory.ERROR_CORREGIDO -> colors.warning
        NoteCategory.ANALISIS_CRITICA -> colors.accent
        NoteCategory.CITA -> Color(0xFF8E63D2)
        NoteCategory.DUDA -> colors.danger
        NoteCategory.NOTA_VOZ -> colors.textSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = note.category.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                )
                if (note.citationRef.isNotBlank()) {
                    Text(
                        text = "[${note.citationRef}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.citationText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (note.isFromVoice) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voz", tint = colors.textMuted, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = formatRelativeTime(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Borrar", tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
