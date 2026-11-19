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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.LocalDocEntry
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.DocAvatar
import com.catedra.app.ui.components.formatRelativeTime
import com.catedra.app.ui.theme.LocalCatedraColors

@Composable
fun WhatsAppDocsScreen(
    uiState: CatedraUiState,
    detectedFolderHint: String,
    onBack: () -> Unit,
    onLinkWhatsAppFolder: () -> Unit,
    onPickSingleWhatsAppDoc: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDocument: (LocalDocEntry) -> Unit
) {
    val colors = LocalCatedraColors.current
    val waGreen = Color(0xFF25D366)
    val isLinked = uiState.settings.whatsappTreeUri.isNotBlank()

    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("Todos") }

    val filteredDocs = remember(uiState.whatsappDocs, searchQuery, typeFilter) {
        uiState.whatsappDocs.filter { item ->
            val matchType = when (typeFilter) {
                "Todos" -> true
                "PDF" -> item.docType == DocType.PDF
                "Word" -> item.docType == DocType.DOCX || item.docType == DocType.DOC
                "Excel" -> item.docType == DocType.XLSX || item.docType == DocType.XLS || item.docType == DocType.CSV
                "PPT" -> item.docType == DocType.PPTX
                else -> item.docType == DocType.EPUB || item.docType == DocType.TEXT || item.docType == DocType.UNKNOWN
            }
            val q = searchQuery.trim()
            val matchQuery = q.isBlank() || item.name.contains(q, ignoreCase = true)
            matchType && matchQuery
        }
    }

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
                Text("Documentos de WhatsApp", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = if (isLinked) "${uiState.whatsappDocs.size} documentos (sin fotos ni videos)" else "Solo PDF, Word, Excel, PPT, EPUB y TXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            if (isLinked) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar", tint = colors.textSecondary)
                }
            }
            IconButton(onClick = onLinkWhatsAppFolder) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = "Cambiar carpeta", tint = waGreen)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        if (!isLinked && uiState.whatsappDocs.isEmpty()) {
            // Pantalla limpia de vinculación única (1 toque)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .border(0.75.dp, waGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Topic, contentDescription = null, tint = waGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Acceso rápido a WhatsApp Documents", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    }
                    Text(
                        text = "Vincula una sola vez tu carpeta de documentos descargados de WhatsApp para verlos aquí ordenados del más reciente al más antiguo — filtrando estrictamente SOLO documentos (PDF, Word, Excel, PowerPoint, EPUB, CSV y TXT; cero fotos ni videos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "Carpeta detectada en tu equipo:\n$detectedFolderHint",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(waGreen)
                            .clickable(onClick = onLinkWhatsAppFolder)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vincular carpeta WhatsApp (1 sola vez)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .border(0.6.dp, colors.divider, RoundedCornerShape(14.dp))
                            .clickable(onClick = onPickSingleWhatsAppDoc)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "O elegir 1 documento de WhatsApp ahora",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        } else {
            // Buscador + filtros de tipo de documento (solo documentos)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar documentos de WhatsApp...", color = colors.textMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("Todos", "PDF", "Word", "Excel", "PPT", "Otros").forEach { tab ->
                    val sel = typeFilter == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) waGreen.copy(alpha = 0.18f) else colors.surfaceElevated)
                            .border(0.5.dp, if (sel) waGreen else colors.divider, RoundedCornerShape(14.dp))
                            .clickable { typeFilter = tab }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sel) waGreen else colors.textSecondary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

            if (uiState.isLoadingWhatsAppDocs) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = waGreen)
                }
            } else if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "No hay documentos descargados en esta carpeta que coincidan con el filtro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceElevated)
                                .clickable(onClick = onLinkWhatsAppFolder)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Cambiar carpeta vinculada", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    items(filteredDocs, key = { it.uri }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDocument(item) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DocAvatar(docType = item.docType, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${formatCompactSize(item.sizeBytes)} · ${formatRelativeTime(item.lastModified)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Text(
                                text = "Estudiar",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = colors.divider)
                    }
                }
            }
        }
    }
}
